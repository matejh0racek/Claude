# GTI Nav for Android and Android Auto

The Android version of GTI Nav: the same dark map, glowing red route, white 3D Polo GTI,
3D buildings and Waze-style camera as the iPhone app, plus an Android Auto car screen.

## What's in it

- **Start screen:** "Where to?" search, and **Start test drive**, which runs a simulated
  drive from Brno hlavní nádraží to Výstaviště, so you can test at your desk.
- **Driving screen:** turn card at the top, street name, and a bottom card with time left,
  **ARRIVAL** (in red) and distance. On the right are route overview and ✕ (end).
  Searching for a place drives from your real GPS position.
- **Android Auto:** the car screen shows the same map, the turn card and the arrival
  estimate. All controls stay on the phone. Start and end drives on the phone; the car
  follows. The car screen needs one button by Android Auto's rules, so it shows a "GTI"
  button that does nothing.

## Setup (once)

1. Install **Android Studio** from developer.android.com/studio.
2. Open this `gti-nav-android` folder in Android Studio (File → Open) and let it finish syncing.
3. Paste your public Mapbox token (the `pk.` one from the iPhone app) into
   `app/src/main/res/values/mapbox_access_token.xml`, replacing `PASTE_YOUR_PUBLIC_MAPBOX_TOKEN_HERE`.

## Run it on your Android phone

1. On the phone, open Settings → About phone and tap **Build number** 7 times, then turn on
   **USB debugging** in Settings → System → Developer options.
2. Plug the phone into the Mac and allow debugging when asked.
3. In Android Studio, pick your phone at the top and press ▶ Run.
4. Allow location (and notifications) when GTI Nav asks.

## Show it in your car (Android Auto, without Google's approval)

1. On the phone, open the **Android Auto** settings (Settings → Connected devices →
   Connection preferences → Android Auto, or search for "Android Auto").
2. Scroll down and tap **Version** about 10 times, and confirm, to unlock developer settings.
3. Tap ⋮ (top right) → **Developer settings** → turn on **Unknown sources**.
4. Connect the phone to the car. GTI Nav appears in the car's app list.
5. Start a drive on the phone; the car screen follows.

## Try Android Auto on the Mac (no car needed)

1. Android Studio → Tools → SDK Manager → SDK Tools → tick
   **Android Auto Desktop Head Unit Emulator** → Apply.
2. On the phone: Android Auto developer settings (above) → ⋮ → **Start head unit server**.
3. In Terminal on the Mac:
   ```bash
   ~/Library/Android/sdk/platform-tools/adb forward tcp:5277 tcp:5277
   ~/Library/Android/sdk/extras/google/auto/desktop-head-unit
   ```
4. A car screen window opens. Open GTI Nav there, then start a test drive on the phone.

## Tuning

- Car size: `PHONE_CAR_SCALE` and `CAR_SCREEN_CAR_SCALE` in `GtiLook.kt`.
- Camera tilt and zoom: `applyCamera` in `GtiLook.kt`.
- Route colours and glow: `routeLineView` in `GtiLook.kt`.

## Credits

Car model: "2023 Volkswagen Polo GTI" by tonielpro520 on Sketchfab (CC-BY-4.0),
edited with a white roof, panoramic sunroof and white mirror caps.
