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


## Preconfigured friend APK

The Android build can be prepared with the server profile already embedded so the friend does not need to enter technical values manually.

Supported build environment variables:

- `BOOSTLAB_DEFAULT_CONTROL_URL`
- `BOOSTLAB_DEFAULT_GATEWAY_HOST`
- `BOOSTLAB_DEFAULT_GATEWAY_PORT`
- `BOOSTLAB_DEFAULT_WG_PUBLIC_KEY`
- `BOOSTLAB_DEFAULT_WG_PORT`
- `BOOSTLAB_DEFAULT_TUNNEL_ADDRESS`
- `BOOSTLAB_DEFAULT_DNS_SERVER`

These values contain connection metadata and a WireGuard **public** key only. Never put a client or server private key into these variables.

If no build defaults are supplied, the advanced settings screen remains available for one-time manual setup.


## Game Boost 0.6

The default BOOST path does not require a server or VPN. Select a game and press **BOOST · ЗАПУСТИТЬ**.

The app performs a non-destructive device readiness check (available RAM, battery saver, thermal state) and launches the selected game. It does **not** clear app data, delete caches or files, or try to kill other applications.

VPN / Network Boost remains optional and is controlled separately. It can be enabled for the selected game only when a WireGuard gateway is configured.


## Per-game launch profiles 0.7

Each installed game can now keep its own local launch profile. Profiles are stored by Android package name and never contain VPN keys or server credentials.

- **SMART** — blocks launch only when Android reports severe thermal stress.
- **ONLINE** — uses the same thermal guard and also requires a validated internet connection.
- **ALWAYS** — never blocks launch; diagnostics still run and remain visible.

The profile is applied before the game starts and is independent from VPN / Network Boost.
