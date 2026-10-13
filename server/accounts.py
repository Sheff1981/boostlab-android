"""BOOSTLAB user account storage and password verification.

Passwords use scrypt with a per-account salt. Never log credentials or return password hashes.
"""
import hashlib
import hmac
import re
import secrets
import sqlite3
import time

EMAIL_RE = re.compile(r"^[^\s@]{1,64}@[^\s@]{1,190}$")


def setup_accounts(conn: sqlite3.Connection):
    conn.execute("""CREATE TABLE IF NOT EXISTS accounts(
        account_id TEXT PRIMARY KEY,
        email TEXT NOT NULL UNIQUE COLLATE NOCASE,
        password_hash BLOB NOT NULL,
        salt BLOB NOT NULL,
        created_at INTEGER NOT NULL,
        session_hash TEXT,
        session_expires_at INTEGER NOT NULL DEFAULT 0
    )""")
    conn.commit()


def valid_password(password):
    return (isinstance(password, str) and 8 <= len(password) <= 128
            and any(ch.isupper() for ch in password)
            and any(ch.islower() for ch in password)
            and any(ch.isdigit() for ch in password))


def digest(password, salt):
    return hashlib.scrypt(password.encode("utf-8"), salt=salt, n=2**14, r=8, p=1, dklen=32)


def register(conn, email, password):
    if not isinstance(email, str) or not EMAIL_RE.fullmatch(email) or not valid_password(password):
        raise ValueError("invalid_credentials")
    salt = secrets.token_bytes(16)
    account_id = secrets.token_hex(16)
    conn.execute("INSERT INTO accounts(account_id,email,password_hash,salt,created_at) VALUES(?,?,?,?,?)",
                 (account_id, email.strip().lower(), digest(password, salt), salt, int(time.time())))
    conn.commit()
    return account_id


def login(conn, email, password):
    if not isinstance(email, str) or not isinstance(password, str):
        return None
    row = conn.execute("SELECT account_id,password_hash,salt FROM accounts WHERE email=?",
                       (email.strip().lower(),)).fetchone()
    if row is None or not hmac.compare_digest(row[1], digest(password, row[2])):
        return None
    token = secrets.token_urlsafe(32)
    expires = int(time.time()) + 30 * 86400
    conn.execute("UPDATE accounts SET session_hash=?,session_expires_at=? WHERE account_id=?",
                 (hashlib.sha256(token.encode()).hexdigest(), expires, row[0]))
    conn.commit()
    return {"account_id": row[0], "session_token": token, "session_expires_at": expires}


def account_for_token(conn, token):
    if not isinstance(token, str) or len(token) < 32:
        return None
    row = conn.execute("SELECT account_id,email FROM accounts WHERE session_hash=? AND session_expires_at>?",
                       (hashlib.sha256(token.encode()).hexdigest(), int(time.time()))).fetchone()
    return {"account_id": row[0], "email": row[1]} if row else None
