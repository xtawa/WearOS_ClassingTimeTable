#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/build/screenshots"
xcrun simctl list devices available -j > "$ROOT/build/devices.json"
PHONE=$(python3 "$ROOT/scripts/select-simulator.py" "$ROOT/build/devices.json" iPhone "${CLASSING_IOS_SIMULATOR_RUNTIME:-}")
WATCH=$(python3 "$ROOT/scripts/select-simulator.py" "$ROOT/build/devices.json" Watch)
python3 "$ROOT/scripts/boot-simulator.py" "$PHONE"
test_status=0
xcodebuild -project "$ROOT/Classing.xcodeproj" -scheme Classing -configuration Debug \
  -destination "platform=iOS Simulator,id=$PHONE" -derivedDataPath "$ROOT/build/ios" \
  -resultBundlePath "$ROOT/build/UITests.xcresult" -parallel-testing-enabled NO CURRENT_PROJECT_VERSION="${GITHUB_RUN_NUMBER:-1}" CODE_SIGNING_ALLOWED=NO test || test_status=$?
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
if [ "$test_status" -ne 0 ]; then exit "$test_status"; fi
# Keep the Watch simulator out of the phone test's memory/CPU budget.
python3 "$ROOT/scripts/boot-simulator.py" "$WATCH"
xcrun simctl install "$WATCH" "$ROOT/build/watch/Build/Products/Debug-watchsimulator/ClassingWatch.app"
xcrun simctl launch "$WATCH" com.xtawa.classingtime.apple.watchkitapp --demo
sleep 2
xcrun simctl io "$WATCH" screenshot "$ROOT/build/screenshots/Apple-Watch.png"
xcrun simctl terminate "$WATCH" com.xtawa.classingtime.apple.watchkitapp
xcrun simctl launch "$WATCH" com.xtawa.classingtime.apple.watchkitapp --demo --settings
sleep 2
xcrun simctl io "$WATCH" screenshot "$ROOT/build/screenshots/Apple-Watch-settings.png"
xcrun simctl terminate "$WATCH" com.xtawa.classingtime.apple.watchkitapp
xcrun simctl launch "$WATCH" com.xtawa.classingtime.apple.watchkitapp --demo --assistant-preview
sleep 2
xcrun simctl io "$WATCH" screenshot "$ROOT/build/screenshots/Apple-Watch-assistant.png"
xcrun simctl terminate "$WATCH" com.xtawa.classingtime.apple.watchkitapp
xcrun simctl launch "$WATCH" com.xtawa.classingtime.apple.watchkitapp --demo --week
sleep 2
xcrun simctl io "$WATCH" screenshot "$ROOT/build/screenshots/Apple-Watch-week.png"
xcrun simctl terminate "$WATCH" com.xtawa.classingtime.apple.watchkitapp
xcrun simctl launch "$WATCH" com.xtawa.classingtime.apple.watchkitapp --demo --week --next-week
sleep 2
xcrun simctl io "$WATCH" screenshot "$ROOT/build/screenshots/Apple-Watch-next-week.png"
