import base64
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import control_api


class ControlApiTests(unittest.TestCase):
    def test_public_key_validation(self):
        key = base64.b64encode(bytes(range(32))).decode()
        self.assertTrue(control_api.validate_public_key(key))
        self.assertFalse(control_api.validate_public_key("not a wireguard key"))
        self.assertFalse(control_api.validate_public_key(""))

    def test_enrollment_tokens_are_hashed(self):
        self.assertEqual(control_api.hash_token("hello"), control_api.hash_token("hello"))
        self.assertNotEqual(control_api.hash_token("hello"), "hello")

    def test_database_schema(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(control_api, "DB", Path(directory) / "test.sqlite3"):
                with control_api.database() as conn:
                    self.assertEqual(conn.execute("SELECT count(*) FROM devices").fetchone()[0], 0)


if __name__ == "__main__":
    unittest.main()
