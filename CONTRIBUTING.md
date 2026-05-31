# Contributing

Thanks for helping improve iBeacon Tasker Plugin. Keep changes small, focused,
and easy to review.

## Development Setup

- Use JDK 17.
- Install Android SDK platform 36.
- Keep `applicationId` as `io.github.smailzhu.ibeacontasker`.

Build locally with:

```bash
./gradlew lintDebug assembleDebug assembleRelease
```

## Pull Requests

- Open an issue first for behavior changes, permission flow changes, or release
  process changes.
- Keep UI text short and clear; this app is a utility, not a marketing page.
- Verify Tasker behavior when changing scan config, permissions, receivers, or
  background scan behavior.
- Do not commit generated build output.

## Signing And Secrets

- Do not commit signing keys, keystores, passwords, or release secrets.
- GitHub release APKs are signed by repository secrets.
- F-Droid signs its own builds unless reproducible builds are configured later.

## Versions And Releases

- `app/build.gradle` is the source of truth for `versionName` and `versionCode`.
- Release tags use `v<versionName>`, for example `v0.1.0`.
- Release APK artifacts are published only from `v*` tag builds.
- Do not rewrite public release tags after the app is shared through F-Droid or
  other public channels unless there is a serious security issue.

## Store And F-Droid Metadata

- Store metadata lives in `fastlane/metadata/android/en-US/`.
- F-Droid metadata template lives at
  `fdroiddata/metadata/io.github.smailzhu.ibeacontasker.yml`.
- Phone screenshots belong under
  `fastlane/metadata/android/en-US/images/phoneScreenshots/` with sequential
  names such as `1.png`.
