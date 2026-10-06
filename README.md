# BOOSTLAB Android

Android client for BOOSTLAB — a per-app low-latency network routing project.

## Current status

**Stage 2**

- Kotlin + Jetpack Compose application.
- Lists launchable applications on the device.
- User can select a target application.
- Uses Android's official `VpnService.prepare()` permission flow.
- Foreground VPN service shell is present.
- Gateway host can be entered in the client.
- UDP route-quality probe measures median RTT, jitter and packet loss.
- Packet routing remains intentionally disabled until a real encrypted gateway is deployed and validated.

## Probe protocol

The client sends small UDP probe packets to port `51821`. The matching gateway returns the same probe token. Eight samples are used to calculate route quality.

This probe carries no game/application traffic.

## Next

1. Deploy the first Linux gateway.
2. Verify measurements from a real phone/network.
3. Integrate a proven encrypted tunnel implementation.
4. Route only the user-selected application and add safe fallback.

## Toolchain

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- compileSdk / targetSdk 37
- Kotlin / Compose compiler 2.4.10
- minSdk 26

> BOOSTLAB is a working codename. No third-party branding or code is copied.
