<p align="center"><img src="doc/logo.png" alt="TVSig" width="280"></p>

# TVSig

Android digital signage app for phones, tablets and Android TV. It plays images and videos from a folder on your own web server. There is no Google account, no Google Drive and no license check.

- **Application ID:** `com.kutlusoy.tvsig`
- **Version:** 0.0.1
- **Min SDK:** 24 (Android 7.0)
- **License:** MIT

## How it works

The app is configured with two values:

| Setting | Example | Meaning |
|---|---|---|
| Base URL | `https://example.com/signage` | Root of your web host |
| Subfolder | `lobby-screen` | Folder for this device |

The media are loaded from `<Base URL>/<Subfolder>/`, here `https://example.com/signage/lobby-screen/`. Use a different subfolder per screen to give every device its own content.

### Playlist

The app builds the playlist in one of two ways:

1. **`playlist.txt`** (optional): if the folder contains this file, it defines the playlist. One file name per line, played in the given order. Empty lines and lines starting with `#` are ignored. Full `http(s)` URLs are allowed as well.
2. **Directory listing**: otherwise the app reads the automatic folder index of the web server (Apache `Options +Indexes`, nginx `autoindex on`) and plays all supported files sorted by name.

Supported files:

| Type | Extensions |
|---|---|
| Images | `jpg`, `jpeg`, `png`, `webp` |
| Videos | `mp4`, `mpeg`, `mpg`, `webm` |

### Playback

- Images are shown for the configured duration (minimum 3 seconds), videos are played to the end.
- The playlist loops endlessly. Files that cannot be played are skipped, and when none of them works the app shows a message and tries again after 30 seconds.
- The next file is downloaded in advance, so there is no pause between two items.
- Photos are shown in the right orientation (EXIF), large images are scaled down to the screen to save memory.
- Only the file types listed above are played. Other files in the folder are ignored.
- The server is checked every 15 minutes. Changed playlists and replaced files are picked up automatically.
- All media are downloaded to the device. If the network or the server is down, the app keeps playing the last synchronized content.

## Settings

Press the back key (or the back button of a TV remote) and enter the PIN. The default PIN is `0000`; change it in the settings.

- Base URL and subfolder
- Display duration per image
- Transition effect: none, fade, slide (left, right, up, down), zoom in, zoom out or random (a different effect for every change)
- Screen orientation: landscape, 90, 180, 270 degrees (clockwise rotation of the content) or auto (sensor, phones and tablets only)
- Light or dark theme
- PIN
- Clear cache (deletes all downloaded media and loads them again)

On a TV remote, left and right skip to the previous or next item.

### Rotated screens

For a TV mounted upright or upside down, pick 90, 180 or 270 degrees in the settings. The app locks the system to landscape and lays out the content for the rotated screen (so a portrait layout fills an upright TV). The settings screen and the PIN dialogs rotate the same way. If the picture is upside down, choose the opposite angle (90 or 270).

## Server setup example

nginx:

```nginx
location /signage/ {
    alias /var/www/signage/;
    autoindex on;
}
```

Apache:

```apache
<Directory /var/www/html/signage>
    Options +Indexes
</Directory>
```

Then put the files into `/var/www/signage/lobby-screen/` and set the subfolder to `lobby-screen`. If your host does not allow directory listings, add a `playlist.txt` instead.

## Build

Requirements: Android Studio (or just JDK 17 and the Android SDK 35). The Gradle wrapper is included.

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

For a signed local release build, put a `signing/release.properties` file into `app/` with the keys `storeFile` (file name of the keystore inside `app/signing/`), `storePassword`, `keyAlias` and `keyPassword`. The `signing/` folder is ignored by git.

## GitHub Actions

| Workflow | Trigger | What it does |
|---|---|---|
| `CI` | push to `main` or `integration`, pull requests | Unit tests, lint, debug APK as build artifact |
| `Release` | tag `v*` (for example `v0.0.1`) | Checks the tag against `versionName`, runs the tests, builds the signed APK and AAB, verifies the certificate, publishes a GitHub release with `SHA256SUMS.txt` (tags with a suffix such as `-rc1` become pre-releases) |

The release workflow needs these repository secrets (Settings, Secrets and variables, Actions):

| Secret | Required | Content |
|---|---|---|
| `TVSIG_KEYSTORE_BASE64` | yes | Keystore file as Base64 text |
| `TVSIG_KEYSTORE_PASSWORD` | yes | Keystore password |
| `TVSIG_KEY_ALIAS` | yes | Key alias, for example `tvsig-release` |
| `TVSIG_KEY_PASSWORD` | no | Key password, defaults to the keystore password |
| `TVSIG_RELEASE_CERT_SHA256` | no | Expected certificate fingerprint, the release fails on a mismatch |

The full step by step guide (creating the keystore, setting the secrets on Windows, Linux and macOS, running a release, troubleshooting) is in [doc/guideline-release-signing.md](doc/guideline-release-signing.md).

To release: raise `versionName` and `versionCode` in `app/build.gradle`, then push the tag.

```bash
git tag v0.0.1
git push origin v0.0.1
```

## Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Load the media from your web server and detect when the network is back |
| `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keep playing around the clock |
| `RECEIVE_BOOT_COMPLETED` | Start after a reboot |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ask once to be excluded from battery optimization |
| `SYSTEM_ALERT_WINDOW` | Optional: if "Display over other apps" is allowed in the Android settings, the app can open itself after a reboot on Android 10 and newer |

## Background operation

The app is built for 24/7 operation: it runs a foreground service, keeps the screen on, starts on boot and restarts itself after a crash. On some devices you have to allow auto start and disable battery optimization manually.

## Graphics

All icons, banners and the splash logo are generated from `design/TVSig_icon.svg`. To change the logo, replace the SVG and run:

```bash
npm install playwright
node design/generate-assets.js
```

The script writes the launcher icons (adaptive, monochrome and legacy), the Android TV banner, the splash logo, the notification icon, the store icon and the repository graphics (`doc/logo.png`, `doc/social-preview.png`). The social preview image can be uploaded in the repository settings under "Social preview".

## License

MIT, see [LICENSE](LICENSE).
