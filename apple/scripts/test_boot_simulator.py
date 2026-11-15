import importlib.util
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("boot_simulator", Path(__file__).with_name("boot-simulator.py"))
simulator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(simulator)
DEVICE = "FCC5F673-7BEC-4A7D-8F2A-4DC35E2668EC"


class SimulatorBootTests(unittest.TestCase):
    @patch.object(simulator.subprocess, "run")
    def test_already_booted_still_checks_readiness(self, run):
        run.side_effect = [subprocess.CompletedProcess([], 149), subprocess.CompletedProcess([], 0)]
        simulator.boot(DEVICE)
        self.assertEqual(run.call_args_list[-1].args[0], ["xcrun", "simctl", "bootstatus", DEVICE, "-b"])
        self.assertTrue(run.call_args_list[-1].kwargs["check"])

    @patch.object(simulator.subprocess, "run")
    def test_migration_timeout_reboots_without_erasing_data(self, run):
        run.side_effect = [None, subprocess.TimeoutExpired("bootstatus", 420), None, None, None]
        simulator.boot(DEVICE)
        commands = [call.args[0] for call in run.call_args_list]
        self.assertIn(["xcrun", "simctl", "shutdown", DEVICE], commands)
        self.assertEqual(sum("bootstatus" in command for command in commands), 2)
        self.assertFalse(any("erase" in command for command in commands))

    @patch.object(simulator.subprocess, "run")
    def test_second_readiness_failure_is_not_silenced(self, run):
        run.side_effect = [None, subprocess.CalledProcessError(1, "bootstatus"), None,
                           None, subprocess.TimeoutExpired("bootstatus", 420)]
        with self.assertRaises(subprocess.TimeoutExpired):
            simulator.boot(DEVICE)


if __name__ == "__main__":
    unittest.main()
