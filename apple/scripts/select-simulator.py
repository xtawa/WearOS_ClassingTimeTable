"""Pick an existing available Apple simulator, newest runtime first."""
import json
import sys
from pathlib import Path
payload = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
kind = sys.argv[2]
requested_runtime = sys.argv[3] if len(sys.argv) > 3 else ''
for runtime, rows in sorted(payload['devices'].items(), reverse=True):
    if requested_runtime and runtime != requested_runtime:
        continue
    for device in rows:
        if device.get('isAvailable') and kind in device.get('name', ''):
            print(device['udid'])
            raise SystemExit(0)
raise SystemExit(f'No available {kind} simulator for {requested_runtime or "the installed runtimes"}. Install its runtime on this Xcode runner.')
