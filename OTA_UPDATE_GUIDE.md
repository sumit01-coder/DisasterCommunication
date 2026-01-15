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

## 🚀 How to Release an Update (Step-by-Step)

Since we set up **GitHub Automation**, you do **NOT** need to build the APK manually. You just need to push a specific "Tag" from Android Studio.

### Step 1: Update Version
1.  Open Android Studio.
2.  Open the file: `app/build.gradle` (Module: app).
3.  Increment `versionCode` (integer) and `versionName` (text).
    ```gradle
    defaultConfig {
        versionCode 3      // Change 2 -> 3
        versionName "1.2"  // Change "1.1" -> "1.2"
    }
    ```
4.  Sync Gradle if asked.

### Step 2: Commit Changes
1.  Click the **Commit** tab (or checkmark icon) in Android Studio.
2.  Select `build.gradle` (and any other changed files).
3.  Enter a commit message: `Prepare release v1.2`.
4.  Click **Commit**.

### Step 3: Create & Push Tag (The Trigger)
This is the magic step that tells GitHub to build and release your app.

1.  Open the **Terminal** tab in Android Studio (bottom bar).
2.  Type these two commands:
    ```bash
    git tag v1.2
    git push origin master --tags
    ```
    *(Make sure the tag name "v1.2" matches your versionName)*

---

### 🎉 That's it!
Go to your **GitHub Repository > Actions** tab. You will see the release building automatically.
- It takes ~2-3 minutes.
- Once green ✅, your app users can go to **Settings > Software Update** to get the new version.

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

## Troubleshooting

### "Release not found (404)" Error
If the app says "Release not found", checks these common causes:
1.  **Repository Visibility**: ensure your GitHub repository is **Public**. This simple update mechanism relies on the public GitHub API.
    - Go to Repo Settings > Danger Zone > Change visibility > Make Public.
2.  **No Release Yet**: The GitHub Action might still be building. Check the "Actions" tab.
3.  **Release Draft**: Ensure the release is "Published", not a "Draft". The API ignores drafts.
