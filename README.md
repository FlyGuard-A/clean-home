# Clean Home: a Fire TV home screen in the style of Apple TV

A small, fast home screen with no ads, no sponsored rows and no recommendations. It shows only your apps.

- The background is a full-screen landscape photo (Bing's photo of the day), with a built-in mountain picture as a backup when offline.
- Apps sit in a frosted-glass dock as rounded icon tiles: each logo on its own brand color. The selected tile grows and gets a white outline. More apps sit in rows below.
- A large clock and the date are in the top left. On the right are Spotify "now playing" and today's games from ESPN, live or not: Champions League, LaLiga, Premier League and the Minnesota Timberwolves. On days with no games it just says so.
- To rearrange: press **☰ (menu)** or **hold Select** on a tile and choose **Move**. The tile wiggles. Move it with the arrows, then press Select.
- **Hide from Home** removes apps you never use. They go into the "Hidden apps" tile, where you can bring them back.
- It also has App info, Uninstall and a Settings tile.
- The TV stays on, with no screensaver or sleep, while Clean Home is on screen.
- It uses only built-in Android parts, with no libraries or tracking. The internet is used only for the background photo, scores and team logos.

## Install

1. On the Fire TV, go to **Settings → My Fire TV → Developer options**. If Developer options is missing, open **About** and press Select on your device name 7 times. Turn on **ADB debugging**, plus **Apps from Unknown Sources** or **Install unknown apps**.
2. Get `CleanHome.apk` onto the TV in one of these ways:
   - **From a computer:** `adb connect <tv-ip>` and then `adb install CleanHome.apk`. The TV's IP is under **Settings → My Fire TV → About → Network**.
   - **From a phone:** install "Send Files to TV" on both the phone and the TV, send the APK, then open it on the TV.
   - **From a link:** put the APK somewhere with a direct download URL and open that URL in the **Downloader** app.
3. Open **Clean Home** from Your Apps.

## One-time setup: Home button and Spotify "now playing"

Fire TV has no menu to give a home screen app these permissions, so you need a computer once, for about 5 minutes. Afterwards Clean Home turns on the Home button redirect and the Spotify card by itself. It also turns them back on if a Fire OS update switches them off.

1. **On the TV:** go to **Settings → My Fire TV → Developer options** and turn on **ADB debugging**. Then find the TV's IP address under **Settings → My Fire TV → About → Network**.
2. **On a Mac:** open **Terminal** and paste these lines one at a time. Replace `TV-IP` with the address from step 1.

   ```
   cd ~/Downloads
   curl -O https://dl.google.com/android/repository/platform-tools-latest-darwin.zip
   unzip -oq platform-tools-latest-darwin.zip && cd platform-tools
   ./adb connect TV-IP
   ```

   The TV asks **"Allow USB debugging?"**. Tick **Always allow from this computer** and choose **OK**. Run `./adb connect TV-IP` again, then:

   ```
   ./adb shell pm grant com.clean.tvhome android.permission.WRITE_SECURE_SETTINGS
   ```

   On Windows, download `platform-tools-latest-windows.zip` from the same address instead. Unzip it, open a terminal in that folder, and run the same commands without `./`.
3. **On the TV:** open Clean Home once. From then on the Home button brings you to Clean Home. Whenever Spotify is playing, the top-right corner shows the album art, song and artist.

How it works: Clean Home has a small accessibility service. When the Amazon home screen appears, the service opens Clean Home on top of it. It only watches the Amazon launcher and reads nothing on the screen. The Spotify card uses notification access only to see what is playing.

## Build it yourself

`build.sh` builds the APK without the Android SDK. It uses aapt2 from apktool, `dalvik-dx`, and Robolectric's `android-all` jar, all from Maven Central:

```
TOOLS=/path/to/toolchain ./build.sh
```

The source is in `src/com/clean/tvhome/`. The `HomeActivity.java` file contains the main screen.
