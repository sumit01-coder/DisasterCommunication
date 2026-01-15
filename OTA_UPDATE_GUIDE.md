# OTA Update System Guide

This guide explains how the **Over-The-Air (OTA) Update System** works in the Disaster Communication App. It allows users to download and install the latest version of the app directly from your GitHub repository.

## 🛠 How It Works

1.  **Check for Updates**: 
    - When the user taps **"Check for Update"** in *Settings*, the app sends a request to the GitHub API:
      `https://api.github.com/repos/{OWNER}/{REPO}/releases/latest`
    
2.  **Version Comparison**:
    - The app compares the **Tag Name** of the latest GitHub release (e.g., `v1.2.0`) with the installed app's `versionName` (e.g., `1.1.0`).
    - If the GitHub tag is different, it prompts the user to update.

3.  **Download**:
    - If the user accepts, the APK file from the release assets is downloaded to the device's internal storage using `HttpURLConnection`.

4.  **Secure Installation**:
    - Once downloaded, the app uses `FileProvider` to securely trigger the Android Package Installer.
    - The user is prompted to allow permission for the install, and the update is applied.

---

## ⚙️ Configuration (Required)

Before this works, you **MUST** configure your repository details in the code.

1.  **Open File**: `app/src/main/java/com/example/disastercomm/utils/UpdateManager.java` (Line 29)
2.  **Edit Variables**:
    ```java
    private static final String GITHUB_OWNER = "YOUR_USERNAME"; // e.g., "SumitCoder"
    private static final String GITHUB_REPO = "DisasterApp";    // e.g., "Disaster-Comm-App"
    ```

---

## 🚀 How to Release an Update

To make an update available to your users:

1.  **Update Version**:
    - In `app/build.gradle`, increment the `versionCode` and `versionName`:
      ```gradle
      defaultConfig {
          versionCode 2
          versionName "1.1.0"
      }
      ```
2.  **Build Signed APK**:
    - Go to **Build > Generate Signed Bundle / APK > APK**.
    - Create a release build.

3.  **Create GitHub Release**:
    - Go to your GitHub Repository > **Releases** > **Draft a new release**.
    - **Tag version**: Must match your `versionName` (e.g., `v1.1.0`).
    - **Title**: "Release v1.1.0".
    - **Attach Binaries**: Upload the signed `.apk` file you built.
    - Click **Publish release**.

---

## 🔒 Permissions

The system uses the following permissions (already added to `AndroidManifest.xml`):

- **INTERNET**: To check GitHub API.
- **REQUEST_INSTALL_PACKAGES**: To trigger the APK installation.
- **FileProvider**: To securely share the downloaded APK with the system installer.

---

## 🧪 Testing

1.  Create a "dummy" release on GitHub with a higher version tag (e.g., `v9.9.9`).
2.  Run the app (with a lower version).
3.  Go to **Settings > Check for Update**.
4.  Verify the dialog appears and the download works.
