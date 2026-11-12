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

"Verbindung testen" in the settings checks the source and reports how many images and videos were found.

### Google Drive

Instead of a web server, a publicly shared Google Drive folder ("Anyone with the link") can be used. In the settings, set the source to "Google Drive" and type the ID of the main folder, there is no Google account and no API key. This relies on the public folder view of Google, which is not an official interface, see [doc/guideline-google-drive.md](doc/guideline-google-drive.md) for the setup and the limits.

### Setup from a smartphone

For screens that only have a remote control, the settings offer "Einrichtung per Smartphone": the TV shows a QR code, the phone opens a setup page in the browser, and the source, the folder and the playback settings are entered there, with copy and paste. The page is served by the app only while the dialog is open and is protected by a one-time code in the QR code. See [doc/guideline-remote-setup.md](doc/guideline-remote-setup.md).

### Choosing the folder

Because a TV has no copy and paste, only the base URL (web server) or the ID of the main folder (Google Drive) is typed. "Ordner auswählen" then browses the folders level by level, up to five levels deep, and takes the chosen folder. Both sources work the same way. For a web server the list comes from the directory listing, or from a `folders.txt` (one folder name per line) in each folder if the server does not allow directory listings.

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
- Images and videos are shown completely, fitted into the screen. Where the content does not fill the screen, the empty area is filled with a very strongly blurred, evenly zoomed copy of the same picture (for videos of one frame of it). Nothing is ever stretched. A setting switches the backdrop to a mirrored continuation of the picture, which has no visible seam at the edge.
- Photos are shown in the right orientation (EXIF), large images are scaled down to the screen to save memory.
- Only the file types listed above are played. Other files in the folder are ignored.
- The server is checked every 15 minutes. Changed playlists and replaced files are picked up automatically.
- All media are downloaded to the device. If the network or the server is down, the app keeps playing the last synchronized content.

## Offline playback and up to date files

All media are stored on the device, so the playback does not depend on the network.

- **Start:** if media are stored, the playback starts at once from the device, without waiting for the server. The source is checked in the background and the playlist is replaced when it differs. This also holds when the network is up but the server or the internet is not reachable.
- **Only what is complete:** without the source only the files that are stored completely are played. Files that are missing are skipped without delay.
- **Network lost while running:** the playback continues. When the network is back, the source is checked again at once, otherwise every 15 minutes.
- **First start without network and without stored media:** the app shows a message and tries again every 30 seconds.

When the source is reachable, every stored file is compared with the server, not only by name:

- the size on the server against the size that was recorded at the download
- the change markers of the server (ETag, modification time, and the content hash where the server sends one)
- the size of the file on the device against the recorded size, so a truncated or damaged file is fetched again
- servers that send no change marker are checked by size, and small files (up to 20 MB) are downloaded again once a day

A changed file is downloaded next to the old one and only replaces it when it is complete, so a broken download never destroys what is playing. A file that disappears from the source is kept on the device for three days before it is removed, so one incomplete answer of a server cannot empty the device.

## Settings

The settings are made for remote controls (and can be filled in from a smartphone, see below): arrow keys move the focus, left and right change a value, OK confirms, and no touch or keyboard is needed except for typing the base URL. The PIN is entered on an on-screen number pad, so remotes without number keys work. The settings also show the app version, author and repository.

Press the back key (or the back button of a TV remote) and enter the PIN. The default PIN is `0000`; change it in the settings.

- Base URL and subfolder
- Display duration per image
- Backdrop around images: zoomed and blurred, or mirrored and blurred
- Transition effect: none, fade, slide (left, right, up, down), zoom in, zoom out or random (a different effect for every change)
- Screen orientation: landscape, 90, 180, 270 degrees (clockwise rotation of the content) or auto (sensor, phones and tablets only)
- Light or dark theme
- PIN
- Clear cache (deletes all downloaded media and loads them again)

On a TV remote, left and right skip to the previous or next item.

### Phones and TVs

The app tells the two apart: TVs, TV boxes and every device without a touchscreen count as TVs. On a TV the interface gets the same size on every screen (a layout of 960 by 540 dp, with safe margins), whatever density the device reports, so a 720p box, a 1080p TV and a 4K TV look alike. On phones and tablets the density of the system is kept and the dialogs switch to a compact side by side layout when the screen is low, for example a phone in landscape. On the first start without a source the settings open by themselves, and the message on the player screen can be tapped to open the PIN dialog.

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

Debug builds are always signed with the same key, `app/debug.keystore`, which is part of the repository on purpose (password `android`, only for debug builds, it protects nothing). So a debug APK from GitHub Actions, one built on another computer and one run from Android Studio can update each other without uninstalling the app. A release build uses the separate release key, see below, and cannot replace a debug build or the other way round: switch between them by uninstalling once.

For a signed local release build, put a `signing/release.properties` file into `app/` with the keys `storeFile` (file name of the keystore inside `app/signing/`), `storePassword`, `keyAlias` and `keyPassword`. The `signing/` folder is ignored by git.

## GitHub Actions

| Workflow | Trigger | What it does |
|---|---|---|
| `CI` | push to `main` or `integration`, pull requests | Unit tests, lint, debug APK as build artifact |
| `Release` | tag `v*` (for example `v0.0.1`) | Checks the tag against `app/version.txt`, runs the tests, builds the signed APK and AAB, verifies the APK (application ID, version, certificate), publishes a GitHub release. The release page lists the signing certificate (SHA-256 and SHA-1), the SHA-256 of the APK and the bundle, the version code, the Android versions and the source commit, and offers `SHA256SUMS.txt` (tags with a suffix such as `-rc1` become pre-releases) |

The release workflow needs these repository secrets (Settings, Secrets and variables, Actions):

| Secret | Required | Content |
|---|---|---|
| `TVSIG_KEYSTORE_BASE64` | yes | Keystore file as Base64 text |
| `TVSIG_KEYSTORE_PASSWORD` | yes | Keystore password |
| `TVSIG_KEY_ALIAS` | yes | Key alias, for example `tvsig-release` |
| `TVSIG_KEY_PASSWORD` | no | Key password, defaults to the keystore password |
| `TVSIG_RELEASE_CERT_SHA256` | no | Expected certificate fingerprint, the release fails on a mismatch |

The full step by step guide (creating the keystore, setting the secrets on Windows, Linux and macOS, running a release, troubleshooting) is in [doc/guideline-release-signing.md](doc/guideline-release-signing.md).

The version of the app is in one file, **`app/version.txt`** (for example `0.0.1`, or `0.1.0-rc1` for a pre-release). The Android version name is that text, the version code is derived from it (`major * 1,000,000 + minor * 1,000 + patch`, so `0.0.1` is 1, `0.1.0` is 1000 and `1.0.0` is 1000000). `tools/version.py` checks the file and the release workflow compares it with the tag.

To release: change `app/version.txt`, commit, then push the matching tag.

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

## Continuous operation

TVSig is built to run around the clock without anyone touching the device.

### What the app does

- **Foreground service:** keeps the app process alive and shows a quiet notification. It restarts itself when the system stops it.
- **Screen and wake lock:** the screen stays on and the device stays awake while the app runs.
- **Start after a reboot or an update:** the app starts on its own after the device boots and after the app was updated.
- **Watchdog:** if the player screen disappears (Home key, another window, the app was closed by the system), the service brings it back after about 20 seconds. This does not apply while the screen is off.
- **Crash recovery:** after a crash the app restarts. The waiting time doubles with every crash inside ten minutes (3 seconds up to 2 minutes), so a defect cannot cause a restart storm.
- **Stalled playback:** a video that stands still for a minute is skipped, and a playlist that shows nothing for three hours is loaded again.
- **No network:** the media are stored on the device and keep playing. The source is checked every 15 minutes, and again as soon as the network is back.
- **Memory:** large images are scaled down to the screen, the next item is prepared in advance, and memory is trimmed when the system asks for it.

### What you should set up on the device

The system can still decide to stop an app, especially on cheap TV boxes and phones with aggressive power saving. Do these once per device:

1. **Make TVSig the home app.** In the Android settings choose TVSig as the default home screen (on Android TV: Settings, Apps, Special app access or Default apps, Home screen). The system then starts the player by itself after the Home key or a restart of the launcher.
2. **Turn off battery optimization.** The app asks once on the first start, if you declined: Settings, Apps, TVSig, Battery, Unrestricted (the wording differs by vendor).
3. **Allow "Display over other apps".** Settings, Apps, Special app access, Display over other apps, TVSig. Without it Android 10 and newer can refuse to open the player from the background after a reboot.
4. **Allow auto start** on devices of vendors with their own power manager (for example Xiaomi, Huawei, Oppo, Vivo, Samsung "sleeping apps"): add TVSig to the allowed or protected apps.
5. **Never put the device to sleep.** Turn off sleep timers and screen savers, and keep the device on a permanent power supply.
6. **Keep the system current and the storage free.** A nearly full storage can stop downloads.

### Leaving the app on purpose

Press the back key, enter the PIN and choose "Beenden". This stops the app and its service for good, so the watchdog does not bring it back. It runs again after you start TVSig yourself or after the next reboot.

### If a device still stops

| Observation | What to check |
|---|---|
| The app is gone after some hours | Battery optimization and the vendor's auto start list (steps 2 and 4) |
| The screen goes black or dim | Sleep timer, screen saver and "turn off the display" options of the vendor |
| After a reboot the player does not show up | "Display over other apps" (step 3) and TVSig as home app (step 1) |
| The picture freezes | Check the video file on another device. A video that cannot be decoded is skipped, a short note in the message line appears only when no file at all can be played |
| Everything stops when the network is lost | Update to the current version. Media are cached, only the first start needs the source once |

## Android developer verification

Google asks for a registration of package name and signing key for apps on certified devices. The repository is prepared: the release workflow checks the proof file `app/src/main/assets/adi-registration.properties`, and the release page says whether it is part of the APK. The file holds the snippet of your developer account and is added once, see [doc/guideline-android-developer-verification.md](doc/guideline-android-developer-verification.md).

## Graphics

All icons, banners and the splash logo are generated from `design/TVSig_icon.svg`. To change the logo, replace the SVG and run:

```bash
npm install playwright
node design/generate-assets.js
```

The script writes the launcher icons (adaptive, monochrome and legacy), the Android TV banner, the splash logo, the notification icon, the store icon and the repository graphics (`doc/logo.png`, `doc/social-preview.png`). The social preview image can be uploaded in the repository settings under "Social preview".

## License

MIT, see [LICENSE](LICENSE).
