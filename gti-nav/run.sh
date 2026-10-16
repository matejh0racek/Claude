#!/bin/bash
# Updates the car model, builds GTI Nav and starts it in the iPhone 17 simulator.
# Usage, from anywhere:  bash ~/Downloads/gti-nav/run.sh
set -e
cd "$(dirname "$0")"

RAW="${GTI_RAW:-https://raw.githubusercontent.com/matejh0racek/Claude/claude/clever-hamilton-yjdmv4/gti-nav}"

echo "=== 1/5 Downloading the car model"
curl -fsSL -o GTINav/polo-gti.glb "$RAW/GTINav/polo-gti.glb"
ls -l GTINav/polo-gti.glb

echo "=== 2/5 Updating ViewController.swift (Liquid Glass bars + car)"
# Keep a backup of the current file and carry over your Mapbox Studio style URL.
[ -f GTINav/ViewController.swift.bak ] || cp GTINav/ViewController.swift GTINav/ViewController.swift.bak
STYLE_LINE="$(grep '^let studioStyleURL' GTINav/ViewController.swift || true)"
curl -fsSL -o GTINav/ViewController.swift "$RAW/GTINav/ViewController.swift"
if [ -n "$STYLE_LINE" ]; then
  GTI_STYLE_LINE="$STYLE_LINE" perl -pi -e 's/^let studioStyleURL.*$/$ENV{GTI_STYLE_LINE}/' GTINav/ViewController.swift
fi
grep -c "UIGlassEffect" GTINav/ViewController.swift >/dev/null && echo "Liquid Glass code: present"
grep -c "polo-gti" GTINav/ViewController.swift >/dev/null && echo "Car code: present"
echo "(Your previous version is saved as GTINav/ViewController.swift.bak)"

if grep -q "PASTE_YOUR_PUBLIC_MAPBOX_TOKEN_HERE" GTINav/Info.plist; then
  echo "!!! Your Mapbox token is not set on this Mac, so routes will not load."
  echo "    Run this with your pk. token, then run this script again:"
  echo "    /usr/libexec/PlistBuddy -c \"Set :MBXAccessToken pk.YOUR_TOKEN\" $(pwd)/GTINav/Info.plist"
  exit 1
fi

echo "=== 3/5 Building (takes a minute or two)"
if ! xcodebuild -project GTINav.xcodeproj -scheme GTINav \
     -destination 'platform=iOS Simulator,name=iPhone 17' \
     -derivedDataPath build build > build.log 2>&1; then
  grep -E "error:" build.log | head -20
  if grep -q "CoreSimulator is out of date" build.log; then
    echo ""
    echo "!!! Xcode was updated but its simulator parts were not. Fix with:"
    echo "    sudo xcodebuild -runFirstLaunch"
    echo "    (type your Mac password; nothing shows while typing), then run this script again."
  fi
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
