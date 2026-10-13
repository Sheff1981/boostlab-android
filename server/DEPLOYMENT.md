# BOOSTLAB Ubuntu 24.04 deployment

This is an operator checklist, **not evidence of deployment**.

1. Keep the API bound to 127.0.0.1:8787. Do **not** expose port 8787 to the internet.
2. Install repository server files under `/opt/boostlab/server` and systemd units under `/etc/systemd/system`.
3. Create `/etc/boostlab/control.env` readable by root only (chmod 600):

```ini
BOOSTLAB_ADMIN_TOKEN=GENERATE_A_NEW_RANDOM_SECRET_OF_AT_LEAST_32_CHARACTERS
BOOSTLAB_GATEWAY_ENDPOINT=212.193.15.139:51820
BOOSTLAB_WG_PUBLIC_KEY=SERVER_PUBLIC_WIREGUARD_KEY
BOOSTLAB_WG_INTERFACE=wg0
BOOSTLAB_LISTEN_HOST=127.0.0.1
BOOSTLAB_LISTEN_PORT=8787
BOOSTLAB_DB=/var/lib/boostlab/control.sqlite3
```

4. Run `python3 -m unittest discover -s server/tests -v` before deployment.
5. `systemctl daemon-reload && systemctl enable --now boostlab-control.service boostlab-reconcile.timer`
6. Put a properly configured TLS reverse proxy in front of the local API. Use a trusted certificate for an IP or a free hostname. Never send administrator tokens over plain HTTP.
7. Require HTTPS and authentication for `/admin`, `/admin/*`, and `/v1/devices/enroll`. Do not publish the control API without access controls and rate limiting.

## Known gaps before public launch

- Enrollment currently requires an admin-created one-time token. There is **no automatic token-free onboarding** yet.
- Android does not yet invoke enrollment from the BOOST button or securely persist its WireGuard private key.
- A periodic timer revokes expired peers, but renewal does not automatically restore a removed peer; admin must explicitly re-enable it.
- The current WireGuard address pool allocator must be hardened for reserved addresses and collision handling before scale.
- No verified live deployment or end-to-end handshake test has been performed for this version.
