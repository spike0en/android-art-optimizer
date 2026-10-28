# Android ART Optimizer

Optimize selected Android apps using Android's own ART compiler.
Connect with Wireless Debugging or supported Ethernet ADB on the same device, choose your apps, and run optimization from a phone, tablet, or TV remote.

## What it does

- Optimizes Java and Kotlin code in selected apps using Android's Ahead-Of-Time `speed` compilation mode.
- Inspects live ART compiler status (`speed`, `speed-profile`, `verify`) for every package.
- Selective optimization ("Optimize needed"): skips already-optimized apps and compiles only unoptimized or updated packages.
- Full compilation passes ("Optimize all"): recompiles all configured apps on demand.
- Clean discovery: lists only installed third-party apps and games, keeping system packages out of the way.
- Quick management: add all discovered apps or remove all configured apps in a single tap.
- Automatic connection: turns on Wireless Debugging automatically on launch once authorized via local ADB.
- Safe disconnect: disconnects transport and shuts off Wireless Debugging system-wide from the Device page.
- Material You and AMOLED theme: pulls dynamic accent colors on Android 12+ alongside a pitch-black dark theme.
- Two pairing methods: reply directly to a pairing notification, or scan a local QR code with a second device.
- TV and remote ready: full D-pad support for Android TV and Google TV.
- Includes an optional Advanced ADB Console for user-entered shell commands.

This targets managed app code.
It does not compile native libraries, graphics, or video decoding.
Android stops the selected app during optimization, and you can open it immediately afterward.

## Supported devices

| Device | Requirements |
|---|---|
| Phone or tablet | Android 11 or newer with **Wireless Debugging** |
| Android TV / Google TV | Modern **Wireless Debugging**, or firmware exposing Ethernet ADB; Android 13+ is the intended baseline |

The app tests actual command capabilities at runtime.
OEM support varies, and root is never required.

## Install

1. Download the latest APK from [GitHub Releases](https://github.com/spike0en/android-art-optimizer/releases).
2. Open the file on your device and approve **install unknown apps** if prompted.
3. On TV, transfer the APK over USB or local file transfer, open it with your TV file manager, and find **ART Optimizer** in the launcher.

## First use

1. Enable **Developer Options** and **Wireless Debugging** in Android Settings.
2. Open ART Optimizer and select a pairing method.
3. In Settings, open **Wireless Debugging -> Pair device with pairing code** and leave the dialog open.
4. Enter the six-digit code:
   - **Notification method**: reply directly to the optimizer's notification while the Settings dialog stays open.
   - **QR code method**: scan the QR code with another device on the same Wi-Fi network and submit the code there.
5. Once connected, the app requests secure settings permission through local ADB.
   On future launches, it will turn on Wireless Debugging automatically.
6. Open **Apps**, browse **Discovered** to add your apps, and tap **Optimize needed**.

When you finish, tap **Disconnect & Turn off ADB** on the Device page to drop the connection and turn off Wireless Debugging system-wide.

## Results and status

| Status | Meaning |
|---|---|
| speed (optimized) | App is compiled with full Ahead-Of-Time optimization. |
| speed-profile | App is optimized based on background runtime profile traces. |
| verify / quicken | App is running unoptimized bytecode or baseline verification. |
| Updated since optimization | App version changed since last run; re-optimize to update native code. |

## Help and project information

- [Detailed user guide](docs/USER_GUIDE.md)
- [Report a problem](https://github.com/spike0en/android-art-optimizer/issues)
- [Build and contribute](docs/CONTRIBUTING.md)
- [Dependency licenses](docs/DEPENDENCIES.md)

Licensed under [GPLv3](LICENSE).
