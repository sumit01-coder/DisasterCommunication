# 📲 Installation and Update Guide

This document explains how to install the Disaster Communication app and how the automatic update system works.

---

## 📥 Installation Guide

Since this application is designed for emergency use and private distribution, it is installed via **APK Sideloading** rather than the Google Play Store.

### 1. Enable Installation from Unknown Sources
Before installing, you must allow your phone to install apps from sources other than the Play Store.

*   **Android 8.0 (Oreo) and newer:**
    1.  Download the APK file.
    2.  Tap to open it.
    3.  A prompt will appear: "For security, your phone is not allowed to install unknown apps from this source."
    4.  Tap **Settings**.
    5.  Toggle **Allow from this source** to **ON**.
    6.  Go back and tap **Install**.

*   **Android 7.0 and older:**
    1.  Go to **Settings** > **Security**.
    2.  Check **Unknown Sources**.
    3.  Tap **OK** on the warning prompt.

### 2. Install the APK
1.  Download the latest `app-release.apk` (or `app-debug.apk`) from the **GitHub Releases** page or receive it via Bluetooth/Nearby Share.
2.  Tap the file in your Notification shade or File Manager.
3.  Tap **Install**.
4.  Once finished, tap **Open**.

### 3. Initial Setup
On first launch:
1.  **Enter a Username** (e.g., "Rescue Team 1").
2.  **Grant Permissions**:
    *   **Location**: Required for Wi-Fi Direct (Mesh Networking) and GPS.
    *   **Bluetooth/Nearby Devices**: Required for device discovery.
    *   **Notifications**: Required for background service alerts.

---

## 🔄 Updating the Application

The app includes a built-in **Update Manager** that checks for new versions automatically.

### Feature: Automatic Background Updates
*   **How it works**: The app periodically checks the GitHub repository for new releases in the background (using `WorkManager`).
*   **Frequency**: Checks occur roughly every 12-24 hours when connected to the internet.
*   **Notification**: If a new version is found, you will see a notification: *"New Update Available! Tap to download."*
*   **Action**: Tapping the notification will download the new APK and prompt you to install it.

### Feature: Manual Update Check
You can force a check for updates at any time:
1.  Open the App.
2.  Tap the **Navigation Drawer** (hamburger menu ☰) or **Menu** (⋮).
3.  Tap **About**.
4.  The "About" dialog will show your current version and say *"Checking..."*.
5.  If an update is available, it will show *"Update Available!"*.

---

## 🛠️ For Developers: Releasing an Update

To push a new update to all users, follow this workflow:

1.  **Commit Changes**: Ensure your code is committed to `main` branch.
2.  **Update Version**:
    *   Open `app/build.gradle`.
    *   Increment `versionCode` (e.g., `10` -> `11`).
    *   Increment `versionName` (e.g., `"1.0"` -> `"1.1"`).
3.  **Push to GitHub**:
    *   Pushing to `main` (or creating a specific tag) will trigger the **GitHub Actions Workflow**.
4.  **CI/CD Pipeline**:
    *   GitHub Actions compiles the APK (`assembleRelease`).
    *   It signs the APK using the stored keystore secrets.
    *   It creates a **GitHub Release** and uploads the APK assets.
5.  **User Detection**:
    *   User apps will detect the new GitHub Release JSON within a few hours and verify the higher `versionCode`.
    *   They will then notify the user to update.

### Important Note on Signatures
*   The `UpdateManager` may block updates if the **Signing Key** changes.
*   Ensure you always use the same Release Keystore for signing subsequent updates, otherwise Android will refuse to update the existing app (claiming "App not installed" or "Signature mismatch").
