"""Private signing transfer regression tests; uses a disposable key, never a real key."""
import hashlib
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('song_signing', Path(__file__).with_name('song-signing.py'))
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)


class SongSigningTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = tempfile.TemporaryDirectory()
        cls.source = Path(cls.fixture.name) / 'source.p12'
        environment = os.environ.copy()
        environment['SONG_TEST_PASSWORD'] = 'temporary-fixture-only'
        signing.keytool(['-genkeypair', '-noprompt', '-keystore', str(cls.source),
                         '-storetype', 'PKCS12', '-alias', 'androiddebugkey',
                         '-storepass:env', 'SONG_TEST_PASSWORD', '-keypass:env', 'SONG_TEST_PASSWORD',
                         '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2',
                         '-dname', 'CN=Disposable signing test'], environment)
        certificate = signing.keytool(['-exportcert', '-keystore', str(cls.source),
                                       '-alias', 'androiddebugkey', '-storepass:env', 'SONG_TEST_PASSWORD'], environment)
        cls.fingerprint = hashlib.sha256(certificate).hexdigest()

    @classmethod
    def tearDownClass(cls):
        cls.fixture.cleanup()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.destination = self.root / 'mac'
        self.archive = self.root / 'transfer.zip'
        self.passwords = patch.dict(os.environ, {'SONG_SOURCE_STORE_PASSWORD': 'temporary-fixture-only',
                                               'SONG_SOURCE_KEY_PASSWORD': 'temporary-fixture-only'})
        self.passwords.start()
        self.addCleanup(self.passwords.stop)

    def export_fixture(self):
        with patch.object(signing, 'EXPECTED', self.fingerprint):
            signing.export_key(self.source, 'androiddebugkey', self.destination, self.archive)

    def test_wrong_certificate_never_creates_key_or_archive(self):
        with self.assertRaisesRegex(RuntimeError, 'does not match'):
            signing.export_key(self.source, 'androiddebugkey', self.destination, self.archive)
        self.assertFalse(self.destination.exists())
        self.assertFalse(self.archive.exists())

    def test_round_trip_preserves_certificate_and_private_key(self):
        self.export_fixture()
        windows = self.root / 'windows'
        with patch.object(signing, 'EXPECTED', self.fingerprint):
            signing.import_key(self.archive, windows)
            signing.verify_directory(windows, os.environ.copy())
        self.assertEqual((self.destination / 'song-release.p12').read_bytes(), (windows / 'song-release.p12').read_bytes())

    def test_import_rejects_a_different_signer(self):
        self.export_fixture()
        windows = self.root / 'windows'
        with self.assertRaisesRegex(RuntimeError, 'does not match'):
            signing.import_key(self.archive, windows)
        self.assertFalse(windows.exists())

    def test_export_does_not_overwrite_existing_files(self):
        self.archive.write_bytes(b'old archive')
        with self.assertRaisesRegex(RuntimeError, 'already exists'):
            signing.export_key(self.source, 'androiddebugkey', self.destination, self.archive)
        self.assertEqual(self.archive.read_bytes(), b'old archive')

    def test_import_does_not_overwrite_existing_directory(self):
        self.destination.mkdir()
        (self.destination / 'keep').write_text('existing key')
        with self.assertRaisesRegex(RuntimeError, 'already exists'):
            signing.import_key(self.archive, self.destination)
        self.assertEqual((self.destination / 'keep').read_text(), 'existing key')

    def test_archive_paths_cannot_escape_destination(self):
        with zipfile.ZipFile(self.archive, 'w') as bundle:
            bundle.writestr('../outside', 'bad')
            bundle.writestr('keystore.properties', 'bad')
        with self.assertRaisesRegex(RuntimeError, 'only the two'):
            signing.import_key(self.archive, self.destination)
        self.assertFalse((self.root / 'outside').exists())
        self.assertFalse(self.destination.exists())


if __name__ == '__main__':
    unittest.main()
