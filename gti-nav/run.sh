#!/bin/bash
# Updates the car model, builds GTI Nav and starts it in the iPhone 17 simulator.
# Usage, from anywhere:  bash ~/Downloads/gti-nav/run.sh
set -e
cd "$(dirname "$0")"

RAW="https://raw.githubusercontent.com/matejh0racek/Claude/claude/clever-hamilton-yjdmv4/gti-nav"

echo "=== 1/5 Downloading the car model"
curl -fsSL -o GTINav/polo-gti.glb "$RAW/GTINav/polo-gti.glb"
ls -l GTINav/polo-gti.glb

echo "=== 2/5 Checking the car code in ViewController.swift"
if ! grep -q "polo-gti" GTINav/ViewController.swift; then
  echo "Car code missing, adding it"
  perl -i -pe '
if (/^\/\/ MARK: - Map styles/) {
  print "// Car model on the map. Make carScale bigger or smaller if the car looks wrong,\n// and change carRotation (in degrees: 90, 180, 270) if it drives sideways or backwards.\nlet carScale: Double = 1.5\nlet carRotation: Double = 0\n\n";
}
if (/navigationMapView\.routeColor = gtiRed/) {
  $_ .= "\n                // Your car instead of the arrow, if polo-gti.glb is in the GTINav folder.\n                if let carURL = Bundle.main.url(forResource: \"polo-gti\", withExtension: \"glb\") {\n                    navigationMapView.puckType = .puck3D(Puck3DConfiguration(\n                        model: MapboxMaps.Model(uri: carURL),\n                        modelScale: .constant([carScale, carScale, carScale]),\n                        modelRotation: .constant([0, 0, carRotation]),\n                        modelCastShadows: .constant(false),\n                        modelReceiveShadows: .constant(false)\n                    ))\n                }\n";
}' GTINav/ViewController.swift
fi
sed -i '' 's/^let carScale: Double = .*/let carScale: Double = 1.5/' GTINav/ViewController.swift
grep -n "polo-gti\|carScale: Double\|carRotation: Double" GTINav/ViewController.swift

echo "=== 3/5 Building (takes a minute or two)"
if ! xcodebuild -project GTINav.xcodeproj -scheme GTINav \
     -destination 'platform=iOS Simulator,name=iPhone 17' \
     -derivedDataPath build build > build.log 2>&1; then
  grep -E "error:" build.log | head -20
  echo "BUILD FAILED. Full log: $(pwd)/build.log"
  exit 1
fi
echo "BUILD SUCCEEDED"
APP=build/Build/Products/Debug-iphonesimulator/GTINav.app
ls -l "$APP/polo-gti.glb"

echo "=== 4/5 Starting the simulator"
xcrun simctl boot "iPhone 17" 2>/dev/null || true
open -b com.apple.iphonesimulator 2>/dev/null \
  || { SIM="$(find "$(xcode-select -p)/.." -maxdepth 6 -name 'Simulator.app' 2>/dev/null | head -1)"; [ -n "$SIM" ] && open "$SIM"; } \
  || echo "Could not open the Simulator window. In Xcode choose: Xcode > Open Developer Tool > Simulator"

echo "=== 5/5 Installing and launching GTI Nav"
xcrun simctl install booted "$APP"
xcrun simctl launch --terminate-running-process booted com.matejhoracek.GTINav
echo "=== Done. In the simulator, tap 'Start test drive'."
