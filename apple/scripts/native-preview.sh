#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/build/screenshots"
xcrun simctl list devices available -j > "$ROOT/build/devices.json"
PHONE=$(python3 "$ROOT/scripts/select-simulator.py" "$ROOT/build/devices.json" iPhone)
WATCH=$(python3 "$ROOT/scripts/select-simulator.py" "$ROOT/build/devices.json" Watch)
# Already-booted devices are harmless; bootstatus below still fails if a device cannot boot.
xcrun simctl boot "$PHONE" || true
xcrun simctl bootstatus "$PHONE" -b
xcrun simctl boot "$WATCH" || true
xcrun simctl bootstatus "$WATCH" -b
xcodebuild -project "$ROOT/Classing.xcodeproj" -scheme Classing -configuration Debug \
  -destination "platform=iOS Simulator,id=$PHONE" -derivedDataPath "$ROOT/build/ios" \
  -resultBundlePath "$ROOT/build/UITests.xcresult" CODE_SIGNING_ALLOWED=NO test
xcrun simctl install "$PHONE" "$ROOT/build/ios/Build/Products/Debug-iphonesimulator/Classing.app"
xcrun simctl launch "$PHONE" com.xtawa.classingtime.apple --demo
xcrun simctl io "$PHONE" screenshot "$ROOT/build/screenshots/iPhone-light.png"
xcrun simctl ui "$PHONE" appearance dark
xcrun simctl io "$PHONE" screenshot "$ROOT/build/screenshots/iPhone-dark.png"
xcrun simctl install "$WATCH" "$ROOT/build/watch/Build/Products/Debug-watchsimulator/ClassingWatch.app"
xcrun simctl launch "$WATCH" com.xtawa.classingtime.apple.watchkitapp --demo
xcrun simctl io "$WATCH" screenshot "$ROOT/build/screenshots/Apple-Watch.png"
xcrun xcresulttool export attachments --path "$ROOT/build/UITests.xcresult" --output-path "$ROOT/build/screenshots/test-attachments"
