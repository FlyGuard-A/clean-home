# Clean Home: a Fire TV home screen in the style of Apple TV

A small, fast home screen with no ads, no sponsored rows and no recommendations. It shows only your apps.

- A large clock and the date at the top. The background takes its color from the app you have selected.
- Apps sit in a grid of rounded 16:9 tiles, five per row. The selected tile grows, gets a shadow and shows its name.
- To rearrange: press **☰ (menu)** or **hold Select** on a tile and choose **Move**. The tile wiggles. Move it with the arrows, then press Select.
- **Hide from Home** removes apps you never use. They go into the "Hidden apps" tile, where you can bring them back.
- It also has App info, Uninstall and a Settings tile.
- The APK is about 50 KB. It uses only built-in Android parts, with no libraries, network access or tracking.

## Install

1. On the Fire TV, go to **Settings → My Fire TV → Developer options**. If Developer options is missing, open **About** and press Select on your device name 7 times. Turn on **ADB debugging**, plus **Apps from Unknown Sources** or **Install unknown apps**.
2. Get `CleanHome.apk` onto the TV in one of these ways:
   - **From a computer:** `adb connect <tv-ip>` and then `adb install CleanHome.apk`. The TV's IP is under **Settings → My Fire TV → About → Network**.
   - **From a phone:** install "Send Files to TV" on both the phone and the TV, send the APK, then open it on the TV.
   - **From a link:** put the APK somewhere with a direct download URL and open that URL in the **Downloader** app.
3. Open **Clean Home** from Your Apps.

## Make the Home button open it

Fire TV doesn't let you pick a different home app. Clean Home has a small accessibility service instead. When the Amazon home screen appears, the service opens Clean Home on top of it. It only watches the Amazon launcher and reads nothing on the screen.

Turn it on in **Settings → Accessibility** if "Clean Home – Home button" is listed there. Otherwise run this from a computer:

```
adb shell settings put secure enabled_accessibility_services com.clean.tvhome/com.clean.tvhome.HomeRedirectService
adb shell settings put secure accessibility_enabled 1
```

While the service is off, the home screen shows a tip about it.

Note: Amazon sometimes changes this behavior in Fire OS updates. If the Home button stops switching to Clean Home, open it from Your Apps instead. You can also pin it to the first spot in Amazon's app row.

## Build it yourself

`build.sh` builds the APK without the Android SDK. It uses aapt2 from apktool, `dalvik-dx`, and Robolectric's `android-all` jar, all from Maven Central:

```
TOOLS=/path/to/toolchain ./build.sh
```

The source is in `src/com/clean/tvhome/`. The `HomeActivity.java` file contains the main screen.
