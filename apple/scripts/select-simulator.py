"""Pick an existing available Apple simulator, newest runtime first."""
import json
import sys
from pathlib import Path
payload = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
kind = sys.argv[2]
for runtime, rows in sorted(payload['devices'].items(), reverse=True):
    for device in rows:
        if device.get('isAvailable') and kind in device.get('name', ''):
            print(device['udid'])
            raise SystemExit(0)
raise SystemExit(f'No available {kind} simulator. Install its runtime on this Xcode runner.')
