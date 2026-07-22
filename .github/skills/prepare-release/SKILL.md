---
name: prepare-release
description: "Prepares and publishes a new release of the FileManager Android app. Use when: creating a release, bumping version, publishing APK, updating changelog, releasing new version. Covers version bump in build.gradle, all README/DEVELOPMENT changelog updates, in-app strings, LICENSE year, memory file cleanup, APK build, and gh release publish."
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
- `versionCode` = numeric from digits, e.g. `1.3.5` → `135`

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

## Step 8 — Copy APK to release-assets

```powershell
Copy-Item "app\build\outputs\apk\release\app-release.apk" `
          "release-assets\filemanager-vX.Y.Z.apk" -Force
```

Verify:
```powershell
Get-Item "release-assets\filemanager-vX.Y.Z.apk" | Select-Object Name, Length
```

---

## Step 9 — Publish GitHub release

> ⚠️ **IMPORTANT**: `UpdateChecker` downloads `app-release.apk` from the release assets.
> The release **must** include an asset named exactly `app-release.apk` in addition to the
> human-readable `filemanager-vX.Y.Z.apk`. Use `--clobber` so re-runs are safe.

```powershell
gh release create vX.Y.Z `
  "release-assets\filemanager-vX.Y.Z.apk" `
  "release-assets\filemanager-vX.Y.Z.apk#app-release.apk" `
  --title "vX.Y.Z" `
  --notes-file "release-assets\notes-vX.Y.Z.md"
```

The command prints the release URL on success:
```
https://github.com/fraugz/FileManager/releases/tag/vX.Y.Z
```

---

## Checklist summary

- [ ] `gh` auth OK
- [ ] `versionCode` and `versionName` bumped in `app/build.gradle`
- [ ] `changelog_content` updated in `values/strings.xml` (EN)
- [ ] `changelog_content` updated in `values-es/strings.xml` (ES)
- [ ] `README.md` changelog entry added
- [ ] `README.es.md` changelog entry added
- [ ] `DEVELOPMENT.md` changelog entry added
- [ ] `DEVELOPMENT.es.md` changelog entry added
- [ ] `LICENSE` year correct
- [ ] `/memories/repo/gestorarchivos3.md` updated
- [ ] `BUILD SUCCESSFUL`
- [ ] APK copied to `release-assets/filemanager-vX.Y.Z.apk`
- [ ] `gh release create` — URL confirmed
