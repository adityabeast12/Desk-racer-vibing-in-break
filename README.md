<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/46a51921-e797-4b91-93cc-0538094b7425

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

## iPhone version (IPA)

The iOS app is built from the **same Kotlin code** as the Android app (physics, bots, items, renderer,
menus, HUD and LAN netcode) — see `ios/`. The `Build iOS (IPA)` workflow compiles it on a GitHub
macOS runner, auto-plays a race in the iPhone simulator (captures on the `ios-shots` branch) and
produces an **unsigned** `DeskRacer.ipa` (release `latest-ipa`, branch `ipa`).

### Installing on an iPhone

Apple only runs signed apps, so the IPA has to be signed with *your* Apple ID when you install it:

**Free (re-sign every 7 days)**
1. On a Windows PC or Mac install **Sideloadly** (sideloadly.io). On Windows also install iTunes and iCloud from Apple's website (not the Microsoft Store).
2. Plug the iPhone in with a cable, tap *Trust*, and unlock it.
3. Drag `DeskRacer.ipa` into Sideloadly, enter an Apple ID (a spare one is fine), press **Start**.
4. On the iPhone: Settings → General → VPN & Device Management → your Apple ID → **Trust**.
   On iOS 16+ also turn on Settings → Privacy & Security → **Developer Mode** (the phone restarts).
5. Open Desk Racer and allow **Local Network** when asked (needed for LAN races).
   After 7 days the app stops opening: plug in and press Start in Sideloadly again (progress is kept).

AltStore (altstore.io) works the same way and can refresh the app over Wi-Fi.

**Paid ($99/year Apple Developer account)** — sign once and share through TestFlight; no cable, lasts 90 days per build.

### Racing Android vs iPhone

Both phones on the same Wi-Fi (or the iPhone on the Android phone's hotspot):
1. Android: **Host** a race.
2. iPhone: **Join** — the room appears in the list after a second or two. If it doesn't, type the IP shown in the host lobby.
