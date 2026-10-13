# BOOSTLAB

Android game VPN accelerator. Clean-room rebuild, October 2026.

## Product contract
- The BOOST action establishes an in-app Android VpnService WireGuard tunnel first.
- The selected game is launched only after a verified VPN handshake.
- Split tunneling routes only the selected application's traffic.
- No simulated ping, fake gateways, or local "performance boost" claims.
- No user-facing private keys, IP entry, or DNS setup.
- Server-issued short-lived enrollment credentials and per-device WireGuard identities.
- VPN cannot offload rendering or CPU work; thermal benefits require measurement.

## Modules
- `android/`: Kotlin Android application (initial scaffold).
- `server/`: Python standard-library control API and WireGuard provisioning (initial implementation).
- `docs/`: deployment and security design.

## Current status
Foundation under construction. Not yet a working or sale-ready APK. The VPS must be configured before end-to-end tests.

## Reference implementations
- WireGuard Android: https://github.com/WireGuard/wireguard-android (Apache-2.0)
- WG Tunnel: https://github.com/wgtunnel/android (MIT)

Never commit WireGuard private keys, enrollment tokens, signing keys or server secrets.
