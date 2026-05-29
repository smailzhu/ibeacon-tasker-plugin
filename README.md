<p align="center">
  <img src="docs/readme-icon.png" alt="iBeacon Tasker Plugin icon" width="96">
</p>

# iBeacon Tasker Plugin

[![Android build](https://github.com/smailzhu/ibeacon-tasker-plugin/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/smailzhu/ibeacon-tasker-plugin/actions/workflows/android.yml)
[![Latest tag](https://img.shields.io/github/v/tag/smailzhu/ibeacon-tasker-plugin?label=latest)](https://github.com/smailzhu/ibeacon-tasker-plugin/tags)
[![License](https://img.shields.io/github/license/smailzhu/ibeacon-tasker-plugin)](LICENSE)

Native Android plug-in for Tasker that performs a short Bluetooth LE scan for iBeacon advertisements and returns matching beacon details as Tasker local variables.

No ads, analytics, trackers, in-app purchases, or Internet permission. Bluetooth
scan results stay on the device unless returned locally to Tasker or another
Locale-compatible host.

The initial implementation of this app was written with OpenAI Codex.

## Tasker Usage

1. Install the APK from the [latest GitHub Release](https://github.com/smailzhu/ibeacon-tasker-plugin/releases/latest).
2. Grant Bluetooth and foreground location permissions from the launcher screen or from the plug-in configuration screen.
3. In Tasker, add it from `Tasks` -> `+` -> `Plugin` -> `Scan iBeacon`.
4. Configure UUID, major, minor, scan duration, and optional minimum RSSI.

A typical Tasker flow is `Tasks` -> `+` -> `Plugin` -> `Scan iBeacon`,
configure the scan filters, then use returned variables such as
`%ibeacon_found`, `%ibeacon_uuid`, `%ibeacon_major`, and `%ibeacon_minor` in
later Tasker actions.

The scan duration is the maximum wait time. The plug-in returns as soon as a
matching iBeacon is found.

Android treats Bluetooth LE scan results as location-capable. For Tasker scans
that run after the app is hidden, open the app's location settings and choose
`Allow all the time`.

Tasker scans run on demand, including after reboot when Android allows Tasker
and the plug-in to receive the automation event. When the optional compatibility
monitor is running, Tasker can also use iBeacons seen in the last 30 seconds
before falling back to a short on-demand scan.

On Android 13 and newer, Android can hide foreground-service notifications from
the notification drawer if notification permission is not granted. The launcher
screen requests notification permission only when you start the compatibility
monitor.

Background scan behavior and monitor-removal checks are documented in
[`docs/background-scan-debugging.md`](docs/background-scan-debugging.md).

Returned variables:

- `%ibeacon_found`
- `%ibeacon_uuid`
- `%ibeacon_major`
- `%ibeacon_minor`
- `%ibeacon_rssi`
- `%ibeacon_tx_power`
- `%ibeacon_distance`
- `%ibeacon_address`
- `%ibeacon_name`
- `%ibeacon_timestamp`
- `%errmsg` when the scan fails

## Troubleshooting

- Background scans need `Allow all the time` location access because Android
  treats Bluetooth LE scan results as location-capable.
- If Tasker background scans are unreliable on your device, start the
  compatibility monitor from the launcher screen and confirm battery usage for
  this app is not restricted.
- Tasker may return from a scan before the configured duration. The duration is
  a maximum wait time, not a fixed delay.
- GitHub APKs and F-Droid APKs use the same package id, but Android cannot
  update between them if they are signed by different keys. Uninstall once when
  switching sources.
- If you installed a pre-public APK, uninstall it once before installing current
  public builds because the package name or signing key may have changed.

More detailed background scan checks are in
[`docs/background-scan-debugging.md`](docs/background-scan-debugging.md).

## Updating Without Uninstalling

Android only installs an APK over an existing app when the package name and signing certificate are unchanged, and the new `versionCode` is not lower.

This project is set up for that:

- Stable package name: `io.github.smailzhu.ibeacontasker`
- GitHub Actions `versionCode`: the source default, for example `1`
- GitHub Actions `versionName`: the source default, for example `0.1.0`
- Repeatable signing: private GitHub Actions secrets

If you installed an early build with package name `com.smailzhu.ibeacontaskerplugin`,
uninstall it once before installing builds that use `io.github.smailzhu.ibeacontasker`.

Release signing uses these GitHub Actions repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Do not commit signing keys to the repository. If you installed a pre-public APK
signed by the old checked-in key, uninstall it once before installing builds
signed by the private GitHub Actions key.

To install an update from a downloaded release APK:

```bash
adb install -r app-release.apk
```

GitHub and F-Droid builds use the same package id. If they are signed by
different keys, Android cannot update one over the other. Uninstall once when
switching between GitHub APKs and F-Droid builds.

Do not change `applicationId` after creating Tasker profiles. Tasker identifies plug-ins partly by package and edit activity class.

## Local Build

```bash
./gradlew assembleDebug
```

The local machine needs Android SDK 36 and JDK 17.

## F-Droid

The repository includes source listing metadata in
`fastlane/metadata/android/en-US/`, including the phone screenshot at
`fastlane/metadata/android/en-US/images/phoneScreenshots/1.png`.

The file `fdroiddata/metadata/io.github.smailzhu.ibeacontasker.yml` is a
copy-ready template for an F-Droid metadata merge request. Before submitting,
confirm the release tag referenced by that file exists on GitHub:

```bash
git ls-remote --tags origin v0.1.0
```

The app uses package id `io.github.smailzhu.ibeacontasker`, license
`Apache-2.0`, no Internet permission, and default source version `0.1.0` with
`versionCode` `1`.

Publishing steps are documented in
[`docs/fdroid-publishing.md`](docs/fdroid-publishing.md).

## License

Apache-2.0. See [LICENSE](LICENSE).
