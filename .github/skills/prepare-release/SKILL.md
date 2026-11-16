---
name: prepare-release
description: "Prepares and publishes a new release of the FileManager Android app. Use when: creating a release, bumping version, publishing APK, updating changelog, releasing new version. Covers version bump in build.gradle, all README/DEVELOPMENT changelog updates, in-app strings, LICENSE year, memory file cleanup, APK build, update-compatibility checks, commit and push, and gh release publish."
argument-hint: "New version number, e.g. 1.3.5"
---

# prepare-release

## When to use
- User says "crea la release X.Y.Z", "prepara la versión X.Y.Z", "sube la nueva versión", "nueva release"
- Any time a new version needs to be built and published to GitHub

## Project facts
- **Repo**: `fraugz/FileManager` (gh CLI configured)
- **Package**: `com.fraugz.filemanager`
- **Workspace root**: `c:\Users\50023654\Downloads\GestorArchivos3`
- **Language**: Java (Android)
- **License**: MIT — year in `LICENSE` line 1 must be current year

---

## Step 0 — Verify prerequisites

```powershell
gh --version
gh auth status
```

- `gh` must be present and logged in as `fraugz`.
- If not authenticated: `gh auth login` and follow prompts.

---

## Step 1 — Decide version numbers

Given new version `X.Y.Z`:
- `versionName` = `"X.Y.Z"`
- `versionCode` = `X × 10000 + Y × 100 + Z`, e.g. `1.3.11` → `10311`, `1.4.0` → `10400`

The new `versionCode` **must be greater** than the one currently in `app/build.gradle`: Android refuses to
install an update with a lower code. Check it before continuing.

> Versions up to 1.3.10 used the digits of the version (`1.3.5` → `135`, `1.3.10` → `1310`). That rule
> stops growing at the next minor version (`1.4.0` → `140`, lower than `1310`), so do not use it.
> The formula above gives values higher than `1310` for every later version, as long as `Y` and `Z`
> stay below 100.

---

## Step 2 — Bump version in `app/build.gradle`

File: `app/build.gradle`

Two lines to update inside `defaultConfig { ... }`:
```
versionCode <old>   →   versionCode <new>
versionName "<old>" →   versionName "<new>"
```

---

## Step 3 — Update in-app changelog (`strings.xml` × 2)

Files:
- `app/src/main/res/values/strings.xml` (English)
- `app/src/main/res/values-es/strings.xml` (Spanish)

Update the `changelog_content` string to list the NEW version first, followed by a summary of changes (use `\n` for line breaks, escape `"` as `\"`). The string is shown in a dialog in Settings → version row.

Pattern:
```xml
<string name="changelog_content">vX.Y.Z\n- Change 1\n- Change 2\n\nvX.Y.Z-1\n- ...</string>
```

---

## Step 4 — Update repo documentation (changelog sections)

Update ALL four docs — never leave any behind:

| File | Section |
|------|---------|
| `README.md` | `## Versions & changelog` |
| `README.es.md` | `## Versiones & changelog` |
| `DEVELOPMENT.md` | `## Versions and Changelog` |
| `DEVELOPMENT.es.md` | `## Versiones y Changelog` |

Add the new version as the **first bullet** in each section. Use a short but complete description. Spanish and English must match in substance.

---

## Step 5 — Check LICENSE year

File: `LICENSE`, line 1:
```
MIT License

Copyright (c) <YEAR> fraugz
```

If `<YEAR>` is not the current calendar year, update it.

---

## Step 6 — Update memory file

File (repo memory): `/memories/repo/gestorarchivos3.md`

Remove stale/completed notes (old PENDING items, resolved bugs, outdated build notes).
Add a brief line noting the new release and any significant new patterns introduced.

This file is not reachable from every environment. If it is not available, skip this step and say so
in the final report instead of failing the release.

---

## Step 7 — Build release APK

```powershell
cd "c:\Users\50023654\Downloads\GestorArchivos3"
.\gradlew.bat --no-daemon --console=plain :app:assembleRelease
```

**Signing behaviour** (automatic, no action needed):
- If `FM_STORE_FILE` / `FM_STORE_PASSWORD` / `FM_KEY_ALIAS` / `FM_KEY_PASSWORD` are set (env or `local.properties`) → custom release keystore used.
- Otherwise → falls back to `~/.android/debug.keystore` (signed, but not for Play Store).

**Success**: last lines will include `BUILD SUCCESSFUL`.
**Output**: `app/build/outputs/apk/release/app-release.apk`

If the build hangs at daemon startup, wait — it recovers automatically (takes ~30–90 s).

---

## Step 8 — Copy APK to release-assets and write the release notes

```powershell
Copy-Item "app\build\outputs\apk\release\app-release.apk" `
          "release-assets\filemanager-vX.Y.Z.apk" -Force
```

Verify:
```powershell
Get-Item "release-assets\filemanager-vX.Y.Z.apk" | Select-Object Name, Length
```

Create `release-assets\notes-vX.Y.Z.md` with the user-facing notes in English (a `## What's new` list,
plus `## Fixes` when there are any). `release-assets/` is git-ignored, so neither file is committed.

---

## Step 9 — Check that existing installs can update

Do both checks before anything is pushed or published.

**Same signing certificate as the previous release.** If it differs, nobody can update.

```powershell
.\gradlew.bat --no-daemon --console=plain :app:printReleaseSigningInfo
$apksigner = (Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools\*\apksigner.bat" | Select-Object -Last 1).FullName
& $apksigner verify --print-certs "app\build\outputs\apk\release\app-release.apk"
& $apksigner verify --print-certs "release-assets\filemanager-v<previous>.apk"
```

`printReleaseSigningInfo` must report the custom keystore, and the `certificate SHA-256 digest` line
must be identical for both APKs.

**The new APK installs over the previous one.** On an emulator or test device (`adb` is not on PATH):

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install "release-assets\filemanager-v<previous>.apk"
& $adb install -r "app\build\outputs\apk\release\app-release.apk"
& $adb shell am start -n com.fraugz.filemanager/.MainActivity
```

Both installs must print `Success` (a lower `versionCode` or a different signature fails here) and the
app must start as the new version. If no emulator or device is available, say so in the final report.

---

## Step 10 — Commit and push

The release tag is created from `main` on GitHub, so the version bump must be there first.

```powershell
git add -A
git commit -m "Release vX.Y.Z - <short summary>"
git push origin main
git status -sb
```

`git status -sb` must not show the branch as `ahead`.

If the push fails with `Cannot prompt because user interactivity has been disabled`, reuse the `gh`
credential for that one command:

```powershell
git -c credential.helper= -c "credential.helper=!gh auth git-credential" push origin main
```

---

## Step 11 — Publish GitHub release

> ⚠️ **IMPORTANT**: `UpdateChecker` downloads `app-release.apk` from the release assets by name.
> The release **must** include an asset whose file name is exactly `app-release.apk`, in addition to
> the human-readable `filemanager-vX.Y.Z.apk`. Upload the real file from the build output:
> `"file.apk#app-release.apk"` only sets a display label and does **not** rename the asset.

Publish as a draft first, so the release never becomes public without its APKs.

1. Create the draft:

```powershell
gh release create vX.Y.Z --repo fraugz/FileManager --draft --target main `
  --title "vX.Y.Z" --notes-file "release-assets\notes-vX.Y.Z.md"
```

2. Upload both APKs (`--clobber` makes re-runs safe):

```powershell
gh release upload vX.Y.Z --repo fraugz/FileManager --clobber `
  "release-assets\filemanager-vX.Y.Z.apk" `
  "app\build\outputs\apk\release\app-release.apk"
```

If the upload does not finish (seen on a slow uplink, about 33 KB/s: `gh` never completed, with no
error), stop it and upload each file with curl over HTTP/1.1 — about 5 minutes per 10 MB APK:

```powershell
$id = ((gh api repos/fraugz/FileManager/releases | ConvertFrom-Json) |
       Where-Object { $_.draft -and $_.tag_name -eq 'vX.Y.Z' }).id
$token = gh auth token
curl.exe --http1.1 -sS --max-time 1500 -X POST `
  -H "Authorization: Bearer $token" -H "Content-Type: application/vnd.android.package-archive" `
  --data-binary "@app\build\outputs\apk\release\app-release.apk" `
  "https://uploads.github.com/repos/fraugz/FileManager/releases/$id/assets?name=app-release.apk"
```

Repeat with `release-assets\filemanager-vX.Y.Z.apk` and `name=filemanager-vX.Y.Z.apk`. Each call must
answer HTTP 201.

3. Check the uploaded assets against the build:

```powershell
gh release download vX.Y.Z --repo fraugz/FileManager --dir "$env:TEMP\fm-release-check" --clobber
Get-FileHash "$env:TEMP\fm-release-check\*.apk", "app\build\outputs\apk\release\app-release.apk"
```

There must be two downloaded APKs, named `app-release.apk` and `filemanager-vX.Y.Z.apk`, with the same
hash as the local build. Delete the temporary folder afterwards.

4. Publish and confirm:

```powershell
gh release edit vX.Y.Z --repo fraugz/FileManager --draft=false --latest
(gh api repos/fraugz/FileManager/releases/latest | ConvertFrom-Json).tag_name
curl.exe -s -o NUL -L -r 0-0 -w "%{http_code}" https://github.com/fraugz/FileManager/releases/download/vX.Y.Z/app-release.apk
```

`gh release edit` prints the release URL (`https://github.com/fraugz/FileManager/releases/tag/vX.Y.Z`),
the latest tag must be `vX.Y.Z`, and the download check must answer `200` or `206`.

---

## Checklist summary

- [ ] `gh` auth OK
- [ ] `versionCode` (formula, higher than the current one) and `versionName` bumped in `app/build.gradle`
- [ ] `changelog_content` updated in `values/strings.xml` (EN)
- [ ] `changelog_content` updated in `values-es/strings.xml` (ES)
- [ ] `README.md` changelog entry added
- [ ] `README.es.md` changelog entry added
- [ ] `DEVELOPMENT.md` changelog entry added
- [ ] `DEVELOPMENT.es.md` changelog entry added
- [ ] `LICENSE` year correct
- [ ] `/memories/repo/gestorarchivos3.md` updated (or reported as not available)
- [ ] `BUILD SUCCESSFUL`
- [ ] APK copied to `release-assets/filemanager-vX.Y.Z.apk` and `notes-vX.Y.Z.md` written
- [ ] Signing certificate identical to the previous release
- [ ] New APK installs over the previous release and starts
- [ ] Release commit pushed to `main`
- [ ] Draft release holds `app-release.apk` and `filemanager-vX.Y.Z.apk`, identical to the build
- [ ] Release published — URL, latest tag and download link confirmed
