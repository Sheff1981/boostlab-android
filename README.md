# BOOSTLAB Android

Android client for BOOSTLAB — a per-app low-latency network routing project.

## Current status

**Stage 4**

- Kotlin + Jetpack Compose.
- Lists launchable applications and lets the user select one target app.
- Measures candidate gateways by median RTT, jitter and packet loss.
- Can discover multiple healthy gateways through the HTTPS control plane.
- Uses hysteresis so the selected route does not flap for tiny improvements.
- Uses the official embeddable WireGuard Android tunnel library.
- Generates the client WireGuard identity locally.
- Encrypts the client private key at rest with Android Keystore.
- Builds a WireGuard config with `IncludedApplications`, so only the selected Android package is routed through the tunnel.
- Connects/disconnects the real WireGuard backend after Android VPN authorization.
- Requires a fresh WireGuard handshake before reporting the boost as active.
- If the handshake fails, the tunnel is closed automatically so normal connectivity is preserved.
- GitHub Actions builds tests and a debug APK artifact.

## Route selection

The client does not simply choose the lowest single ping. It scores a route using:

- median RTT;
- jitter;
- packet loss.

Packet loss is penalized strongly so a slightly faster but unstable server does not win.

## First real tunnel test

The remaining external dependency is a real Linux gateway with a public IP.

1. Deploy `boostlab-gateway`.
2. Configure standard WireGuard on UDP `51820`.
3. Keep the BOOSTLAB probe on UDP `51821`.
4. Register the Android **public** key as a peer on the gateway.
5. Assign the phone a tunnel address such as `10.77.0.2/32`.
6. Put the gateway public key and host into the app, or publish the public metadata through the control plane.
7. Connect and verify that a fresh handshake succeeds.
8. Confirm only the selected application traverses the tunnel.

No private key should ever be copied from the phone or committed to Git.

## Toolchain

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- compileSdk / targetSdk 37
- Kotlin / Compose compiler 2.4.10
- minSdk 26
- WireGuard Android tunnel library 1.0.20260102

> BOOSTLAB is a working codename. No third-party branding or code is copied.
