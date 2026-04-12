# GestorArchivos / FileManager

> A clean, lightweight Android file manager with trash support, rich previews, recent files, and bilingual UI.

[![Release](https://img.shields.io/github/v/release/fraugz/FileManager)](https://github.com/fraugz/FileManager/releases/latest)
[![Android](https://img.shields.io/badge/Android-7.0%2B-green)](https://github.com/fraugz/FileManager/releases/latest)
[![License](https://img.shields.io/github/license/fraugz/FileManager)](LICENSE)
[![Language](https://img.shields.io/badge/language-Java-orange)](https://github.com/fraugz/FileManager/search?l=java)

---

| Language | Document |
|----------|----------|
| English  | [README.md](README.md) |
| Español  | [README.es.md](README.es.md) |

---

## Screenshots

<p align="center">
  <img src="screenshots/1_storage_dark.jpg" width="160"/>
  <img src="screenshots/2_recents_dark.jpg" width="160"/>
  <img src="screenshots/3_multiselect_light.jpg" width="160"/>
  <img src="screenshots/4_trash_light.jpg" width="160"/>
  <img src="screenshots/6_breadcrumb_light.jpg" width="160"/>
</p>
<p align="center">
  <img src="screenshots/5_settings_dark_es.jpg" width="160"/>
  <img src="screenshots/5_settings_light_en.jpg" width="160"/>
</p>

---

## Features

- 📁 Folder navigation with breadcrumb and home shortcut
- 🕐 Recent files sorted by access date, with day separators and pin support
- ☑️ Multi-selection with copy, move, share, delete, and info actions
- 🗑️ Trash with restore and permanent delete
- 🖼️ Rich previews: image thumbnails, video frames, audio covers, APK icons
- 🔍 Incremental search with cancellation
- 📤 Share target: receive files from other apps and save to any folder
- 🎨 Appearance settings: dark/light theme, UI scale (4 sizes), language
- 🌐 Full bilingual support: English and Spanish
- 📱 Minimum Android 7.0 (API 24)

---

## Download

**[⬇️ Download latest APK](https://github.com/fraugz/FileManager/releases/latest)**

> F-Droid submission planned.

---

## Quick Start

New to FileManager? Check out the **[Quick Guide](QUICK_GUIDE.md)** for common tasks and tips.

---

## Build from source

### Android Studio (recommended)

1. Clone the repository.
2. Open the project in Android Studio.
3. Wait for Gradle sync to complete.
4. Run on a device or emulator (API 24+).

### Command line

> **Note:** This repository does not include Gradle wrapper (`gradlew`/`gradlew.bat`). Use a local Gradle installation or generate the wrapper files.

```bash
gradle :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

**Requirements:** Android Studio, JDK compatible with AGP, Android SDK 34.

---

## Feedback & Community

Found a bug or have an idea? There are three ways to reach out:

- 🐛 **Bug or crash** → [Open an Issue](https://github.com/fraugz/FileManager/issues) with steps to reproduce
- 💡 **Ideas or questions** → [Join the Discussions](https://github.com/fraugz/FileManager/discussions)
- 📬 **Direct contact** → [satin-speed-friday@duck.com](mailto:satin-speed-friday@duck.com)

All feedback is welcome — the project is actively maintained.

---

## Roadmap

- Migrate to Storage Access Framework for better Android 11+ compatibility
- Dual view mode: list and grid with persistent sorting
- Richer in-app previews (PDF, video, text)
- Favorites and smart collections (Downloads, Images, Documents)
- Instrumented UI tests for critical flows
- Improved accessibility: content descriptions, contrast, keyboard navigation

---

## Contributing

- Open an issue with context and reproduction steps
- One branch per feature or fix
- Small, focused commits
- Include screenshots for visual changes
- Describe how you validated changes before opening a PR

For technical details and development information, see **[DEVELOPMENT.md](DEVELOPMENT.md)**.

---

## License

Distributed under the MIT License. See [LICENSE](LICENSE) for details.

