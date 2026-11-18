# DuloTV Android wrapper

This Android Studio project wraps https://dulotv.site/ in a fullscreen Android WebView.
It enables JavaScript, DOM storage, cookies, HTML5 media and fullscreen video, and uses Android Back to navigate page history.

## Build the APK
1. Install Android Studio (with Android SDK Platform 35).
2. Open this `DuloTV-Android` folder in Android Studio.
3. Wait for Gradle sync to finish.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Find the generated APK under `app/build/outputs/apk/debug/app-debug.apk`.

For a signed release APK, use **Build > Generate Signed Bundle / APK** and create a keystore you control.

## Notes
- This project is an app wrapper, not an offline copy of DuloTV. An internet connection is required.
- DuloTV describes itself as a discovery/catalogue site and playback may be provided by external services. Some providers may refuse playback inside embedded WebViews or require their own app/browser.
- The app does not bypass DRM, paywalls, provider restrictions, or copyright controls.
