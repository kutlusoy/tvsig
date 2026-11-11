# TVSig: Setup from a smartphone

- **Version:** 0.1 (draft)
- **Date:** November 11, 2026
- **Audience:** Operators who set up screens that only have a remote control
- **Reference implementation:** `app/src/main/java/com/kutlusoy/tvsig/remote/`, `app/src/main/assets/remote_setup.html`

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Why

A TV has no copy and paste and a remote control makes typing slow. The setup from a smartphone moves the typing to the phone: the TV shows a QR code, the phone opens a page, and the source, the folder and the playback settings are entered there. A link or an ID can be pasted from any app.

## 2. Steps

1. On the TV open the settings (back key, PIN) and choose **Einrichtung per Smartphone**.
2. The TV shows a QR code and an address such as `192.168.1.23:8080`.
3. Connect the phone to the **same network** as the TV and scan the QR code with the camera. The setup page opens in the phone browser.
4. On the page choose the source (web server or Google Drive), paste the base URL or the Drive folder ID, and press **Ordner auswählen** to browse the folders level by level. **Verbindung testen** reports how many images and videos were found.
5. Choose the display time, the transition and the orientation, then press **Auf dem Fernseher speichern**.

The TV saves the values, closes the settings and starts the playback. The PIN cannot be changed from the phone.

## 3. Security

- The page is served by the app itself, only while the dialog on the TV is open, and stops when it is closed or after 10 minutes.
- Every request needs the one-time token that is part of the QR code. The token is created again each time the dialog opens. Without seeing the screen of the TV nobody can use the page.
- The connection is plain HTTP inside your local network, it is not encrypted. Do not use it on a network that you do not trust.
- The settings that can be changed from the phone are validated on the TV, the PIN is not part of them.

## 4. Troubleshooting

| Problem | Cause and fix |
|---|---|
| The page does not open | Phone and TV are not in the same network. Guest networks and "client isolation" of a router block the connection. Use the normal network of the TV. |
| `Keine Netzwerkverbindung` on the TV | The TV has no network address. Check the cable or the WiFi. |
| The page says that the link is invalid | The dialog was closed and opened again, which creates a new token. Scan the new QR code. |
| The QR code does not scan | Increase the brightness of the TV and hold the phone still, or type the address shown below the code into the phone browser together with `/?t=` and the token, which is only inside the QR code, so scanning is the way. |
| The port is busy | The app tries the ports 8080 to 8099 and takes the first free one. The port is part of the shown address. |

## 5. Checklist

- [ ] Phone and TV in the same network
- [ ] QR code scanned, the page opens
- [ ] Connection test shows the expected file count
- [ ] Saved, the TV starts the playback
