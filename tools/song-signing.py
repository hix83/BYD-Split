#!/usr/bin/env python3
"""Export/import the existing Song key. Requires Python 3 and JDK 17 keytool."""
import argparse
import getpass
import hashlib
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import tempfile
import zipfile

EXPECTED = '63442ad5ab88b867970e1d7480aa2e849ec46f63b20c61b2da837aa43d715d10'
FILES = {'song-release.p12', 'keystore.properties'}
DEFAULT_DIR = Path.home() / '.byd-split' / 'song-signing'


def keytool(arguments, environment):
    java = environment.get('JAVA_HOME')
    tool = str(Path(java) / 'bin' / ('keytool.exe' if os.name == 'nt' else 'keytool')) if java else shutil.which('keytool')
    if not tool or not Path(tool).is_file():
        raise RuntimeError('Set JAVA_HOME to JDK 17 or add keytool to PATH.')
    result = subprocess.run([tool, *arguments], env=environment, capture_output=True)
    if result.returncode:
        raise RuntimeError('keytool failed: check the keystore, alias and passwords. No passwords were logged.')
    return result.stdout


def certificate(store, alias, variable, environment):
    data = keytool(['-exportcert', '-keystore', str(store), '-alias', alias,
                    '-storepass:env', variable], environment)
    if hashlib.sha256(data).hexdigest() != EXPECTED:
        raise RuntimeError('Certificate does not match published Song releases. Import refused.')


def read_config(directory):
    config = {}
    for line in (directory / 'keystore.properties').read_text(encoding='ascii').splitlines():
        key, value = line.split('=', 1)
        if key in config:
            raise RuntimeError('Duplicate signing property.')
        config[key] = value
    if set(config) != {'storeFile', 'storeType', 'storePassword', 'keyAlias', 'keyPassword'}:
        raise RuntimeError('Invalid signing properties.')
    if config['storeFile'] != 'song-release.p12' or config['storeType'] != 'PKCS12' or config['keyAlias'] != 'song':
        raise RuntimeError('Unexpected signing archive format.')
    return config


def verify_directory(directory, environment):
    config = read_config(directory)
    environment = environment.copy()
    environment['SONG_STORE_PASSWORD'] = config['storePassword']
    environment['SONG_KEY_PASSWORD'] = config['keyPassword']
    store = directory / config['storeFile']
    certificate(store, 'song', 'SONG_STORE_PASSWORD', environment)
    # A valid public certificate alone is insufficient: exercise the private key.
    keytool(['-certreq', '-keystore', str(store), '-alias', 'song',
             '-storepass:env', 'SONG_STORE_PASSWORD', '-keypass:env', 'SONG_KEY_PASSWORD'], environment)


def export_key(source, alias, destination, archive):
    if not source.is_file():
        raise RuntimeError('Source keystore does not exist.')
    if destination.exists() or archive.exists():
        raise RuntimeError('Destination or archive already exists. Nothing was overwritten.')
    environment = os.environ.copy()
    password = environment.get('SONG_SOURCE_STORE_PASSWORD')
    if password is None:
        password = getpass.getpass('Original keystore password [Enter: android]: ') or 'android'
    environment['SONG_SOURCE_STORE_PASSWORD'] = password
    environment['SONG_SOURCE_KEY_PASSWORD'] = environment.get('SONG_SOURCE_KEY_PASSWORD', password)
    certificate(source, alias, 'SONG_SOURCE_STORE_PASSWORD', environment)
    environment['SONG_DEST_PASSWORD'] = secrets.token_urlsafe(36)
    destination.parent.mkdir(parents=True, exist_ok=True)
    archive.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.song-signing-', dir=destination.parent) as temp:
        staging = Path(temp) / 'key'
        staging.mkdir(mode=0o700)
        store = staging / 'song-release.p12'
        keytool(['-importkeystore', '-noprompt', '-srckeystore', str(source),
                 '-srcalias', alias, '-srcstorepass:env', 'SONG_SOURCE_STORE_PASSWORD',
                 '-srckeypass:env', 'SONG_SOURCE_KEY_PASSWORD', '-destkeystore', str(store),
                 '-deststoretype', 'PKCS12', '-destalias', 'song',
                 '-deststorepass:env', 'SONG_DEST_PASSWORD', '-destkeypass:env', 'SONG_DEST_PASSWORD'], environment)
        secret = environment['SONG_DEST_PASSWORD']
        config = staging / 'keystore.properties'
        config.write_text('storeFile=song-release.p12\nstoreType=PKCS12\n'
                          f'storePassword={secret}\nkeyAlias=song\nkeyPassword={secret}\n', encoding='ascii')
        for path in (store, config):
            path.chmod(0o600)
        verify_directory(staging, environment)
        # Exclusive creation prevents accidental replacement of a previous backup.
        with archive.open('xb') as output:
            try:
                archive.chmod(0o600)
                with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as bundle:
                    for name in sorted(FILES):
                        bundle.write(staging / name, name)
                staging.rename(destination)
            except Exception:
                output.close()
                archive.unlink()
                raise
    print('Song signing directory:', destination)
    print('PRIVATE transfer archive:', archive)
    print('Certificate SHA256:', EXPECTED)


def import_key(archive, destination):
    if destination.exists():
        raise RuntimeError('Destination already exists. Nothing was overwritten.')
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.song-signing-', dir=destination.parent) as temp:
        staging = Path(temp) / 'key'
        staging.mkdir(mode=0o700)
        with zipfile.ZipFile(archive) as bundle:
            entries = bundle.infolist()
            if len(entries) != 2 or {entry.filename for entry in entries} != FILES:
                raise RuntimeError('Archive must contain only the two Song signing files.')
            for entry in entries:
                if entry.file_size > 1024 * 1024:
                    raise RuntimeError('Signing archive entry is too large.')
                path = staging / entry.filename
                path.write_bytes(bundle.read(entry))
                path.chmod(0o600)
        verify_directory(staging, os.environ.copy())
        staging.rename(destination)
    print('Verified Song signing directory:', destination)
    print('Certificate SHA256:', EXPECTED)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    export = sub.add_parser('export', help='On Mac: preserve the existing key and create a private transfer archive')
    export.add_argument('--source', type=Path, default=Path.home() / '.android' / 'debug.keystore')
    export.add_argument('--source-alias', default='androiddebugkey')
    export.add_argument('--output-dir', type=Path, default=DEFAULT_DIR)
    export.add_argument('--archive', type=Path, default=Path.home() / '.byd-split' / 'transfer' / 'BYD-Split-Song-signing.zip')
    load = sub.add_parser('import', help='On Windows: verify the archive and install it into a new private directory')
    load.add_argument('--archive', type=Path, required=True)
    load.add_argument('--output-dir', type=Path, default=DEFAULT_DIR)
    args = parser.parse_args()
    destination = args.output_dir.expanduser().resolve()
    archive = args.archive.expanduser().resolve()
    if args.action == 'export':
        export_key(args.source.expanduser().resolve(), args.source_alias, destination, archive)
    else:
        import_key(archive, destination)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError, zipfile.BadZipFile) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
