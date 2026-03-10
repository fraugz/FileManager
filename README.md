# FileManager

Android file manager application with a modern UI, trash support, recent files view, lightweight image previews, and bilingual localization (Spanish/English).

## Contents

- Features
- Tech stack
- Requirements
- Installation and run
- Configuration
- Project structure
- Versions and changelog
- Recommended Git workflow
- Recommended testing
- Default Apps Behavior
- Trash Behavior
- Common issues
- Roadmap
- Contributing

## Features

- Internal storage navigation with breadcrumb.
- Search with incremental loading and cancellation.
- Recent files view ordered by last access.
- Rename with extension control:
  - change name and extension
  - change name only
- Multi-selection actions:
  - copy
  - move
  - share
  - send to trash
- Trash with restore and permanent delete.
- Lightweight image thumbnails (async loading and cache).
- Appearance settings:
  - dark/light theme
  - UI scale
  - language (Spanish / English)
- Initial language automatically selected from device language (if no saved preference exists).
- Default app management for file opening:
  - list entries
  - change app per entry
  - remove single entry
  - clear all (with confirmation)

## Tech Stack

- Language: Java
- Platform: Android
- Build: Gradle (Android Application plugin)
- UI: AppCompat, RecyclerView, SwipeRefreshLayout
- Min SDK: 24
- Target SDK: 34
- Namespace / ApplicationId: `com.fraugz.filemanager`

## Requirements

- Android Studio (recent version recommended)
- JDK compatible with Android Gradle Plugin
- Android SDK 34 installed
- Android device or emulator (API 24+)

## Installation and Run

1. Clone the repository.
2. Open the project folder in Android Studio.
3. Wait for Gradle sync to finish.
4. Run the app on an emulator or physical device.

Note:
This repository does not include `gradlew` / `gradlew.bat`. Building from Android Studio is the expected flow.

## Configuration

### Language

Path: `Settings > Language`

- `Spanish`
- `English`

Initial behavior:

- If the user has not selected a language before, the app uses the device language.
- If device language is not Spanish, English is used by default.

### Theme and Scale

Path: `Settings`

- Theme: `Dark` / `Light`
- UI scale: `Small`, `Normal`, `Large`, `Extra large`

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

Current tags:

- `v1.0.0`: initial baseline
- `v1.1.0`: branding/package rename to `com.fraugz.filemanager`
- `v1.2.0`: bilingual localization (EN/ES) + language selector

## Recommended Git Workflow

Suggested flow for new work:

1. Create feature branch:
   - `git checkout -b feat/nombre-corto`
2. Create small, focused commits.
3. Push branch and open a PR.
4. Merge into `main`.
5. Add semantic tag (`v1.3.0`, `v1.3.1`, etc.) for each release.

## Recommended Testing

Minimum functional checks before publishing:

- Folder navigation and breadcrumb.
- Search behavior (start, cancel, results).
- File rename with and without extension changes.
- Recent view and access-order behavior.
- File opening and default app registration.
- Trash actions: move, restore, empty.
- Language changes in settings and persistence after restart.
- Theme and UI scale changes.

## Default Apps Behavior

The app stores "default app" entries based on file open handlers detected at runtime.

- When opening a file, FileManager tries to capture the resolved external app handler.
- System chooser/resolver activities are ignored.
- If there is no explicit default but only one valid external handler exists, FileManager stores that handler as the effective app.
- The list in `Settings > Default apps` is ordered by most recently detected app.

Per-entry management:

- Change app
- Delete one entry
- Clear all (with confirmation)

Notes:

- If Android displays a chooser and multiple handlers exist, no single app may be stored for that action.
- Behavior can vary slightly by Android version and OEM customizations.

## Trash Behavior

FileManager uses an app-scoped trash directory instead of immediate hard delete.

- Deleted items are moved to a hidden app trash location.
- Each trash entry stores metadata (original path, original name, deletion timestamp).
- Restore tries to move back to original location.
- If original path already exists, a unique sibling name is generated.
- Empty Trash permanently deletes all trash entries.
- A retention cleanup policy removes old entries automatically.

Error handling details:

- If direct move fails, fallback copy/delete logic is used where possible.
- Restore and move operations expose detailed failure reasons in UI toasts.

## Common Issues

- Storage permissions on Android 11+:
  - verify full storage access in system settings.
- Some file types do not open:
  - depends on external apps installed on the device.
- Git line ending warnings (LF/CRLF):
  - normal on Windows and usually harmless if build is unaffected.

## Roadmap

- Complete extraction of remaining internal messages into translatable resources.
- Add instrumented/UI tests.
- Improve accessibility (content descriptions and contrast).
- Publish automated release notes per tag.

## Contributing

If you want to contribute:

- open an issue with context and reproduction steps
- create a branch per feature/fix
- include screenshots for visual changes
- describe how you tested the change
