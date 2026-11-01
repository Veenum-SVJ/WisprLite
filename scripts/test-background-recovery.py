"""Opt-in idle-process recovery check for WisprLite; requires an authorized USB device.

Kills only WisprLite through its own UID, never force-stops/uninstalls/clears data.
On a failed automatic recovery, restores only its existing accessibility entry.
"""
import argparse
import datetime
import json
import re
import subprocess
import time
from pathlib import Path

PACKAGE = "com.veenum.wisprlite"
COMPONENT = PACKAGE + "/com.edib.openwhispr.WhisperAccessibilityService"
ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", type=Path, default=ROOT / ".tools/android/sdk/platform-tools/adb.exe")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.:-]+", args.serial):
        parser.error("Invalid ADB serial")

    def command(*values, check=True):
        return subprocess.run([str(args.adb), "-s", args.serial, *values], capture_output=True,
                              text=True, encoding="utf-8", errors="replace", check=check,
                              timeout=25).stdout.strip()

    def bound():
        return "Service[label=WisprLite," in command("shell", "dumpsys", "accessibility")

    def services():
        value = command("shell", "settings", "get", "secure", "enabled_accessibility_services")
        if value == "null":
            return []
        assert re.fullmatch(r"[A-Za-z0-9_./:]*", value), "Unexpected service list"
        return [entry for entry in value.split(":") if entry]

    assert bound() and COMPONENT in services(), "WisprLite must already be enabled and bound"
    assert "(running)" not in command("shell", "cmd", "appops", "get", PACKAGE, "RECORD_AUDIO"), "Do not interrupt an active recording"
    manufacturer = command("shell", "getprop", "ro.product.manufacturer").lower()
    exempt = any(PACKAGE in line.split(",") for line in command("shell", "cmd", "deviceidle", "whitelist").splitlines())
    autostart = None
    if manufacturer in ("xiaomi", "redmi", "poco"):
        own_ops = command("shell", "cmd", "appops", "get", PACKAGE)
        autostart = bool(re.search(r"MIUIOP\(10008\): allow\b", own_ops))
        assert exempt and autostart, "First configure WisprLite battery exemption and Xiaomi Background autostart"
    original_services = services()
    command("shell", "am", "start", "-W", "-n", PACKAGE + "/com.edib.openwhispr.MainActivity")
    command("shell", "input", "keyevent", "3")
    original = command("shell", "pidof", PACKAGE)
    assert re.fullmatch(r"[0-9]+", original), "Require exactly one own process"
    recovered = False
    observations = []
    killed = False
    try:
        started = time.monotonic()
        command("shell", "run-as", PACKAGE, "kill", "-9", original)
        killed = True
        for _ in range(15):
            time.sleep(2)
            process = command("shell", "pidof", PACKAGE, check=False)
            row = dict(elapsed_ms=round((time.monotonic() - started) * 1000),
                       process_present=bool(process), new_process=bool(process and process != original),
                       service_bound=bound())
            observations.append(row)
            print(json.dumps(row), flush=True)
            if row["new_process"] and row["service_bound"]:
                recovered = True
                break
        if recovered:
            stable_process = process
            for _ in range(2):
                time.sleep(5)
                process = command("shell", "pidof", PACKAGE, check=False)
                connected = bound()
                observations.append(dict(elapsed_ms=round((time.monotonic() - started) * 1000),
                                         process_present=bool(process), new_process=bool(process and process != original),
                                         service_bound=connected, stability_sample=True))
                if process != stable_process or not connected:
                    recovered = False
                    break
        unchanged = services() == original_services
        report = dict(method="SIGKILL through own UID; no force-stop or data deletion",
                      manufacturer=manufacturer, battery_exempt=exempt, autostart_enabled=autostart,
                      automatic_rebind=recovered, secure_service_selection_unchanged=unchanged,
                      observations=observations)
        folder = ROOT / ".tools/device-tests"
        folder.mkdir(parents=True, exist_ok=True)
        path = folder / ("background-recovery-" + datetime.datetime.now().strftime("%Y%m%d-%H%M%S") + ".json")
        path.write_text(json.dumps(report, indent=2), encoding="utf-8")
        print("Recovery evidence:", path, flush=True)
        assert unchanged, "Service selections changed during the observation; investigate separately"
        assert recovered, "Automatic recovery failed; this case is not passed"
    finally:
        if killed and not recovered:
            # Respect a user disabling it during the test. Never overwrite other entries.
            current = services()
            if COMPONENT in current:
                others = [entry for entry in current if entry != COMPONENT]
                if others:
                    command("shell", "settings", "put", "secure", "enabled_accessibility_services", ":".join(others))
                else:
                    command("shell", "settings", "delete", "secure", "enabled_accessibility_services")
                command("shell", "settings", "put", "secure", "enabled_accessibility_services", ":".join(others + [COMPONENT]))
                command("shell", "settings", "put", "secure", "accessibility_enabled", "1")


if __name__ == "__main__":
    main()
