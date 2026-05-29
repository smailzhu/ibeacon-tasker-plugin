# F-Droid Publishing Notes

Use these notes when submitting this app to the main F-Droid repository.

## Before Submitting

- Make the GitHub repository public.
- Keep `applicationId` as `io.github.smailzhu.ibeacontasker`.
- Keep signing keys out of git. GitHub APKs are signed with private GitHub Actions secrets; F-Droid will sign its own builds unless reproducible builds are configured later.
- Confirm the app has a FOSS license file and only FOSS dependencies.
- Confirm the release commit is tagged. For the first public release, use `v0.1.0`.
- Confirm `fastlane/metadata/android/en-US/` has title, descriptions, changelog, graphics, and at least one phone screenshot.
- Confirm phone screenshots are in `fastlane/metadata/android/en-US/images/phoneScreenshots/` and use sequential names such as `1.png`.
- Confirm `fdroiddata/metadata/io.github.smailzhu.ibeacontasker.yml` points to the current version and tag.

If you installed a GitHub APK before switching to an F-Droid build, uninstall once. Android will not update across different signing keys.

## Metadata Merge Request Path

This repo already includes a copy-ready metadata file:

```text
fdroiddata/metadata/io.github.smailzhu.ibeacontasker.yml
```

To submit it:

```bash
git clone https://gitlab.com/YOUR_GITLAB_USERNAME/fdroiddata.git
cd fdroiddata
git checkout -b io.github.smailzhu.ibeacontasker
cp /path/to/ibeacon-tasker-plugin/fdroiddata/metadata/io.github.smailzhu.ibeacontasker.yml metadata/io.github.smailzhu.ibeacontasker.yml
git add metadata/io.github.smailzhu.ibeacontasker.yml
git commit -m "New App: io.github.smailzhu.ibeacontasker"
git push origin io.github.smailzhu.ibeacontasker
```

Open a merge request against:

```text
https://gitlab.com/fdroid/fdroiddata
```

Watch the GitLab pipeline and reply to reviewer questions.

## Optional Local Checks

If `fdroidserver` is installed, run these from the `fdroiddata` checkout:

```bash
fdroid readmeta
fdroid rewritemeta io.github.smailzhu.ibeacontasker
fdroid checkupdates --allow-dirty io.github.smailzhu.ibeacontasker
fdroid lint io.github.smailzhu.ibeacontasker
fdroid build -v -l io.github.smailzhu.ibeacontasker
```

The F-Droid GitLab CI can also validate the metadata after the branch is pushed.

## Simpler RFP Path

For a first-time contributor, it is also acceptable to open an RFP issue:

```text
https://gitlab.com/fdroid/rfp/-/issues
```

Include the GitHub source URL, package id, license, current tag, and a note that this repo already has Fastlane metadata and an fdroiddata YAML template.

## Screenshot Metadata

The source repo includes a phone screenshot for F-Droid and Play metadata at:

```text
fastlane/metadata/android/en-US/images/phoneScreenshots/1.png
```

Keep screenshots as PNG or JPEG files under the Fastlane locale's
`images/phoneScreenshots/` directory. Use sequential filenames such as `1.png`,
`2.png`, and so on, so F-Droid imports them in a predictable order.

## Future Updates

After F-Droid accepts the app:

- Bump `versionName` and `versionCode` in `app/build.gradle`.
- Add a matching changelog file under `fastlane/metadata/android/en-US/changelogs/`.
- Tag the release as `v<versionName>`.
- Push the tag.

The metadata is configured with tag-based update checks, so F-Droid should detect future tagged releases.

## References

- F-Droid submitting quick start: https://gitlab.com/fdroid/fdroid-website/-/raw/master/_docs/Submitting_to_F-Droid_Quick_Start_Guide.md
- F-Droid Data contributing guide: https://gitlab.com/fdroid/fdroiddata/-/raw/master/CONTRIBUTING.md
- fdroiddata repository: https://gitlab.com/fdroid/fdroiddata
