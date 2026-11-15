import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class SimulatorSelectionTests(unittest.TestCase):
    def select(self, runtime=None):
        payload = {"devices": {
            "com.apple.CoreSimulator.SimRuntime.iOS-26-5": [{"name": "iPhone 17 Pro", "isAvailable": True, "udid": "newer"}],
            "com.apple.CoreSimulator.SimRuntime.iOS-26-2": [{"name": "iPhone 17 Pro", "isAvailable": True, "udid": "pinned"}],
        }}
        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory) / "devices.json"
            fixture.write_text(json.dumps(payload), encoding="utf-8")
            command = [sys.executable, str(Path(__file__).with_name("select-simulator.py")), str(fixture), "iPhone"]
            if runtime:
                command.append(runtime)
            return subprocess.run(command, capture_output=True, text=True)

    def test_explicit_runtime_wins_over_newest(self):
        result = self.select("com.apple.CoreSimulator.SimRuntime.iOS-26-2")
        self.assertEqual(result.returncode, 0)
        self.assertEqual(result.stdout.strip(), "pinned")

    def test_missing_runtime_fails_without_falling_back(self):
        result = self.select("com.apple.CoreSimulator.SimRuntime.iOS-99-0")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("iOS-99-0", result.stderr)

    def test_unpinned_selection_uses_newest_available(self):
        self.assertEqual(self.select().stdout.strip(), "newer")


if __name__ == "__main__":
    unittest.main()
