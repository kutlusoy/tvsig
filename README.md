# TvSig

Digital signage app for Android phones, tablets and Android TV. It plays images and videos from a folder on your own web server. There is no Google account, no Google Drive and no license check.

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
- The playlist loops endlessly.
- The server is checked every 15 minutes. Changed playlists and replaced files are picked up automatically.
- All media are downloaded to the device. If the network or the server is down, the app keeps playing the last synchronized content.

## Settings

Press the back key (or the back button of a TV remote) and enter the PIN. The default PIN is `0000`; change it in the settings.

- Base URL and subfolder
- Display duration per image
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

Requirements: Android Studio (or Gradle 8.13+), JDK 17, Android SDK 35.

```bash
gradle assembleDebug
```

For release builds, put a `signing/release.properties` file into `app/` with the keys `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. The `signing/` folder is ignored by git.

## Background operation

The app is built for 24/7 operation: it runs a foreground service, keeps the screen on, starts on boot and restarts itself after a crash. On some devices you have to allow auto start and disable battery optimization manually.

## License

MIT, see [LICENSE](LICENSE).
