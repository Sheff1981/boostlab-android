# BOOSTLAB architecture and acceptance gates

## Trust boundaries
Android client owns a per-install WireGuard private key. Server sees only public keys.
Control API authenticates device enrollment and subscription. Gateway admission is
server-side; a modified APK must not bypass subscription checks.
HTTPS with valid certificates is mandatory for public control-plane traffic.
WireGuard handles the data plane; only selected game packages are routed via VPN.

## Planned Android components
- Catalog: installed launchable packages and user selection
- Control API: enrollment, device token, gateway directory
- Tunnel: Android VpnService + WireGuard GoBackend
- Session: permission -> provision -> handshake -> verify -> game launch
- Telemetry: measured gateway RTT, jitter, packet loss, tunnel byte counters
- UI: games, boost, account, diagnostic support

## Server components
- HTTPS reverse proxy
- Control API + SQLite for MVP
- WireGuard wg0 with unique per-device /32 address
- Firewall, rate limiting, audit log, revocation
- Future: multiple gateways, billing provider, admin console

## Acceptance criteria
1. Never launch a game on BOOST when tunnel is down.
2. Never show invented latency or a gateway that does not exist.
3. Enrollment cannot be performed without an authenticated server-issued credential.
4. VPN private keys never leave their originating device.
5. Existing non-BOOSTLAB VPN may require disconnecting; Android normally permits one VPN.
6. Confirm traffic for the selected package via gateway counters and network tests.
7. Benchmark game latency and temperature; do not promise thermal improvements without evidence.

## First milestone
Build Android scaffold; implement authenticated provisioning; install on a clean VPS;
integrate embedded WireGuard and run first real device test.
