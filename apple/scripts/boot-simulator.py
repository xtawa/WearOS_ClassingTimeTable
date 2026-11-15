"""Boot an existing simulator with a bounded wait and one non-destructive retry."""
import subprocess
import sys
import time
import uuid


def boot(device, timeout=420, attempts=2):
    uuid.UUID(device)
    for attempt in range(1, attempts + 1):
        print(f"Booting simulator {device}, attempt {attempt}/{attempts}", flush=True)
        deadline = time.monotonic() + timeout
        try:
            # Boot reports an error for an already booted device; bootstatus must
            # still confirm readiness before the caller can proceed.
            subprocess.run(["xcrun", "simctl", "boot", device], check=False,
                           timeout=max(0.1, deadline - time.monotonic()))
            subprocess.run(["xcrun", "simctl", "bootstatus", device, "-b"], check=True,
                           timeout=max(0.1, deadline - time.monotonic()))
            return
        except (subprocess.TimeoutExpired, subprocess.CalledProcessError) as error:
            print(f"Simulator readiness failed: {error}", flush=True)
            if attempt == attempts:
                raise
            # Preserve all simulator data. A hung migration gets one clean
            # shutdown/reboot; a second failure fails CI instead of hanging.
            subprocess.run(["xcrun", "simctl", "shutdown", device], check=False, timeout=30)


if __name__ == "__main__":
    boot(sys.argv[1])
