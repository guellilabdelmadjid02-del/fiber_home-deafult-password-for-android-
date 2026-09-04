# FiberHome Default Password Scanner 🚀
**Developed by GUELLIL**

A complete and modern Android application built with **Kotlin** and **Android NDK (C/C++)** designed to scan nearby Wi-Fi networks and generate default passwords for FiberHome routers.

---

## 📌 Overview
This application leverages a custom C algorithm (integrated via JNI) to reverse-engineer default Wi-Fi passwords for routers typically labeled with SSIDs starting with `FH_` (commonly used by Idoom Fibre and FiberHome).

### 🌟 Key Features
- **Wi-Fi Scanner**: Automatically discovers nearby networks using Android's `WifiManager`.
- **Smart Filtering**: Specifically identifies FiberHome routers with a green highlight and a checkmark badge (✔️).
- **Native Password Generation**: High-performance C logic handles the character mapping to generate passwords starting with `wlan`.
- **Cyberpunk UI**: A professional, futuristic Dark Mode design with glassmorphism cards and glowing accents.
- **Sticky Active-Network Banner**: A persistent banner at the top showing the current target SSID and MAC address.
- **Smart Notifications**: Heads-up reminder with the target SSID that auto-dismisses after 30 seconds.
- **Anti-Throttling**: Built-in logic to handle Android's Wi-Fi scan throttling (gracefully shows cached results).
- **Instant Redirection**: Automatically opens Android Wi-Fi settings after copying the password.

---

## 🆕 What's New in Version 2.0
- **UI Overhaul**: Complete transition to a high-end Cyberpunk / Futuristic aesthetic.
- **Sticky Header**: Added an `activeBanner` to keep track of the selected network while scrolling.
- **Workflow Automation**: Integrated an intent to jump directly to Wi-Fi settings upon copying.
- **Smart Reminders**: Added high-priority notifications to show the SSID while you are in the settings app.
- **Radar Visuals**: Added a subtle, animated Wi-Fi radar to indicate active scanning.
- **Enhanced Reliability**: Fixed scan failure bugs related to Android system throttling.

---

## 🛠️ Technology Stack
- **Kotlin**: Primary language for the Android application.
- **Android NDK (JNI)**: Bridges the high-level Kotlin code with performance-critical C logic.
- **CMake**: Build system for the native C libraries.
- **Material 3**: For a sleek, modern, and adaptive user interface.
- **Gradle 9.7.1**: Optimized build configuration for modern environments.

---

## 🗂️ Project Structure
- `app/src/main/java`: Kotlin source code (Scanner logic, UI Adapters).
- `app/src/main/cpp`: Native C code (`lib.c`, `native-lib.cpp`) and `CMakeLists.txt`.
- `app/src/main/res`: XML Layouts and adaptive icons.
- `gradle/`: Gradle wrapper files.
- `README.md`: Project documentation.

---

## ⚙️ How to Build
1.  **Clone the repository**:
    ```bash
    git clone https://github.com/your-username/fiberhome-password-scanner.git
    ```
2.  **Open in Android Studio**:
    - Ensure you have the **Android NDK** and **CMake** installed.
    - Android Studio will automatically sync the Gradle files.
3.  **Setup local.properties**:
    - Ensure `sdk.dir` points to your Android SDK path.
4.  **Run**:
    - Connect a physical Android device (Wi-Fi scanning is not supported on emulators).
    - Press the **Run** button (▶).

---

## ⚖️ Disclaimer
*This tool is intended for educational purposes and for network administrators to test the security of their own devices. Unauthorized access to Wi-Fi networks is illegal.*

---

## 👤 Credits
Developed with ❤️ by **GUELLIL**.
