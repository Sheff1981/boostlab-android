#!/usr/bin/env python3
"""BOOSTLAB MVP control plane. Run behind HTTPS reverse proxy, never directly on public IP."""
import hashlib
import hmac
import json
import os
import secrets
import sqlite3
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from ipaddress import ip_address
from pathlib import Path

DB = Path(os.environ.get("BOOSTLAB_DB", "/var/lib/boostlab/control.sqlite3"))
ADMIN_TOKEN = os.environ.get("BOOSTLAB_ADMIN_TOKEN", "")
PUBLIC_ENDPOINT = os.environ.get("BOOSTLAB_GATEWAY_ENDPOINT", "")
SERVER_PUBLIC_KEY = os.environ.get("BOOSTLAB_WG_PUBLIC_KEY", "")
WG_INTERFACE = os.environ.get("BOOSTLAB_WG_INTERFACE", "wg0")
LISTEN_HOST = os.environ.get("BOOSTLAB_LISTEN_HOST", "127.0.0.1")
LISTEN_PORT = int(os.environ.get("BOOSTLAB_LISTEN_PORT", "8787"))
LOCK = threading.RLock()


def database():
    DB.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("""CREATE TABLE IF NOT EXISTS enrollments(
        token_hash TEXT PRIMARY KEY, used INTEGER NOT NULL DEFAULT 0,
        expires_at INTEGER NOT NULL DEFAULT 0)""")
    conn.execute("""CREATE TABLE IF NOT EXISTS devices(
        device_id TEXT PRIMARY KEY, public_key TEXT UNIQUE NOT NULL,
        ip TEXT UNIQUE NOT NULL, enabled INTEGER NOT NULL DEFAULT 1)""")
    cols = {row[1] for row in conn.execute("PRAGMA table_info(enrollments)")}
    if "expires_at" not in cols:
        conn.execute("ALTER TABLE enrollments ADD COLUMN expires_at INTEGER NOT NULL DEFAULT 0")
    columns = {row[1] for row in conn.execute("PRAGMA table_info(devices)")}
    for name, definition in (
        ("created_at", "INTEGER NOT NULL DEFAULT 0"),
        ("expires_at", "INTEGER NOT NULL DEFAULT 0"),
        ("manufacturer", "TEXT NOT NULL DEFAULT ''"),
        ("model", "TEXT NOT NULL DEFAULT ''"),
        ("android_version", "TEXT NOT NULL DEFAULT ''"),
    ):
        if name not in columns:
            conn.execute(f"ALTER TABLE devices ADD COLUMN {name} {definition}")
    conn.commit()
    return conn


def validate_public_key(value):
    import base64
    if not isinstance(value, str):
        return False
    try:
        decoded = base64.b64decode(value, validate=True)
        return len(decoded) == 32 and base64.b64encode(decoded).decode() == value
    except (ValueError, Exception):
        return False


def hash_token(token):
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def add_peer(public_key, address):
    # No shell interpolation: subprocess arguments are separate.
    subprocess.run(
        ["wg", "set", WG_INTERFACE, "peer", public_key, "allowed-ips", address + "/32"],
        check=True, capture_output=True, timeout=10,
    )


class Handler(BaseHTTPRequestHandler):
    def respond(self, status, data):
        raw = json.dumps(data).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def body(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length < 1 or length > 4096:
            raise ValueError("Invalid request size")
        return json.loads(self.rfile.read(length))

    def authorized(self):
        supplied = self.headers.get("Authorization", "")
        return bool(ADMIN_TOKEN) and hmac.compare_digest(supplied, "Bearer " + ADMIN_TOKEN)

    def do_GET(self):
        if self.path == "/admin":
            html = Path(__file__).with_name("admin.html").read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(html)))
            self.end_headers()
            self.wfile.write(html)
            return
        if self.path == "/admin/summary":
            if not self.authorized():
                self.respond(403, {"error": "forbidden"})
                return
            with LOCK, database() as conn:
                total = conn.execute("SELECT COUNT(*) FROM devices").fetchone()[0]
                enabled = conn.execute("SELECT COUNT(*) FROM devices WHERE enabled=1 AND expires_at>?", (int(time.time()),)).fetchone()[0]
            self.respond(200, {"total_devices": total, "enabled_devices": enabled})
            return
        if self.path == "/admin/devices":
            if not self.authorized():
                self.respond(403, {"error": "forbidden"})
                return
            with LOCK, database() as conn:
                rows = conn.execute("SELECT device_id, ip, enabled, expires_at, manufacturer, model, android_version FROM devices ORDER BY rowid DESC LIMIT 500").fetchall()
            self.respond(200, {"devices": [{"id": row[0], "vpn_ip": row[1], "enabled": bool(row[2]), "expires_at": row[3], "days_remaining": max(0, (row[3]-int(time.time())+86399)//86400), "manufacturer": row[4], "model": row[5], "android_version": row[6]} for row in rows]})
            return
        if self.path == "/health":
            self.respond(200, {"status": "ok"})
        else:
            self.respond(404, {"error": "not_found"})

    def do_POST(self):
        if self.path == "/admin/enrollment":
            if not self.authorized():
                self.respond(403, {"error": "forbidden"})
                return
            token = secrets.token_urlsafe(32)
            with LOCK, database() as conn:
                conn.execute("INSERT INTO enrollments(token_hash, expires_at) VALUES(?,?)",
                             (hash_token(token), int(time.time()) + 600))
                conn.commit()
            self.respond(201, {"enrollment_token": token})
            return

        if self.path == "/admin/device/access":
            if not self.authorized():
                self.respond(403, {"error": "forbidden"})
                return
            try:
                payload = self.body()
                device_id, enabled = payload["device_id"], payload["enabled"]
                if not isinstance(device_id, str) or type(enabled) is not bool:
                    raise ValueError("invalid_input")
                with LOCK, database() as conn:
                    row = conn.execute("SELECT public_key, ip, expires_at FROM devices WHERE device_id=?", (device_id,)).fetchone()
                    if row is None:
                        self.respond(404, {"error": "not_found"})
                        return
                    if enabled and row[2] <= int(time.time()):
                        self.respond(409, {"error": "subscription_expired"})
                        return
                    if enabled:
                        add_peer(row[0], row[1])
                    else:
                        subprocess.run(["wg", "set", WG_INTERFACE, "peer", row[0], "remove"],
                                       check=True, capture_output=True, timeout=10)
                    conn.execute("UPDATE devices SET enabled=? WHERE device_id=?", (int(enabled), device_id))
                    conn.commit()
                self.respond(200, {"device_id": device_id, "enabled": enabled})
            except (KeyError, TypeError, ValueError, json.JSONDecodeError):
                self.respond(400, {"error": "invalid_request"})
            except (subprocess.SubprocessError, OSError):
                self.respond(503, {"error": "wireguard_failed"})
            return
        if self.path == "/admin/subscription":
            if not self.authorized():
                self.respond(403, {"error": "forbidden"})
                return
            try:
                payload = self.body()
                device_id, days = payload["device_id"], payload["days"]
                if not isinstance(device_id, str) or type(days) is not int or not 1 <= days <= 365:
                    raise ValueError("Invalid input")
                with LOCK, database() as conn:
                    row = conn.execute("SELECT expires_at FROM devices WHERE device_id=?", (device_id,)).fetchone()
                    if row is None:
                        self.respond(404, {"error": "not_found"})
                        return
                    expires = max(int(time.time()), row[0]) + days*86400
                    conn.execute("UPDATE devices SET expires_at=?, enabled=1 WHERE device_id=?", (expires, device_id))
                    conn.commit()
                self.respond(200, {"device_id": device_id, "expires_at": expires})
            except (KeyError, ValueError, TypeError, json.JSONDecodeError):
                self.respond(400, {"error": "invalid_request"})
            return
        if self.path != "/v1/devices/enroll":
            self.respond(404, {"error": "not_found"})
            return
        try:
            body = self.body()
            token = body.get("enrollment_token", "")
            public_key = body.get("public_key", "")
            if not isinstance(token, str) or len(token) < 32 or not validate_public_key(public_key):
                self.respond(400, {"error": "invalid_input"})
                return
            if not PUBLIC_ENDPOINT or not SERVER_PUBLIC_KEY:
                self.respond(503, {"error": "gateway_unconfigured"})
                return
            with LOCK, database() as conn:
                row = conn.execute("SELECT used, expires_at FROM enrollments WHERE token_hash=?", (hash_token(token),)).fetchone()
                if row is None or row[0] or row[1] <= int(time.time()):
                    self.respond(403, {"error": "invalid_enrollment"})
                    return
                allocated = {row[0] for row in conn.execute("SELECT ip FROM devices")}
                address = next((f"10.77.{i // 253}.{i % 253 + 2}" for i in range(1, 60000)
                                if f"10.77.{i // 253}.{i % 253 + 2}" not in allocated
                                and i // 253 <= 255), None)
                if not address or ip_address(address) not in __import__("ipaddress").ip_network("10.77.0.0/16"):
                    self.respond(503, {"error": "pool_exhausted"})
                    return
                device_id = secrets.token_hex(16)
                # Configure peer before committing enrollment; a failure leaves token usable.
                add_peer(public_key, address)
                conn.execute("""INSERT INTO devices(device_id, public_key, ip, created_at, expires_at,
                    manufacturer, model, android_version) VALUES(?,?,?,?,?,?,?,?)""",
                    (device_id, public_key, address, int(time.time()), int(time.time())+30*86400,
                     str(body.get("manufacturer", ""))[:80], str(body.get("model", ""))[:80],
                     str(body.get("android_version", ""))[:30]))
                conn.execute("UPDATE enrollments SET used=1 WHERE token_hash=?", (hash_token(token),))
                conn.commit()
            self.respond(201, {"device_id": device_id, "address": address + "/32",
                               "gateway_endpoint": PUBLIC_ENDPOINT, "gateway_public_key": SERVER_PUBLIC_KEY})
        except (ValueError, TypeError, json.JSONDecodeError):
            self.respond(400, {"error": "invalid_request"})
        except (sqlite3.IntegrityError, subprocess.SubprocessError, OSError):
            self.respond(503, {"error": "provisioning_failed"})


if __name__ == "__main__":
    if not ADMIN_TOKEN or len(ADMIN_TOKEN) < 32:
        raise SystemExit("Set a random BOOSTLAB_ADMIN_TOKEN of at least 32 characters")
    with database():
        pass
    ThreadingHTTPServer((LISTEN_HOST, LISTEN_PORT), Handler).serve_forever()
