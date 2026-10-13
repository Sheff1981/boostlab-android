"""BOOSTLAB control plane tests: run python3 -m unittest discover -s server/tests -v."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import sys
import threading
import unittest
from unittest.mock import patch
from urllib.request import Request, urlopen
from http.server import ThreadingHTTPServer

MODULE_PATH = Path(__file__).resolve().parents[1] / "control_api.py"
sys.path.insert(0, str(MODULE_PATH.parent))
spec = importlib.util.spec_from_file_location("boostlab_control", MODULE_PATH)
api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)


class ControlTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.old_db = api.DB
        self.old_token = api.ADMIN_TOKEN
        self.old_endpoint = api.PUBLIC_ENDPOINT
        self.old_key = api.SERVER_PUBLIC_KEY
        api.DB = Path(self.tmp.name) / "control.sqlite3"
        api.ADMIN_TOKEN = "test-token-" + "a" * 40
        api.PUBLIC_ENDPOINT = "127.0.0.1:51820"
        api.SERVER_PUBLIC_KEY = "A" * 43 + "="
        with api.database():
            pass
        self.httpd = ThreadingHTTPServer(("127.0.0.1", 0), api.Handler)
        self.thread = threading.Thread(target=self.httpd.serve_forever, daemon=True)
        self.thread.start()
        self.url = "http://127.0.0.1:%d" % self.httpd.server_port

    def tearDown(self):
        self.httpd.shutdown()
        self.httpd.server_close()
        self.thread.join(timeout=2)
        api.DB = self.old_db
        api.ADMIN_TOKEN = self.old_token
        api.PUBLIC_ENDPOINT = self.old_endpoint
        api.SERVER_PUBLIC_KEY = self.old_key
        self.tmp.cleanup()

    def request(self, path, payload=None, auth=False):
        headers = {"Content-Type": "application/json"}
        if auth:
            headers["Authorization"] = "Bearer " + api.ADMIN_TOKEN
        data = json.dumps(payload).encode() if payload is not None else None
        req = Request(self.url + path, data=data, headers=headers)
        try:
            with urlopen(req, timeout=3) as resp:
                return resp.status, json.load(resp)
        except __import__("urllib.error", fromlist=["HTTPError"]).HTTPError as err:
            return err.code, json.load(err)

    def test_admin_requires_auth(self):
        status, _ = self.request("/admin/devices")
        self.assertEqual(status, 403)
        status, data = self.request("/admin/summary", auth=True)
        self.assertEqual((status, data["total_devices"]), (200, 0))

    def test_enrollment_trial_and_renewal(self):
        status, token_data = self.request("/admin/enrollment", {}, auth=True)
        self.assertEqual(status, 201)
        with patch.object(api, "add_peer"):
            status, device = self.request("/v1/devices/enroll", {
                "enrollment_token": token_data["enrollment_token"],
                "public_key": "A" * 43 + "=",
                "manufacturer": "Huawei", "model": "P30", "android_version": "10"
            })
        self.assertEqual(status, 201)
        status, listing = self.request("/admin/devices", auth=True)
        self.assertEqual(status, 200)
        self.assertEqual(listing["devices"][0]["days_remaining"], 30)
        self.assertEqual(listing["devices"][0]["manufacturer"], "Huawei")
        status, _ = self.request("/admin/subscription", {
            "device_id": device["device_id"], "days": 30
        }, auth=True)
        self.assertEqual(status, 200)
        status, listing = self.request("/admin/devices", auth=True)
        self.assertEqual(listing["devices"][0]["days_remaining"], 60)
        status, _ = self.request("/v1/devices/enroll", {
            "enrollment_token": token_data["enrollment_token"],
            "public_key": "A" * 43 + "="
        })
        self.assertEqual(status, 403)

    def test_account_register_and_login(self):
        status, account = self.request("/v1/accounts/register", {
            "email": "test@example.com", "password": "SecurePass123"
        })
        self.assertEqual(status, 201)
        self.assertTrue(account["account_id"])
        status, result = self.request("/v1/accounts/login", {
            "email": "test@example.com", "password": "SecurePass123"
        })
        self.assertEqual(status, 200)
        self.assertTrue(result["session_token"])
        status, _ = self.request("/v1/accounts/login", {
            "email": "test@example.com", "password": "WrongPass123"
        })
        self.assertEqual(status, 401)
        status, _ = self.request("/v1/accounts/register", {
            "email": "test@example.com", "password": "SecurePass123"
        })
        self.assertEqual(status, 409)

    def test_device_list_requires_session(self):
        status, _ = self.request("/v1/accounts/devices")
        self.assertEqual(status, 401)

    def test_expired_token_rejected(self):
        status, token_data = self.request("/admin/enrollment", {}, auth=True)
        with api.database() as conn:
            conn.execute("UPDATE enrollments SET expires_at=1")
            conn.commit()
        status, _ = self.request("/v1/devices/enroll", {
            "enrollment_token": token_data["enrollment_token"],
            "public_key": "A" * 43 + "="
        })
        self.assertEqual(status, 403)


if __name__ == "__main__":
    unittest.main()
