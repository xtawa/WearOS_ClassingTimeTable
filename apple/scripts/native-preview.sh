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
xcrun xcresulttool export attachments --path "$ROOT/build/UITests.xcresult" --output-path "$ROOT/build/screenshots/test-attachments"
# XCTest captures after the app is ready, unlike an immediate post-launch simctl capture.
python3 - "$ROOT/build/screenshots" <<'PY'
import json, shutil, sys
from pathlib import Path
output = Path(sys.argv[1])
attachments = output / 'test-attachments'
for test in json.loads((attachments / 'manifest.json').read_text()):
    for item in test['attachments']:
        name = item['suggestedHumanReadableName'].split('_0_')[0]
        if name.startswith('iPhone-'):
            shutil.copyfile(attachments / item['exportedFileName'], output / (name + '.png'))
PY
xcrun simctl install "$WATCH" "$ROOT/build/watch/Build/Products/Debug-watchsimulator/ClassingWatch.app"
xcrun simctl launch "$WATCH" com.xtawa.classingtime.apple.watchkitapp --demo
xcrun simctl io "$WATCH" screenshot "$ROOT/build/screenshots/Apple-Watch.png"
