# TVSig: Using a shared Google Drive folder

- **Version:** 0.1 (draft)
- **Date:** November 11, 2026
- **Audience:** Operators who keep the media of their screens in Google Drive
- **Reference implementation:** `app/src/main/java/com/kutlusoy/tvsig/repositories/DriveSource.kt`, `MediaRepository.kt`

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. What it is, and what it is not

TVSig can play the media of a publicly shared Google Drive folder. There is no Google account on the device, no API key and no Google service in the app. The app reads the public folder view and downloads the files by their share link.

This is **not an official Google API**. It depends on how Google publishes shared folders, and Google can change or limit that at any time. For screens that MUST keep running, a web server of your own (see the README) is the more reliable source. Drive is the convenient choice for a few screens and for content that changes often.

Limits of the Drive source:

- The folder MUST be shared with "Anyone with the link". A folder that is only shared with named people does not work, the app has no login.
- Google limits the downloads of a file that is requested very often. When a file is blocked, the app shows a message and skips the file until the limit is lifted. Many screens that load the same big video at the same time can reach this limit.
- Changed files are detected by their size and modification time. Replace a file by uploading a new version of it (right click, "Manage versions"), or give a new file a new name.
- Google Docs, Sheets and Slides are not media and are ignored.
- The order is alphabetical by file name. Use numbers in front of the names to control the order: `01-welcome.jpg`, `02-menu.png`, `03-video.mp4`.

## 2. Prepare the folders

1. Create one main folder in Drive, for example `TVSig`.
2. Inside it, create one subfolder per screen, for example `Lobby`, `Reception North`, `Canteen`. A folder name can be as long as you like, it is picked from a list on the TV.
3. Put the images (JPG, PNG, WebP) and videos (MP4, MPEG, WebM) of each screen into its subfolder. Do not nest further folders.
4. Share the **main folder**: right click, "Share", "General access", "Anyone with the link", role "Viewer". The subfolders inherit this.
5. Copy the link of the main folder ("Copy link"). It looks like `https://drive.google.com/drive/folders/1AbC...?usp=sharing`.

A screen that should show the same content as another screen can use the same subfolder.

## 3. Set up the screen

1. Open the settings (back key, PIN).
2. Enter the link of the main folder in **Basis-URL oder Google-Drive-Ordnerlink**. All usual link forms work, the bare folder ID works as well.
3. Press **Ordner auswählen** and choose the subfolder of this screen from the list. "(Hauptordner verwenden)" plays the files of the main folder itself.
4. Press **Verbindung testen**. The app reports how many images and videos it found.
5. Press **Speichern**.

The app downloads the media and keeps playing them when the network is down. The folder is checked again every 15 minutes, new, changed and removed files are picked up without any action on the screen.

## 4. Troubleshooting

| Message | Cause and fix |
|---|---|
| `Drive folder not found or not shared with anyone who has the link` | The folder is not shared with "Anyone with the link", the link is wrong, or Google no longer serves the public folder view. Open the link in a private browser window: if it asks for a login, the sharing is not right. |
| `No Google Drive folder ID found in the link` | The text is not a folder link. Copy the link of the folder, not of a file. |
| `No media found` | The folder has no JPG, PNG, WebP or video files, or they are in a deeper subfolder. |
| `The server sent a web page instead of the file` | Google blocked the download of that file (download limit or no access). Try again later, make sure the file itself is not restricted, or put big videos on a web server. |
| `Subfolder not found: ...` | The saved subfolder was renamed or removed in Drive. Pick it again. |

## 5. Checklist

- [ ] Main folder shared with "Anyone with the link" (Viewer)
- [ ] One subfolder per screen, files named in the wanted order
- [ ] Link entered, subfolder picked, connection test shows the expected file count
- [ ] Network cable pulled once on a test screen: playback continues from the stored files
