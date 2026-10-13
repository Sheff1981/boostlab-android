"""BOOSTLAB pilot administration API. Python 3.11+, standard library only.

Bind to 127.0.0.1 and expose ONLY behind an authenticated HTTPS reverse proxy.
Set BOOSTLAB_ADMIN_TOKEN to a long random secret (never commit it).
This manages subscriptions; WireGuard peer provisioning is deliberately not
implemented until the existing device challenge/session protocol is integrated.
"""
import datetime as dt
import hmac
import json
import os
import sqlite3
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

DB = os.environ.get("BOOSTLAB_DB", "/var/lib/boostlab/clients.sqlite3")
HOST = os.environ.get("BOOSTLAB_BIND", "127.0.0.1")
PORT = int(os.environ.get("BOOSTLAB_PORT", "8765"))
TOKEN = os.environ.get("BOOSTLAB_ADMIN_TOKEN", "")
UTC = dt.timezone.utc


def connect():
    db = sqlite3.connect(DB, timeout=10)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("""CREATE TABLE IF NOT EXISTS clients (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        contact TEXT NOT NULL DEFAULT '',
        notes TEXT NOT NULL DEFAULT '',
        enabled INTEGER NOT NULL DEFAULT 0,
        expires_at TEXT,
        created_at TEXT NOT NULL
    )""")
    db.execute("""CREATE TABLE IF NOT EXISTS payments (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        client_id INTEGER NOT NULL REFERENCES clients(id),
        amount_rub INTEGER NOT NULL,
        days INTEGER NOT NULL,
        received_at TEXT NOT NULL
    )""")
    db.commit()
    return db


def now():
    return dt.datetime.now(UTC)


def iso(value):
    return value.isoformat(timespec="seconds")


def client_dict(row):
    value = dict(row)
    expiry = dt.datetime.fromisoformat(value["expires_at"]) if value["expires_at"] else None
    value["access_allowed"] = bool(value["enabled"]) and expiry is not None and expiry > now()
    return value


class Handler(BaseHTTPRequestHandler):
    def respond(self, status, obj):
        raw = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def authorized(self):
        candidate = self.headers.get("Authorization", "")
        if not TOKEN or not hmac.compare_digest(candidate, "Bearer " + TOKEN):
            self.respond(401, {"error": "unauthorized"})
            return False
        return True

    def body(self):
        size = int(self.headers.get("Content-Length", "0"))
        if size <= 0 or size > 16384:
            raise ValueError("invalid body size")
        data = json.loads(self.rfile.read(size))
        if not isinstance(data, dict):
            raise ValueError("expected JSON object")
        return data

    def do_GET(self):
        if not self.authorized():
            return
        path = urlsplit(self.path).path
        if path == "/admin/v1/health":
            return self.respond(200, {"status": "ok"})
        if path == "/admin/v1/clients":
            with connect() as db:
                rows = db.execute("SELECT * FROM clients ORDER BY id").fetchall()
            return self.respond(200, {"clients": [client_dict(row) for row in rows]})
        return self.respond(404, {"error": "not_found"})

    def do_POST(self):
        if not self.authorized():
            return
        path = urlsplit(self.path).path
        try:
            data = self.body()
            with connect() as db:
                if path == "/admin/v1/clients":
                    name = str(data.get("name", "")).strip()
                    if not 1 <= len(name) <= 120:
                        raise ValueError("name must be 1..120 chars")
                    cur = db.execute(
                        "INSERT INTO clients(name,contact,notes,created_at) VALUES(?,?,?,?)",
                        (name, str(data.get("contact", ""))[:160],
                         str(data.get("notes", ""))[:500], iso(now())),
                    )
                    db.commit()
                    return self.respond(201, {"id": cur.lastrowid})
                parts = path.strip("/").split("/")
                if len(parts) != 5 or parts[:3] != ["admin", "v1", "clients"]:
                    return self.respond(404, {"error": "not_found"})
                client_id = int(parts[3])
                row = db.execute("SELECT * FROM clients WHERE id=?", (client_id,)).fetchone()
                if row is None:
                    return self.respond(404, {"error": "client_not_found"})
                if parts[4] == "extend":
                    days = int(data.get("days", 30))
                    amount = int(data.get("amount_rub", 0))
                    if not 1 <= days <= 365 or not 0 <= amount <= 1000000:
                        raise ValueError("invalid days or amount")
                    current = dt.datetime.fromisoformat(row["expires_at"]) if row["expires_at"] else now()
                    expiry = max(current, now()) + dt.timedelta(days=days)
                    db.execute("UPDATE clients SET enabled=1, expires_at=? WHERE id=?", (iso(expiry), client_id))
                    db.execute("INSERT INTO payments(client_id,amount_rub,days,received_at) VALUES(?,?,?,?)",
                               (client_id, amount, days, iso(now())))
                elif parts[4] == "disable":
                    db.execute("UPDATE clients SET enabled=0 WHERE id=?", (client_id,))
                elif parts[4] == "enable":
                    db.execute("UPDATE clients SET enabled=1 WHERE id=?", (client_id,))
                else:
                    return self.respond(404, {"error": "not_found"})
                db.commit()
                updated = db.execute("SELECT * FROM clients WHERE id=?", (client_id,)).fetchone()
                return self.respond(200, {"client": client_dict(updated)})
        except (ValueError, TypeError, json.JSONDecodeError) as exc:
            return self.respond(400, {"error": str(exc)})

if __name__ == "__main__":
    if len(TOKEN) < 32:
        raise SystemExit("BOOSTLAB_ADMIN_TOKEN must be at least 32 characters")
    os.makedirs(os.path.dirname(os.path.abspath(DB)), exist_ok=True)
    with connect():
        pass
    print(f"BOOSTLAB admin API listening on {HOST}:{PORT}", flush=True)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
