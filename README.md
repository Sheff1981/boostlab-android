# BOOSTLAB Android

Android client for BOOSTLAB — a per-app low-latency network routing project.

## Current status

**Stage 3**

- Kotlin + Jetpack Compose application.
- Lists launchable applications on the device.
- User can select a target application.
- Uses Android's official `VpnService.prepare()` permission flow.
- Foreground VPN service shell is present.
- Manual gateway probe measures median RTT, jitter and packet loss.
- HTTPS control-plane discovery can return multiple healthy gateways.
- The client benchmarks up to eight gateways in parallel and selects the best route using RTT, jitter and packet loss.
- Packet routing remains intentionally disabled until the first real encrypted gateway is deployed and validated.

## Route selection

The client does not simply choose the lowest single ping. It scores a route using:

- median RTT;
- jitter;
- packet loss.

Packet loss is penalized strongly so a slightly faster but unstable server does not win.

## Next

1. Deploy the first Linux gateway and control service.
2. Verify automatic selection from a real phone/network.
3. Integrate a proven encrypted tunnel implementation.
4. Route only the user-selected application and add safe fallback.

## Toolchain

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- compileSdk / targetSdk 37
- Kotlin / Compose compiler 2.4.10
- minSdk 26

> BOOSTLAB is a working codename. No third-party branding or code is copied.
