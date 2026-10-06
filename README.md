# BOOSTLAB Android

Android client for BOOSTLAB — a per-app low-latency network routing project.

## Current status

**Stage 1**

- Kotlin + Jetpack Compose app shell.
- Lists launchable applications on the device.
- User can select a target application.
- Uses Android's official `VpnService.prepare()` permission flow.
- Starts/stops a foreground VPN service shell.
- Packet routing is intentionally disabled until a real encrypted gateway exists.

## Next

Stage 2 will connect the Android client to the first BOOSTLAB gateway, enable routing only for the selected app, and expose live latency/jitter/loss metrics.

## Toolchain

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- compileSdk / targetSdk 37
- Kotlin / Compose compiler 2.4.10
- minSdk 26

> BOOSTLAB is a working codename. No third-party branding or code is copied.
