# TvSig: Release Signing and GitHub Secrets

- **Version:** 0.1 (draft)
- **Date:** November 11, 2026
- **Audience:** Maintainers who create the release key and run the release workflow
- **Reference implementation:** `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `app/build.gradle` (signing config) in this repository

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Workflows

| Workflow | Start | Signing | Result |
|---|---|---|---|
| `CI` | push to `main` or `integration`, pull requests, manual | none (debug key of the runner) | test results, lint report, debug APK as build artifact |
| `Release` | push of a tag `v*`, or manual with the version | the release keystore from the repository secrets | GitHub release with APK, AAB, `SHA256SUMS.txt` and R8 mapping |

`CI` needs no secrets. The `Release` workflow stops with an error when a required secret is missing, so it cannot publish an unsigned or wrongly signed release by accident.

A tag with a suffix such as `v0.1.0-rc1` is published as a pre-release. The tag MUST match `versionName` in `app/build.gradle` (`0.1.0-rc1` in this example), otherwise the workflow stops.

Only repository secrets are used. The tab "Variables" is not needed, there are no repository variables.

## 2. Create the release keystore

Do this once, offline, on a machine you trust. You need a JDK (`keytool` is part of it).

Bash:

```bash
keytool -genkeypair -v -keystore tvsig-release.jks -alias tvsig-release \
  -keyalg RSA -keysize 4096 -sigalg SHA384withRSA -validity 50040
```

PowerShell (the line continuation is a backtick, not a backslash):

```powershell
keytool -genkeypair -v -keystore tvsig-release.jks -alias tvsig-release `
  -keyalg RSA -keysize 4096 -sigalg SHA384withRSA -validity 50040
```

Both work as one single line as well.

- `-validity 50040` is 137 years. The number is symbolic, Android only needs the certificate to outlive the app.
- The keystore password and the key password MUST be long and unique. Store them in a password manager. When keytool asks for the key password, pressing Enter reuses the keystore password, which is fine here.
- Keep at least two offline backups of `tvsig-release.jks` in different places. **If this file is lost, no update can be signed any more, and every user has to uninstall and reinstall the app.**
- The keystore MUST NOT be committed, pasted into chats or stored anywhere except the offline backups and the repository secrets. `.gitignore` already excludes `*.jks`, `*.keystore` and `app/signing/`.
- Print the certificate fingerprint and note it down:

Bash:

```bash
keytool -list -v -keystore tvsig-release.jks -alias tvsig-release | grep SHA256
```

PowerShell:

```powershell
keytool -list -v -keystore tvsig-release.jks -alias tvsig-release | Select-String SHA256
```

The `SHA256:` line under "Certificate fingerprints" is the fingerprint.

## 3. Repository secrets

Open the repository on GitHub, then Settings, Secrets and variables, Actions, tab "Secrets", button "New repository secret". Enter the name exactly as written, paste the value, "Add secret".

| Secret | Required | Value |
|---|---|---|
| `TVSIG_KEYSTORE_BASE64` | yes | the Base64 text of the keystore file, see below |
| `TVSIG_KEYSTORE_PASSWORD` | yes | keystore password |
| `TVSIG_KEY_ALIAS` | yes | `tvsig-release` |
| `TVSIG_KEY_PASSWORD` | no | key password, defaults to the keystore password |
| `TVSIG_RELEASE_CERT_SHA256` | no | the expected certificate SHA-256 from §2, then every release run fails if the APK carries another certificate |

Secrets cannot be read again after saving, only replaced. Use "Repository secrets", not "Environment secrets" (the workflow does not use environments).

### 3.1 Base64 text of the keystore

The keystore value is one very long line, so the easiest way is to write it to a text file and copy it from there.

Windows (PowerShell, not `cmd`), run in the folder that holds the keystore:

```powershell
cd C:\path\to\folder
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\tvsig-release.jks).Path)) | Out-File -Encoding ascii -NoNewline keystore-base64.txt
notepad keystore-base64.txt
```

Linux:

```bash
base64 -w0 tvsig-release.jks > keystore-base64.txt
```

macOS:

```bash
base64 -i tvsig-release.jks | tr -d '\n' > keystore-base64.txt
```

Open `keystore-base64.txt`, select everything (Ctrl+A), copy it (Ctrl+C) and paste it into the secret field `TVSIG_KEYSTORE_BASE64`. The text must be one single line with no spaces and no line breaks, and the file does not need to be saved again. When the secret is stored, delete `keystore-base64.txt`, because it contains your signing key in readable form.

`Resolve-Path` is needed because .NET resolves a bare file name against its own working directory, which can differ from the folder shown by PowerShell, and then reports a missing file. To copy to the clipboard without a file, replace `Out-File ...` with `Set-Clipboard`.

### 3.2 Alternative: GitHub CLI

With the [GitHub CLI](https://cli.github.com/) installed and logged in (`gh auth login`), the secrets can be set from a terminal in the keystore folder. The values are read from the prompt or from files, so they do not end up in the shell history.

```bash
gh secret set TVSIG_KEYSTORE_BASE64 --repo kutlusoy/tvsig < keystore-base64.txt
gh secret set TVSIG_KEYSTORE_PASSWORD --repo kutlusoy/tvsig
gh secret set TVSIG_KEY_ALIAS --repo kutlusoy/tvsig --body tvsig-release
gh secret set TVSIG_KEY_PASSWORD --repo kutlusoy/tvsig
gh secret set TVSIG_RELEASE_CERT_SHA256 --repo kutlusoy/tvsig
```

Check which secrets exist (names only, never values):

```bash
gh secret list --repo kutlusoy/tvsig
```

## 4. Run a release

1. Raise `versionName` and `versionCode` in `app/build.gradle` and merge the change to `main`. `versionCode` MUST grow with every release, Android refuses an update with a lower or equal code.
2. Tag the commit with `v` plus the version and push the tag:

```bash
git tag v0.0.2
git push origin v0.0.2
```

   Or open Actions, workflow "Release", "Run workflow" and enter the version without the `v`.
3. The workflow checks the tag against `versionName`, runs the unit tests, builds the signed APK and AAB, verifies the signature with `apksigner verify --print-certs`, and publishes the files, `SHA256SUMS.txt` and notes with the certificate fingerprint.
4. Compare the fingerprint in the release notes with the one you noted in §2.

A failed run can be started again with "Re-run jobs". A run for an existing release replaces its files.

## 5. Signed build on your own machine

For a local signed build, create `app/signing/release.properties`:

```properties
storeFile=tvsig-release.jks
storePassword=...
keyAlias=tvsig-release
keyPassword=...
```

Copy the keystore into `app/signing/` and run `./gradlew assembleRelease`. The APK is written to `app/build/outputs/apk/release/`. Without this file the release build is unsigned.

## 6. Android Developer Verification

Google is introducing a verification of developers for apps installed on certified Android devices, also for apps outside Google Play. Check the current rules and dates in the Android Developer Console documentation before you register, they change.

- A personal account is enough. Distribution through Google Play is not required.
- Register the package name `com.kutlusoy.tvsig` with the SHA-256 fingerprint of the release certificate from §2.
- Keep the release key unchanged for the lifetime of the app. A key change requires Android key rotation and a new registration.
- Keep the account and its identity documents up to date. A lapsed verification can block installs on certified devices.

## 7. Troubleshooting

| Message | Cause and fix |
|---|---|
| `The repository secret for ... is missing` | The secret name is misspelled or the secret is set as an environment secret. Check §3, names are case sensitive. |
| `base64: invalid input` | The value of `TVSIG_KEYSTORE_BASE64` contains spaces or line breaks. Create it again as one line, see §3.1. |
| `Keystore was tampered with, or password was incorrect` | `TVSIG_KEYSTORE_PASSWORD` is wrong. |
| `Cannot recover key` | `TVSIG_KEY_PASSWORD` is wrong, or the key password differs from the keystore password and `TVSIG_KEY_PASSWORD` is not set. |
| `Tag ... does not match versionName` | Change `versionName` in `app/build.gradle`, or delete the tag and tag again. |
| `does not match TVSIG_RELEASE_CERT_SHA256` | The APK is signed with another key than expected. Check the keystore secret, or update the expected fingerprint when you changed the key on purpose. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` on a device | The installed app was signed with another key (for example a debug build). Uninstall it once, then install the release. |

## 8. Checklist

- [ ] Release keystore created offline (§2), two backups stored
- [ ] Certificate fingerprint noted
- [ ] Repository secrets set (§3)
- [ ] Package name registered with the fingerprint (§6)
- [ ] First release run green, fingerprint in the notes matches
- [ ] Update from the first release to the next one tested on a device
