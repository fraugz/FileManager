# Development Guide

This document contains technical information for developers working on FileManager.

## Contents

- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Application Behavior](#application-behavior)
- [Configuration](#configuration)
- [Versions and Changelog](#versions-and-changelog)
- [Recommended Testing](#recommended-testing)
- [Common Issues](#common-issues)

---

## Tech Stack

- **Language:** Java
- **Platform:** Android
- **Build:** Gradle (Android Application plugin)
- **UI:** AppCompat, RecyclerView, SwipeRefreshLayout
- **Min SDK:** 30 (Android 11)
- **Target SDK:** 34
- **Namespace/ApplicationId:** com.fraugz.filemanager

---

## Project Structure

```text
app/
  src/main/
    java/com/fraugz/filemanager/
      MainActivity.java
      SettingsActivity.java
      TrashActivity.java
      FileAdapter.java
      RecentAdapter.java
      ThemeManager.java
      LocaleManager.java
      TrashManager.java
      RecentManager.java
      DefaultAppsManager.java
      ...
    res/
      layout/
      drawable/
      values/       # English (base)
      values-es/    # Spanish
```

---

## Application Behavior

### Fast Scrolling

- Storage, Recents, and Trash share `FastScrollRecyclerView`.
- Scrollable lists display a slim pill thumb on the right: 6 x 48 dp (8 dp wide and accent-coloured while held), with a 48 dp wide touch target. It grows with larger UI scales.
- While the thumb is dragged, a teardrop-shaped bubble points at it from the left, set back from the edge so the finger does not cover the text. It names the row being passed, using whatever the list is ordered by: the initial (sorted by name), the date (sorted by date, and always in Recents), the file size (sorted by size) or the deletion date (Trash). Adapters supply the text through `FastScrollRecyclerView.LabelProvider`.
- Sorted by name, an initial is useless when many files start the same way (`IMG_…`, `Screenshot_…`, `20261114-…`). When more than 30 consecutive rows share a start, `NameLabels` skips it and labels them by what changes next: the date written in the name (`yyyyMMdd` or `yyyy-MM-dd`, shown like in Recents) or, failing that, the first differing characters. A text label always starts with the name's own beginning, so it never looks like another initial: the whole start when it is short (`La ciudad` → `LA C`), otherwise the initial, an ellipsis and what changes (`Factura_Enero` → `F…E`, `informe_137` → `I…13`). Shorter runs keep the initial.
- Dragging jumps through the list without laying out every intermediate item. The handle is hidden for empty lists and lists that fit on screen.
- Handle drags suppress pull-to-refresh and horizontal tab switching; normal gestures and selection remain available outside the handle.

### Default Apps

- When opening a file, the app tries to store the resolved external handler.
- System chooser/resolver activities are ignored.
- If there is no explicit default but only one valid handler exists, it is stored as the effective app.
- The list in Settings > Default apps is ordered by latest detection.
- Default app rules are stored per extension (for example: `.txt` → app package).
- Add extension and app flow now offers unresolved common extensions first, plus a custom extension option.
- App selection dialogs include search and app icons.
- The same searchable icon-based app picker is also available from Files/Recents when setting defaults directly.
- In selection/file menus, the action label is **Open**; it filters candidate user apps by file type, saves the chosen app as default for that extension, and immediately opens the file with that app.
- App visibility on Android 11+ uses manifest package queries for launcher and VIEW handlers.

**Available actions per entry:**
- Change app
- Delete entry
- Clear all (with confirmation)

### Trash

- Deleted items are moved to an internal app trash location.
- Each entry stores metadata: original path, original name, and deletion timestamp.
- Restore tries to move back to origin and generates a unique name if there is a conflict.
- Empty trash permanently deletes all entries.
- A retention policy cleans old entries automatically.
- If direct move fails, a copy/delete fallback is used when possible.

### Share Sheet Import

- FileManager is available as a target in Android share sheet for single and multiple files.
- Shared items are queued in-app and shown in the top action bar as **Save here**.
- You can navigate to any folder and save all pending shared files into the current directory.

### Recent Files Behavior

- Recent files view sorted by last access.
- When a file is moved to a new path, the previous path entry is removed from Recents.
- Horizontal swipe gestures to switch between Recents and Files views.
- Pull-down gesture in Files view to refresh the current directory.
- Recents quick actions:
  - Clear all recents with confirmation
  - Long press on a recent file for **Set default app** or **Remove from recents**
- Pinned items always stay at the top, in their own "Pinned" section. They do not count towards the limit of 50 recent items, so recent files remain below however many items are pinned.
- Day separators for better organization.
- Clear recents preserves pinned items.

### File Operations

- **Rename** with extension control (full filename or name-only), without forced pre-selection in the input.
- **Multi-selection actions:** copy, move, share, and send to trash.
- Selection action bar includes a direct **Set default app** action when exactly one file is selected.
- **Rich previews** with cache: image thumbnails, video frames, embedded audio covers, APK icons, and fallback to configured app icon for types without preview.
- Long filenames are shown in as many lines as needed in Files and Recents.

### Multi-Selection — Available Actions

Visible actions in the bottom bar depend on the active tab and what is selected.

**Storage**

| Selection | Send | Open | Play | Pin/Unpin | Move | Copy | Delete | Rename | Info |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 file | ✓ | ✓ | — | — | ✓ | ✓ | ✓ | ✓ | ✓ |
| 1 folder | — | — | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| N files (all media) | ✓ | — | ✓ | — | ✓ | ✓ | ✓ | — | — |
| N files | ✓ | — | — | — | ✓ | ✓ | ✓ | — | — |
| N folders | — | — | — | ✓ | ✓ | ✓ | ✓ | — | — |
| Mixed files and folders | ✓ | — | — | — | ✓ | ✓ | ✓ | — | — |

**Recents**

| Selection | Send | Play | Locate | Move | Copy | Delete | Rename | Pin/Unpin |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 file | ✓ | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| 1 folder | — | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| N files (all media) | ✓ | ✓ | — | ✓ | ✓ | ✓ | — | ✓ |
| N files | ✓ | — | — | ✓ | ✓ | ✓ | — | ✓ |
| N folders | — | — | — | ✓ | ✓ | ✓ | — | ✓ |

**Trash**

| Selection | Restore | Info | Delete forever |
|---|:---:|:---:|:---:|
| 1 item | ✓ | ✓ | ✓ |
| N items | ✓ | — | ✓ |

---

## Configuration

### Language

**Path:** Settings > Language

**Options:**
- Spanish
- English

**Initial behavior:**
- If no preference is stored, the device language is used.
- If the device is not in Spanish, fallback language is English.

### Theme and Scale

**Path:** Settings

**Options:**
- **Theme:** Dark / Light
- **UI scale:** Small, Normal, Large, Extra large

---

## Versions and Changelog

**Published tags:**

- **v1.0.0:** Initial baseline.
- **v1.1.0:** Branding and package renamed to `com.fraugz.filemanager`.
- **v1.2.0:** Bilingual localization (EN/ES) and language selector.
- **v1.2.1:** Stability adjustments and minor improvements.
- **v1.2.2:** Fixes in file-management flows.
- **v1.2.3:** Incremental UX and robustness improvements.
- **v1.2.4:** Recent trash/error handling updates and maintenance.
- **v1.2.5:** Recents/selection UX refinements, direct set-default action from Files/Recents, default-app extension flow with searchable app picker, richer file previews (audio/video/APK + app icon fallback), APK install safety prompt, language icon update, and Android 11+ app visibility fixes.
- **v1.2.6:** Playback robustness update (temporary M3U playlist with player-specific compatibility for VLC/AIMP/Total Commander), inline select-all and selection-state cleanup, one-shot paste behavior with dynamic Move/Paste button label, progress dialogs improved for move/delete/trash flows, real cancellation handling in long trash operations, direct permanent-delete option with extra warning confirmation and alert icon, trash delete progress UI, and incremental post-rename refresh optimization to reduce UI freezes on large folders.
- **v1.2.7:** Fine UI/UX adjustments: top-right settings button now opens Settings directly (no intermediate menus), selection bottom bar labels constrained to one line to avoid wrapping on small screens, Set default app action renamed to Open and app picker now filters by file type while keeping only user apps, selecting an app both saves it as default and immediately opens the file, plus select-all icon refresh (empty square/checked square), tint consistency with new-folder icon, and pixel-level vertical alignment tuning.
- **v1.2.8:** Smarter temporary playlist names for multi-file playback, FileManager added as Android share target with Save here import flow, and moved files are now removed from Recents at their previous path.
- **v1.2.9:** App name localized by system language (File Manager / Gestor Archivos), Open app picker expanded to a second step with an additional System apps button, Info action added to selection bar (single item, including folders), Rename hidden for multi-selection, plus new Quick Guide links (EN/ES) and direct project GitHub link in Settings.
- **v1.3.0:** Recents was redesigned to match Storage list/selection UX, then refined to Locate/Pin/Info actions (single select) and Pin/Remove-from-recents actions on multi-select (Info hidden for 2+ items), with direct remove-from-recents support for one or multiple selected items, with Locate jumping to Storage and selecting the target file; includes visible pin badge, access-date ordering with day separators, clear-recents preserving pinned items, and a safeguard so auto-discovery does not repopulate old files after clearing; delete confirmation order was improved (Cancel, Delete permanently, Move to trash) with destructive emphasis; More apps now follows the same base filtering strategy as add-extension selection; storage header controls were compacted next to the title with a Home shortcut; breadcrumb now uses full width with side margins and wraps only when needed; Send from Storage now also supports folder selection and opens compatible apps from chooser; plus shared-text save now asks for custom name/type before creating the file, horizontal swipe between Recents and Storage follows the finger smoothly, and the Open picker label was simplified to More apps.
- **v1.3.2:** Critical fix: files now move to app trash instead of being deleted directly. Fixed multi-selection also deleting permanently. Multi-selection in Trash screen (long-press to select, tap to toggle, batch Restore/Delete actions). Trash thumbnails now use original filename for media type detection. Visited folders now appear in Recents sorted by last access.
- **v1.3.3:** System trash now detected via MediaStore query (same approach as Google Photos), fixing items missing from the Trash screen. Folder item counts now include both system and app trash. Only pinned folders appear in Recents automatically; non-pinned folders are no longer added on scan. Trash icon visible in both Storage and Recents tabs. New once-a-day update checker dialog.
- **v1.3.4:** App registered as ACTION_VIEW handler — appears in "Open with…" from any app; the opened file is added to Recents and the browser navigates to its parent folder. App registered as ACTION_GET_CONTENT handler — appears as a file picker when other apps request an attachment or upload (messaging, email, cloud storage, etc.); selecting a file returns its URI to the caller via setResult and closes the app. Recents auto-discovery and MediaStore background scan removed (deprecated); only manually opened or pinned items appear. Re-opening a file already in Recents updates its access timestamp without changing its position (new RecentManager.touch()).- **v1.3.5:** Recents auto-discovery restored via a single `MediaStore.Files` query — no hardcoded paths; covers images, video, audio, documents (MEDIA_TYPE_DOCUMENT), and MEDIA_TYPE_NONE files with a known-extension whitelist (zip, apk, pdf, docx, epub, etc.). `DATE_ADDED` from MediaStore used as the arrival timestamp instead of `File.lastModified()`, fixing WhatsApp/Telegram photos with historic mtimes. Pull-to-refresh gesture in Recents forces an immediate scan (bypasses 5-minute throttle). Returning to Recents after ≥30 seconds also triggers a forced scan. Fixed `LruCache` size (was 20 KB hard cap — smaller than one thumbnail; now 1/8 of heap). Fixed discovery-loop bug (scan re-triggered on every `loadRecentFiles`, causing `notifyDataSetChanged` cycles and multi-tap to open). Fixed pull-to-refresh on empty folders and empty search results (`SwipeRefreshLayout` was set to `GONE`).- **v1.3.1:** Trash screen UX overhaul: thumbnail previews for image/video/audio/APK items; click opens file with the preferred app (honouring DefaultAppsManager, same as main browser); long-press shows a bottom action bar (Restore / Info / Delete) with icons matching the main selection bar; both app-trash and system-trash sections are always visible with an empty-state placeholder when each is empty; section headers carry a ? info badge (theme-aware); Empty trash covers both sections with a combined count confirmation dialog; folder item counts now exclude hidden files and flag trashed items separately; fixed image opening via MediaStore URI so gallery apps can swipe between siblings; auto-delete countdown shown in item rows and Info dialog; Info dialog also shows the original file path; typo fix in Spanish "Tamaño total en papelera".
- **v1.3.10:** Recents: the list order comes only from `RecentManager` (pinned first, then most recent first; `MainActivity` no longer re-sorts by access time), pinned rows share a single "Pinned" header, and `RecentManager.trim()` keeps every pinned entry plus the 50 newest unpinned ones instead of 50 entries in total. `NameLabels`: a text label always starts with the name's own beginning (`LA C` for a short shared start, `F…E` for a long one) instead of a bare character from the middle of the name. `versionCode` is now 1310; later versions must use a higher number (for example 1400 for 1.4.0).
- **v1.3.9:** Fast scrolling: `FastScrollRecyclerView` (Storage, Recents, Trash) draws a slim draggable thumb and, while dragged, a bubble supplied by the adapter through `LabelProvider`; `NameLabels` skips the shared start of long runs of similar names and labels them by the date in the name or the first differing characters. `FileOperations.rename()` refuses to replace a different existing item. "Save as" no longer truncates confirmed names to 80 characters, and the clipboard is consumed only when the paste is confirmed. `onNewIntent()` handles folder shortcuts. `UpdateChecker`: download receiver registered as `RECEIVER_EXPORTED` on the application context (the completion broadcast comes from the system download provider, so a non-exported receiver never gets it), failed checks reported on manual checks, dismissed versions offered again on manual checks, no dialog on a finished activity. Cancelled or failed copies remove their partial destination. Single-item delete and trash run on the background path. Thumbnail caches in `TrashActivity` and `RecentAdapter` sized to 1/8 of the heap. Instrumented tests added under `app/src/androidTest`.
- **v1.3.8:** Fix: `DownloadManager` `BroadcastReceiver` registered without `RECEIVER_EXPORTED` flag caused the auto-install to silently fail on Android 13+ (API 33+, `targetSdkVersion >= 33`). Fixed by passing `Context.RECEIVER_EXPORTED` when `Build.VERSION.SDK_INT >= 33`, falling back to flag `0` on older versions.
- **v1.3.7:** When saving a single shared file or pasting a single file from the clipboard (copy or move), a "Save as" dialog now lets the user confirm or edit the destination filename before the operation runs. The base name (before the extension) is pre-selected so the user can type a replacement immediately. The rename dialog is shown via the new `showRenameBeforeSaveDialog` helper; the shared-items import path was refactored into `doImportSharedItems()` and the clipboard paste path into `doClipboardPaste()` to keep each entry point small. Multiple-file operations are unaffected.
- **v1.3.6:** App removed from the system "Open with…" picker for arbitrary file types; only ACTION_SEND, ACTION_SEND_MULTIPLE and ACTION_GET_CONTENT intents remain registered. Selection count label (`tv_selection_count`) added above the action bar, showing the number of selected items with the same background colour as the bar to avoid a visible line. "Select all" (`btn_select_all_recent`) and "Select by day" (`btn_select_day_recent`) buttons added to the Recent tab header; select-all now toggles — a second tap calls `exitSelectionMode()` and the button switches to a check icon when all items are selected (mirrors the Storage tab behaviour). Folder long-press context menu includes a new "Add shortcut to home screen" option via `ShortcutManagerCompat.requestPinShortcut`. Update dialog redesigned with three buttons: Download APK (positive), Remind me later (neutral), Dismiss update (negative — stores dismissed version in SharedPreferences). APK update download replaced with `DownloadManager` for a direct in-app download; a one-shot `BroadcastReceiver` listens for `ACTION_DOWNLOAD_COMPLETE` and launches the system installer automatically.

---

## Recommended Testing

**Minimum checklist before release:**

- ✅ Folder navigation and breadcrumb
- ✅ Search behavior (start, cancel, and results)
- ✅ Horizontal swipe gesture between Recents and Files tabs/views
- ✅ Pull down in Files view refreshes the current directory
- ✅ Rename with and without extension changes
- ✅ Recents ordering by access time
- ✅ Recents long press behavior (about half-second): Set default app and Remove from recents
- ✅ Clear recents action and confirmation dialog
- ✅ Selection action bar:
  - Appears when selecting files/folders
  - Set default app appears only for single-file selection
- ✅ Rich preview behavior:
  - Image/video/audio/APK previews when available
  - Fallback icon from configured default app when no rich preview exists
- ✅ Very long filenames wrap into multiple lines without forced truncation
- ✅ File opening and default app registration
- ✅ Trash actions: move, restore, and empty
- ✅ Language change and persistence after restart
- ✅ Theme and UI scale changes

---

## Common Issues

### Permissions on Android 11+

Verify full file access in system settings if the app cannot access certain directories.

### File Types Not Opening

Some file types may not open depending on which external apps are installed on the device.

### LF/CRLF Warnings in Git

On Windows, these warnings are usually harmless if the build is not affected. Configure Git line endings if needed:

```bash
git config core.autocrlf true
```

---

## Build Notes

### Gradle Wrapper

The repository includes the Gradle wrapper (Gradle 8.7), so no local Gradle installation is needed. On Windows use `.\gradlew.bat` instead of `./gradlew`.

### Build Commands

**Debug APK:**
```bash
./gradlew :app:assembleDebug
```

**Release APK** (signed with the `FM_*` keystore if configured, otherwise with the local debug keystore):
```bash
./gradlew :app:assembleRelease
```

**Output location:**
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk` (`app-release-unsigned.apk` when no keystore is available)

---

## Contributing Workflow

1. **Fork** the repository
2. **Create a branch** from `main`: `git checkout -b feature/my-feature`
3. **Make changes** with focused commits
4. **Test thoroughly** using the checklist above
5. **Push** to your fork
6. **Open a Pull Request** with:
   - Clear description of changes
   - Screenshots for UI changes
   - Testing performed
   - Related issue numbers (if any)

---

## Code Style

- Use consistent Java formatting (Android Studio default)
- Meaningful variable and method names
- Comments for complex logic
- Keep methods focused and reasonably sized
- Follow Android best practices for Activities, Fragments, and Adapters

---

## Resources

- [Android Developer Documentation](https://developer.android.com/)
- [Material Design Guidelines](https://material.io/design)
- [Gradle Build Tool](https://gradle.org/)
