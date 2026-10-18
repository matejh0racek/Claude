#!/bin/bash
# Runs GTI Nav on an iOS 26 simulator and opens it in the classic Simulator app (from Xcode 26),
# which still has I/O > External Displays > CarPlay. Xcode 27's Device Hub hides CarPlay for simulators.
# Usage:  bash ~/Downloads/gti-nav/carplay.sh
set -e
cd "$(dirname "$0")"

DEVICE_NAME="GTI iPhone (iOS 26)"

echo "=== 1/5 Finding the classic Simulator app (inside Xcode 26)"
SIM26="$(find /Applications -maxdepth 6 -path '*Xcode*26*' -name Simulator.app 2>/dev/null | head -1)"
if [ -z "$SIM26" ]; then
  echo "!!! Could not find Xcode 26 in Applications. Put the unpacked Xcode 26 there (e.g. as Xcode-26.app)."
  exit 1
fi
echo "$SIM26"

echo "=== 2/5 Getting the iOS 26 simulator (a few GB the first time)"
RUNTIME="$(xcrun simctl list runtimes | grep -E '^iOS 26\.' | grep -v unavailable | tail -1 | grep -oE 'com\.apple\.CoreSimulator\.SimRuntime\.iOS-26-[0-9-]+' || true)"
if [ -z "$RUNTIME" ]; then
  for VERSION in 26.4 26.3 26.2 26.1 26.0; do
    echo "Trying iOS $VERSION ..."
    if xcodebuild -downloadPlatform iOS -buildVersion "$VERSION"; then break; fi
  done
  RUNTIME="$(xcrun simctl list runtimes | grep -E '^iOS 26\.' | grep -v unavailable | tail -1 | grep -oE 'com\.apple\.CoreSimulator\.SimRuntime\.iOS-26-[0-9-]+' || true)"
fi
if [ -z "$RUNTIME" ]; then
  echo "!!! Could not download an iOS 26 simulator. Paste the output above to Claude."
  exit 1
fi
echo "$RUNTIME"

echo "=== 3/5 Creating the iOS 26 iPhone"
UDID="$(xcrun simctl list devices | grep -F "$DEVICE_NAME (" | grep -oE '[0-9A-F-]{36}' | head -1 || true)"
if [ -z "$UDID" ]; then
  UDID="$(xcrun simctl create "$DEVICE_NAME" "iPhone 17" "$RUNTIME")"
fi
echo "$UDID"

echo "=== 4/5 Building GTI Nav for it"
if ! xcodebuild -project GTINav.xcodeproj -scheme GTINav \
     -destination "platform=iOS Simulator,id=$UDID" \
     -derivedDataPath build26 build > build26.log 2>&1; then
  grep -E "error:" build26.log | head -20
  echo "BUILD FAILED. Full log: $(pwd)/build26.log"
  exit 1
fi
echo "BUILD SUCCEEDED"

echo "=== 5/5 Starting it in the classic Simulator"
osascript -e 'quit app "Device Hub"' 2>/dev/null || true
# Shut down other simulators so the classic Simulator shows this one.
xcrun simctl shutdown all 2>/dev/null || true
xcrun simctl boot "$UDID"
defaults write com.apple.iphonesimulator CarPlay -bool YES
open "$SIM26" --args -CurrentDeviceUDID "$UDID"
sleep 5
xcrun simctl install "$UDID" build26/Build/Products/Debug-iphonesimulator/GTINav.app
xcrun simctl launch "$UDID" com.matejhoracek.GTINav
echo "=== Done. In the Simulator menu bar choose I/O > External Displays > CarPlay,"
echo "    then tap GTI Nav on the car screen and press 'Test drive'."
