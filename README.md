# BOOSTLAB Android

Private Android gaming-route client being built for one friend.

## Current target

**Android only.** The normal user flow is intentionally simple:

1. Choose the game/app.
2. Tap **Буст**.
3. BOOSTLAB brings up the saved per-app WireGuard route.
4. The screen shows ping, jitter and packet loss.
5. Tap **Отключить** to return to the normal route.

Technical server fields are hidden under **Настройки сервера** and are intended to be configured once.

## Current implementation

- Kotlin + Jetpack Compose.
- Remembers the last selected app.
- Remembers the public server profile between launches.
- Android WireGuard client identity is generated locally.
- The client private key is encrypted at rest with Android Keystore and is not stored in the server profile.
- Only the selected Android package is included in the WireGuard VPN.
- A fresh WireGuard handshake is required before BOOSTLAB reports the boost as active.
- Failed handshakes tear the tunnel down for safe fallback.
- Gateway route quality is measured with correlated UDP probe v2 packets.
- Automatic gateway selection uses RTT, jitter, packet loss and switching hysteresis.
- LAN discovery remains available inside advanced settings for no-VPS development tests.

## One-time server setup

The private profile can store:

- control-plane HTTPS address;
- gateway host;
- gateway probe port;
- WireGuard server public key;
- WireGuard port;
- phone tunnel address;
- DNS server.

These are operational/public connection settings. The Android WireGuard private key is kept separately in protected local storage.

## Toolchain

- Kotlin.
- Jetpack Compose.
- Android Gradle Plugin 9.4.0.
- Gradle 9.6.0.
- compileSdk / targetSdk 37.
- minSdk 26.
- Official WireGuard Android tunnel library.

> BOOSTLAB is a working codename. No third-party branding or implementation is copied.
