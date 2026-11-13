# TVSig: Updates from inside the app

- **Version:** 0.1 (draft)
- **Date:** November 13, 2026
- **Audience:** Maintainers who publish releases, and operators who update devices
- **Reference implementation:** `app/src/main/java/com/kutlusoy/tvsig/update/`, `UpdateViewModel.kt`, the section "Updates" in `SettingsActivity.kt`
- **See also:** [`guideline-release-signing.md`](./guideline-release-signing.md)

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. How it works

1. Settings, "Updates", button "Check for updates". The app asks `api.github.com/repos/kutlusoy/tvsig/releases/latest`. This is the newest release that is not a draft and not a pre-release. No account and no identifier is sent.
2. The tag of the release (`v0.0.4`) is turned into a version code with the scheme of `app/version.txt` (`major * 1,000,000 + minor * 1,000 + patch`) and compared with the installed version code. Only when the release is newer, its version number is shown. The release notes are not shown.
3. The button "Install version X" starts the update. The app downloads `SHA256SUMS.txt` and the APK `TVSig-X.apk` of the release, and compares the SHA-256 of the download with the checksum. A download without a matching checksum is deleted and never installed.
4. The APK is given to the Android package installer. Android checks that it is signed with the **same key** as the installed app, and refuses it otherwise.
5. After the installation the app is replaced. The receiver for `MY_PACKAGE_REPLACED` starts the service and tries to open the player. Android 10 and newer allow the screen to open from the background only when "Display over other apps" is allowed for TVSig. Without it the player stays closed until the check of the service brings it back (about 20 seconds, when the service runs) or until the app is opened by hand.

Nothing happens without a press of the button: there is no check in the background and no automatic installation.

## 2. What a release MUST contain

| File | Needed for |
|---|---|
| `TVSig-<version>.apk` | the app that is installed |
| `SHA256SUMS.txt` | the checksum of the APK, lines of the form `<sha256>  ./TVSig-<version>.apk` |

The release workflow creates both. A release without them can be seen in the check, but the installation is refused with a message.

## 3. Limits of Android

- The app needs the permission `REQUEST_INSTALL_PACKAGES` and the user MUST allow TVSig to "install unknown apps" once. The settings open the page of Android for it.
- Android may ask for a confirmation of the installation. Installing without a question is possible from Android 12 on, and only for an update of an app that was installed by the same installer before, so the first update through the app is confirmed on the device.
- Pre-releases are ignored. Debug builds (`com.kutlusoy.tvsig.debug`) never update themselves.
- A new release MUST be signed with the same key as the installed one, and its version code MUST be higher. Both are guaranteed by the release workflow when the key and `app/version.txt` are kept.
- The unauthenticated GitHub API allows 60 requests per hour and address. One check per button press is far below that.

## 4. Troubleshooting

| Message | Cause and fix |
|---|---|
| No release found | The repository has no published release yet, or only pre-releases. |
| Could not check for updates: no connection | The device cannot reach `api.github.com`. |
| The release has no APK file / no checksum file | The release was created by hand. Publish it with the release workflow. |
| The checksum of the download does not match | The download is damaged or the release was changed. Nothing was installed, try again. |
| Update failed with a message of the installer | Usually a different signing key (the release was signed with another key than the installed app) or an app with a higher version code is installed. |
| The app does not open again after the installation | "Display over other apps" is not allowed for TVSig (Settings, Apps, Special app access). Allow it once, see the README. Until then open the app by hand. |
| The button for the permission shows no page | Some TV systems do not offer "install unknown apps". Install the APK with `adb install -r` instead. |
