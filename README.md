# FileManager

Android file manager with a modern UI, trash support, recent files view, lightweight thumbnails, and bilingual localization (Spanish/English).

## Language

| Language | Document |
| --- | --- |
| English | [README.md](README.md) |
| Espanol | [README.es.md](README.es.md) |

## Contents

- [Overview](#overview)
- [Core Features](#core-features)
- [Tech Stack](#tech-stack)
- [Requirements](#requirements)
- [Build and Run](#build-and-run)
- [Configuration](#configuration)
- [Application Behavior](#application-behavior)
- [Project Structure](#project-structure)
- [Versions and Changelog](#versions-and-changelog)
- [Recommended Testing](#recommended-testing)
- [Common Issues](#common-issues)
- [Roadmap](#roadmap)
- [Contributing](#contributing)

## Overview

FileManager lets users browse internal storage, search files, manage trash, and open files with external apps while remembering open preferences.

## Core Features

- Folder navigation with breadcrumb.
- Search with incremental loading and cancellation.
- Recent files view sorted by last access.
- When a file is moved to a new path, the previous path entry is removed from Recents.
- Horizontal swipe gestures to switch between Recents and Files views.
- Pull-down gesture in Files view to refresh the current directory.
- Recents quick actions:
  - clear all recents with confirmation
  - long press on a recent file for Set default app or Remove from recents
- Rename with extension control (full filename or name-only), without forced pre-selection in the input.
- Multi-selection actions: copy, move, share, and send to trash.
- Incoming share target: FileManager appears in Android share sheet and lets you navigate to a destination folder before saving shared files.
- Selection action bar includes a direct Set default app action when exactly one file is selected.
- Trash with restore and permanent delete.
- Rich previews with cache: image thumbnails, video frames, embedded audio covers, APK icons, and fallback to configured app icon for types without preview.
- Long filenames are shown in as many lines as needed in Files and Recents.
- Appearance settings: theme, UI scale, and language.
- Default app management for opening files.

## Tech Stack

- Language: Java
- Platform: Android
- Build: Gradle (Android Application plugin)
- UI: AppCompat, RecyclerView, SwipeRefreshLayout
- Min SDK: 24
- Target SDK: 34
- Namespace/ApplicationId: com.fraugz.filemanager

## Requirements

- Updated Android Studio
- JDK compatible with Android Gradle Plugin
- Android SDK 34 installed
- Android device or emulator (API 24+)

## Build and Run

### Option A: Android Studio (recommended)

1. Open the project in Android Studio.
2. Wait for Gradle sync to finish.
3. Build with Build > Make Project.
4. Run on an emulator or physical device.

### Option B: Command line

Note: this repository does not include gradlew/gradlew.bat. If you want to build from CLI, use a local Gradle installation.

```bash
gradle :app:assembleDebug
```

Expected debug APK:

- app/build/outputs/apk/debug/app-debug.apk

## Configuration

### Language

Path: Settings > Language

- Spanish
- English

Initial behavior:

- If no preference is stored, the device language is used.
- If the device is not in Spanish, fallback language is English.

### Theme and Scale

Path: Settings

- Theme: Dark / Light
- UI scale: Small, Normal, Large, Extra large

## Application Behavior

### Default Apps

- When opening a file, the app tries to store the resolved external handler.
- System chooser/resolver activities are ignored.
- If there is no explicit default but only one valid handler exists, it is stored as the effective app.
- The list in Settings > Default apps is ordered by latest detection.
- Default app rules are stored per extension (for example: .txt -> app package).
- Add extension and app flow now offers unresolved common extensions first, plus a custom extension option.
- App selection dialogs include search and app icons.
- The same searchable icon-based app picker is also available from Files/Recents when setting defaults directly.
- In selection/file menus, the action label is Open; it filters candidate user apps by file type, saves the chosen app as default for that extension, and immediately opens the file with that app.
- App visibility on Android 11+ uses manifest package queries for launcher and VIEW handlers.

Available actions per entry:

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
- Shared items are queued in-app and shown in the top action bar as Save here.
- You can navigate to any folder and save all pending shared files into the current directory.

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

## Versions and Changelog

Published tags:

- v1.0.0: initial baseline.
- v1.1.0: branding and package renamed to com.fraugz.filemanager.
- v1.2.0: bilingual localization (EN/ES) and language selector.
- v1.2.1: stability adjustments and minor improvements.
- v1.2.2: fixes in file-management flows.
- v1.2.3: incremental UX and robustness improvements.
- v1.2.4: recent trash/error handling updates and maintenance.
- v1.2.5: recents/selection UX refinements, direct set-default action from Files/Recents, default-app extension flow with searchable app picker, richer file previews (audio/video/APK + app icon fallback), APK install safety prompt, language icon update, and Android 11+ app visibility fixes.
- v1.2.6: playback robustness update (temporary M3U playlist with player-specific compatibility for VLC/AIMP/Total Commander), inline select-all and selection-state cleanup, one-shot paste behavior with dynamic Move/Paste button label, progress dialogs improved for move/delete/trash flows, real cancellation handling in long trash operations, direct permanent-delete option with extra warning confirmation and alert icon, trash delete progress UI, and incremental post-rename refresh optimization to reduce UI freezes on large folders.
- v1.2.7: fine UI/UX adjustments: top-right settings button now opens Settings directly (no intermediate menus), selection bottom bar labels constrained to one line to avoid wrapping on small screens, Set default app action renamed to Open and app picker now filters by file type while keeping only user apps, selecting an app both saves it as default and immediately opens the file, plus select-all icon refresh (empty square/checked square), tint consistency with new-folder icon, and pixel-level vertical alignment tuning.
- v1.2.8: smarter temporary playlist names for multi-file playback, FileManager added as Android share target with Save here import flow, and moved files are now removed from Recents at their previous path.
- v1.2.9: app name localized by system language (File Manager / Gestor Archivos), Open app picker expanded to a second step with an additional System apps button, Info action added to selection bar (single item, including folders), Rename hidden for multi-selection, plus new Quick Guide links (EN/ES) and direct project GitHub link in Settings.

Suggested next version:

- v1.3.0: Recents was redesigned to match Storage list/selection UX, then refined to Locate/Pin/Info actions (single select) and Pin/Info (multi-select), with Locate jumping to Storage and selecting the target file; includes visible pin badge, access-date ordering with day separators, clear-recents preserving pinned items, and a safeguard so auto-discovery does not repopulate old files after clearing; delete confirmation order was improved (Cancel, Delete permanently, Move to trash) with destructive emphasis; More apps now follows the same base filtering strategy as add-extension selection; storage header controls were compacted next to the title with a Home shortcut; breadcrumb now uses full width with side margins and wraps only when needed; plus shared-text save now asks for custom name/type before creating the file, horizontal swipe between Recents and Storage follows the finger smoothly, and the Open picker label was simplified to More apps.

## Recommended Testing

Minimum checklist before release:

- Folder navigation and breadcrumb.
- Search behavior (start, cancel, and results).
- Horizontal swipe gesture between Recents and Files tabs/views.
- Pull down in Files view refreshes the current directory.
- Rename with and without extension changes.
- Recents ordering by access time.
- Recents long press behavior (about half-second): Set default app and Remove from recents.
- Clear recents action and confirmation dialog.
- Selection action bar:
  - appears when selecting files/folders
  - Set default app appears only for single-file selection
- Rich preview behavior:
  - image/video/audio/APK previews when available
  - fallback icon from configured default app when no rich preview exists
- Very long filenames wrap into multiple lines without forced truncation.
- File opening and default app registration.
- Trash actions: move, restore, and empty.
- Language change and persistence after restart.
- Theme and UI scale changes.

## Common Issues

- Permissions on Android 11+: verify full file access in system settings.
- Some file types do not open: depends on external apps installed on the device.
- LF/CRLF warnings in Git: on Windows they are usually harmless if build is not affected.

## Roadmap

- Migrate file listing/operations to Storage Access Framework for better Android 11+ compatibility.
- Add instrumented UI tests for critical flows (trash, rename, multi-select).
- Add dual view mode (list/grid) with persistent sorting and filters.
- Improve accessibility: complete content descriptions, contrast, and keyboard/screen-reader navigation.
- Add richer previews for PDF/video/text without leaving the app.
- Implement favorites and smart collections (Recents, Downloads, Images, Documents).
- Generate automated release notes per tag from changelog history.

## Contributing

- Open an issue with context and reproduction steps.
- Create one branch per feature or fix.
- Make small and focused commits.
- Include screenshots for visual changes.
- Describe how you validated changes before opening a PR.
