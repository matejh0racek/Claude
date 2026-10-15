# GTI Nav

Personal iPhone navigation app on the Mapbox Navigation SDK v3, styled after the
VW Polo GTI Digital Cockpit: black base, dark grey roads, GTI red route with a soft glow.

## Run it

1. Open `GTINav.xcodeproj` in Xcode 16 or newer. Swift Package Manager fetches
   `mapbox-navigation-ios` (3.x) on its own. No secret token or `.netrc` is needed.
2. In `GTINav/Info.plist`, replace `PASTE_YOUR_PUBLIC_MAPBOX_TOKEN_HERE` with your
   public token (`pk.…`) from https://account.mapbox.com/access-tokens/.
3. Optional: in `ViewController.swift`, set `studioStyleURL` to your Mapbox Studio
   style URL. Until you do, the app uses the bundled `gti-dark-style.json`.
4. Select an iPhone simulator and press Run, or from Terminal:

   ```
   xcodebuild -project GTINav.xcodeproj -scheme GTINav \
     -destination 'platform=iOS Simulator,name=iPhone 17' build
   ```

5. Tap **Start test drive**. A simulated drive runs from Brno hlavní nádraží to Výstaviště.

To run on your own iPhone, pick your Apple ID team under Signing & Capabilities.

## Car model

`GTINav/polo-gti.glb` replaces the navigation arrow with a white Polo GTI (white roof
with a panoramic glass sunroof, white mirror caps). It is stored without Draco
compression so Mapbox can load it. Tune `carScale` and `carRotation` at the top of
`ViewController.swift` if it looks too big, too small or turned the wrong way.

Based on "2023 Volkswagen Polo GTI" by tonielpro520
(https://sketchfab.com/3d-models/2023-volkswagen-polo-gti-42af1df85fea476e8f74ee2930fcfa29),
licensed CC-BY-4.0 (http://creativecommons.org/licenses/by/4.0/). Changes: roof and
mirror caps repainted white, sunroof glass added, model recentred and rescaled.
