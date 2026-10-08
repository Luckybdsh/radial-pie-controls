# 🍰 Pie Controls

A premium, highly customizable edge-bar navigation assistant for Android. Pie Controls replaces standard navigation buttons with a fluid, hidden semicircle menu that floats seamlessly over your system interface. 

Built natively in Kotlin using `WindowManager` and `AccessibilityService` APIs, it delivers a smooth, zero-latency navigation experience designed for one-handed use on large displays.

##📱screenshots 

![image alt](https://github.com/Luckybdsh/radial-pie-controls/blob/58fbcef569acfa0488fe4d6e99be0f254426d339/app/assets/Screenshot_20261008-114603_Chrome.png)

## ✨ Key Features

* **Interactive Drag & Drop:** Reorder your navigation tiles in real-time using a custom-built physics semicircle preview right on the home screen.
* **Live UI Engine:** Settings adjustments (sliders, shapes, themes) instantly redraw the edge bar in real-time without needing to restart the service.
* **Liquid Glass Rendering:** Utilizes Android's `FLAG_BLUR_BEHIND` for true, hardware-accelerated iOS-style frosted glass blur natively in the OS.
* **Custom Tile Geometry:** Switch between Circular, Square, Rounded, Fur, and precise outward-pointing Pie Slices.
* **Vibrant Themes:** Choose from Flat Simple, Glowing Neon, and dynamically colored Vibrant Slices.
* **Tiered Unlock System:** Built-in passcode gateway to unlock Pro and Master tiers (Custom App Shortcuts, Local JSON Backups, Custom Positioning).
* **JSON State Management:** Export and import your exact configurations, themes, and tile layouts to local storage.

## 🛠 Tech Stack

* **Language:** Kotlin (100% Native)
* **UI/UX:** Custom Canvas Drawing, `ValueAnimator` physics, `GradientDrawable` shape morphing
* **System APIs:** `AccessibilityService`, `WindowManager` (System Alert Window), `SharedPreferences`
* **CI/CD:** Automated APK compilation via GitHub Actions

## ⚙️ Required Permissions

To function natively over other applications, Pie Controls requires two core Android permissions:
1. **Display Over Other Apps (System Alert Window):** Allows the hidden trigger bar and pie menu to render on top of games, videos, and standard apps without stealing focus.
2. **Accessibility Service:** Required to securely execute system-level navigation commands (`GLOBAL_ACTION_HOME`, `GLOBAL_ACTION_BACK`, `GLOBAL_ACTION_RECENTS`) and pull down the notification shade.

## 🚀 Installation & Build

This project utilizes GitHub Actions for automated, bug-free APK generation.

1. Clone or fork this repository.
2. Navigate to the **Actions** tab in your GitHub repository.
3. Select the latest successful **Build Android APK** workflow run.
4. Scroll to the **Artifacts** section at the bottom and download the compiled APK.
5. Install on your Android device and grant the requested overlay and accessibility permissions on the launch screen.

## 🎛 Calibration 

The edge trigger can be perfectly calibrated to your specific device screen:
* **Bar Height & Width:** Scale the invisible touch-zone to match your thumb's natural resting position.
* **Bar Opacity:** Make the edge trigger completely invisible or slightly tinted.
* **Vertical Positioning:** Slide the trigger infinitely up and down the Y-axis to avoid interfering with specific in-app buttons or keyboards.
---

## 📥 Download the App

Ready to try it out? Download the latest stable, bug-free APK directly from the official releases page:

[![Download APK](https://img.shields.io/badge/Download-V1.0_APK-2979FF?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Luckybdsh/radial-pie-controls/releases/tag/V1.0)

*(Clicking the button above will take you to the V1.0 Release page where you can download the `.apk` file under the **Assets** dropdown).*
