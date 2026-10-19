#!/usr/bin/env python3
"""Import the existing release key into a portable private PKCS12 keystore.

The private key is preserved, not regenerated. No passwords are printed or
passed as command-line arguments. Requires Python 3 and JDK keytool.
"""
import argparse
import getpass
import hashlib
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys

EXPECTED = "63442ad5ab88b867970e1d7480aa2e849ec46f63b20c61b2da837aa43d715d10"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path, help="Original keystore from the other PC")
    parser.add_argument("--source-alias", default="androiddebugkey")
    parser.add_argument("--output-dir", type=Path, default=Path.home() / ".byd-split" / "signing")
    args = parser.parse_args()
    source = args.source.expanduser().resolve()
    destination = args.output_dir.expanduser().resolve()
    if not source.is_file():
        parser.error("Source keystore does not exist.")
    if destination.exists():
        parser.error("Output directory already exists. Choose a new empty location; nothing was overwritten.")
    java_home = os.environ.get("JAVA_HOME")
    keytool = str(Path(java_home) / "bin" / ("keytool.exe" if os.name == "nt" else "keytool")) if java_home else shutil.which("keytool")
    if not keytool or not Path(keytool).is_file():
        parser.error("Set JAVA_HOME to JDK 17 or add keytool to PATH.")
    environment = os.environ.copy()
    source_password = environment.get("BYD_SOURCE_STORE_PASSWORD")
    if source_password is None:
        source_password = getpass.getpass("Original keystore password [Enter: android]: ") or "android"
    environment["BYD_IMPORT_SOURCE_PASSWORD"] = source_password
    environment["BYD_IMPORT_KEY_PASSWORD"] = environment.get("BYD_SOURCE_KEY_PASSWORD", source_password)

    def run(arguments):
        result = subprocess.run([keytool, *arguments], env=environment, capture_output=True)
        if result.returncode:
            # Do not echo external commands or environment variables containing passwords.
            raise RuntimeError("keytool failed. Check source alias, store password and key password; no existing files were changed.")
        return result.stdout

    certificate = run(["-exportcert", "-keystore", str(source), "-alias", args.source_alias,
                       "-storepass:env", "BYD_IMPORT_SOURCE_PASSWORD"])
    if hashlib.sha256(certificate).hexdigest() != EXPECTED:
        raise RuntimeError("Source certificate does not match GitHub alpha.4. Import refused before creating output files.")
    environment["BYD_IMPORT_DEST_PASSWORD"] = secrets.token_urlsafe(36)
    destination.mkdir(parents=True, mode=0o700)
    store = destination / "seagull-release.p12"
    config = destination / "keystore.properties"
    try:
        run(["-importkeystore", "-noprompt", "-srckeystore", str(source),
             "-srcalias", args.source_alias, "-srcstorepass:env", "BYD_IMPORT_SOURCE_PASSWORD",
             "-srckeypass:env", "BYD_IMPORT_KEY_PASSWORD", "-destkeystore", str(store),
             "-deststoretype", "PKCS12", "-destalias", "seagull",
             "-deststorepass:env", "BYD_IMPORT_DEST_PASSWORD", "-destkeypass:env", "BYD_IMPORT_DEST_PASSWORD"])
        exported = run(["-exportcert", "-keystore", str(store), "-alias", "seagull",
                        "-storepass:env", "BYD_IMPORT_DEST_PASSWORD"])
        if hashlib.sha256(exported).hexdigest() != EXPECTED:
            raise RuntimeError("Imported certificate verification failed.")
        password = environment["BYD_IMPORT_DEST_PASSWORD"]
        config.write_text("storeFile=seagull-release.p12\nstoreType=PKCS12\n"
                          f"storePassword={password}\nkeyAlias=seagull\nkeyPassword={password}\n", encoding="ascii")
        store.chmod(0o600)
        config.chmod(0o600)
    except Exception:
        # Only remove files created by this invocation in its new directory.
        for path in (store, config):
            if path.exists():
                path.unlink()
        destination.rmdir()
        raise
    print("Shared signing directory:", destination)
    print("Certificate SHA256:", EXPECTED)
    print("Copy this private directory to the same home-relative path on both PCs. Do not publish it.")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
