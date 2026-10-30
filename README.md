[citra_project_readme.md](https://github.com/user-attachments/files/34177243/citra_project_readme.md)
# 🍊 Citra

![Citra Banner](https://via.placeholder.com/1200x400/FF6F00/FFFFFF?text=Citra+%E2%80%94+Gamified+Productivity+%26+Focus)

**Citra** is a feature-rich, gamified productivity mobile application built for Android. Designed around a warm, fruit-inspired aesthetic and modern Google Material Design guidelines, Citra brings together a highly flexible Pomodoro focus timer, task and habit management, interactive calendars, Markdown notes with rich editor capabilities, and cloud account synchronization.

---

## 🌟 Key Features

### ⏱️ Flexible Pomodoro Focus Timer
* **Dynamic Intervals:** Customizable focus cycles (10 to 120 minutes in 5-minute increments) paired with break controls.
* **Orange-Themed UI:** Timer visualizer styled after the interior geometry of a citrus fruit with vault-style selector controls.
* **Persistent Counting:** Background timer persistence that syncs automatically even if the app process is closed or cleared from memory.
* **Category Tagging:** Assign focus sessions to custom categories with dedicated emojis.

### 📋 Tasks & Habits Management
* **Fast Task Creation:** Streamlined flow requiring only essential details (Name + Date) with optional priorities and categories.
* **Habit Tracker & Streaks:** Daily/weekly habit tracking with streak visualization and flexible recurrence rules.
* **Robust Alarm Engine:** High-priority exact alarms (`SCHEDULE_EXACT_ALARM`) that persist across device restarts and battery optimization states.
* **Custom Notification Sounds:** Support for system defaults or custom audio file selection from local device storage.

### 📝 Smooth Markdown Notes Editor
* **WYSIWYG-style Markdown:** Visual-first editing that hides syntax markup (like `###` or `**`) while rendering rich formatting inline.
* **Rich Elements:** Headings (H1–H5), checklists, dynamic tables, callouts, toggles, math blocks ($2^3 \rightarrow 2^3$), and syntax-highlighted code blocks powered by **Prism.js**.
* **Media & Links:** In-line image/audio uploads, local note-linking (`[[Note Name|Alias]]`), and web hyperlinks.
* **Export Options:** Export notes to `.md`, `.pdf`, `.txt`, `.html`, or export as an image. Includes a 30-day recovery trash bin.

### 📅 Calendar & Visualizations
* **Unified Timeline:** Scrollable calendar view integrating tasks, habits, and notes on a single date grid.
* **Display Filters:** Toggle visibility for habits, tasks, or notes to keep the calendar clutter-free.

### 📊 Analytics & "Me" Ecosystem
* **Cloud Sync & Identity:** Google Sign-In integration (profile photo and username sync) backed by Firebase.
* **Social & Privacy:** Public/Private profile modes, friend connections via 9-character ID or QR Code, and leaderboards.
* **Detailed Reports:** Daily/weekly focus time, habit completion histories, category distributions, and period comparisons.

---

## 🛠️ Tech Stack & Architecture

* **Framework:** React Native (TypeScript)
* **Target Platform:** Android (Native execution)
* **Backend & Cloud:** Firebase (Authentication, Firestore, Storage)
* **Design System:** Material-inspired clean UI (Themeable: Light/Dark modes, custom accent palettes)
* **Code Highlighting:** Prism.js
* **Development Methodology:** AI-assisted development via **Google AI Studio** (*Vibe Code* process)

---

## 📂 Data & Backup Management

* **Local Storage First:** Offline-first architecture ensures full app functionality without an active internet connection.
* **JSON Backups:** Export and import full app state through standard `.json` data files.
* **Cloud Sync:** Firebase Firestore handles user preferences, notes, habits, tasks, and historical analytics when logged in.

---

## 👤 Author & Credits

* **Developer:** Márcio Rey
* **Email:** marcioreyakm@gmail.com

---

*Copyright © Márcio Rey. All rights reserved.*
