# Server deployment (clean Ubuntu 24.04)

This is an early control-plane implementation, NOT production-ready. Do not expose
port 8787 to the Internet. It binds to 127.0.0.1 by default.

## Prerequisites
- One clean Ubuntu 24.04 VPS, public IPv4, root/sudo access
- DNS name with valid HTTPS certificate for control API
- WireGuard interface wg0 and firewall forwarding/NAT configured
- Python 3.12, wireguard-tools, a systemd service and a reverse proxy
- 10.77.0.0/16 VPN pool, wg0 address 10.77.0.1/16

## Configuration
Environment variables (use a root-owned systemd EnvironmentFile, mode 0600):
BOOSTLAB_ADMIN_TOKEN (at least 32 random chars)
BOOSTLAB_GATEWAY_ENDPOINT (example: vpn.example.com:51820)
BOOSTLAB_WG_PUBLIC_KEY (public key of server wg0)
BOOSTLAB_DB (/var/lib/boostlab/control.sqlite3)
BOOSTLAB_LISTEN_HOST (127.0.0.1)
BOOSTLAB_LISTEN_PORT (8787)

Start: python3 server/control_api.py

The process needs tightly scoped permission to run 'wg set wg0 peer ...'.
For a production deployment use a privileged provisioning helper instead of
running the HTTP server as root.

## Endpoints
GET /health: health status
POST /admin/enrollment: requires Authorization: Bearer <admin-token>;
returns a one-time enrollment token
POST /v1/devices/enroll: JSON enrollment_token, public_key;
returns a WireGuard /32 address, endpoint and server public key.

## Security work remaining before production
- HTTPS proxy and request rate limiting
- Device authentication and token renewal, expiry and revocation
- Persistence/reconciliation of peers after WireGuard service restart
- Multi-gateway catalog, subscriptions, anti-abuse controls
- IPv6 handling, DNS leak tests, package-scoped VPN verification
- Avoid root HTTP daemon; protect admin API with separate access controls

Do not sell access or onboard customers before these are complete.
