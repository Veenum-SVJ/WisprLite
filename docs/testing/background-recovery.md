# Redmi background recovery

## Configuration and observed recovery

The earlier controlled process-death test left WisprLite disconnected even though its accessibility toggle and ordinary battery exemption were enabled. On 2026-11-01, the Xiaomi **Background autostart** page showed WisprLite's own switch off. Its corresponding `MIUIOP(10008)` was `ignore`. Enabling only the visible WisprLite switch changed it to `allow`; no hidden operation was written by number and no other app's switch was changed.

With battery exemption and Xiaomi autostart enabled, a controlled SIGKILL through WisprLite's own UID produced a new process and bound accessibility service after 2,418 ms. Secure accessibility selections remained unchanged. Three follow-up samples, five seconds apart, showed the same recovered PID and bound service. The test checked that microphone recording was not active before killing the process. It did not force-stop, uninstall, clear app data, alter saved providers or insert a transcript.

This establishes recovery for the observed idle-process case on the Redmi 12, not every device/firmware, reboot, force-stop, Doze or battery-management scenario. A deliberately force-stopped package is a different condition. The earlier WorkManager scheduling limitation is not retroactively relabeled a pass; this observed automatic rebind did not depend on forcing a watchdog job.

Initial private evidence: `.tools/background-autostart-recovery.json` and `.tools/background-autostart-recovery.log`. Device UI hierarchy and screenshots used to inspect Xiaomi settings stay out of Git.

## Setup help in the app

Settings includes **Background autostart** on Xiaomi/Redmi/POCO, even when standard battery exemption already reports ready. It explains that startup permission is separate from battery optimization, opens the vendor startup page, and falls back to WisprLite's own app-information page if that entry is unavailable or denied. It does not claim to read or grant the startup switch automatically. There is no new permission, runtime dependency or hidden-API bypass. A custom dotted Signal restart icon is selected explicitly, independent of the translated label. Help strings are supplied in every existing app locale.

The first build caught missing French keys; the next lint run caught the other shipped translations. Those resources were completed, then all 122 unit tests, debug/test/release APK builds and debug/release lint passed in 3m7s (`.tools/background-startup-complete-build.log`). Later changes are test-only and their results are recorded separately.

## Repeatable test and harness corrections

`python scripts/test-background-recovery.py --serial SERIAL` is an explicit debug-app test, excluded from the default suite. It checks the real binding, idle microphone, own PID and Xiaomi prerequisites; sends SIGKILL only through the app's UID; observes rebind and short stability; and records JSON under ignored `.tools/device-tests/`. It preserves secure service selections, never force-stops/uninstalls/clears data, and restores only the still-enabled own accessibility entry if recovery fails. Run only while no dictation is in progress.

The post-change UI runner exposed two fixture races, retained as failed evidence:

- `.tools/device-tests/20261101-130724/long-history-detail.txt`: Android denied clipboard reads because the test window lacked focus. The fixture now waits for stable window focus and restores the clipboard only after capturing a valid foreground snapshot.
- `.tools/device-tests/20261101-131137/model-picker-visibility.txt`: ActivityScenario's Activity was destroyed during the runner's concurrent foreground launch. Removing CLEAR_TASK alone had not eliminated the race. The runner now registers `DeviceForegroundReadyListener` for affected Redmi activity suites: the listener waits while the shell foregrounds Main, then the runner writes a unique private readiness marker before Activity tests begin. The marker is removed in cleanup. This does not mask app assertions or retry a failed test.

`uiautomator dump` can temporarily suppress other accessibility services while UI automation is attached. An absent overlay in its XML is therefore not proof of recovery failure; real binding, process identity and an ordinary empty-field visual check are separate observations. Final current-build suite, shortcut and recovery outcomes must be recorded after execution, not inferred from these harness changes.

## Verified current-build outcome

The complete corrected Redmi runner passed 29 checks, including the optional real-service offline retention fixture (9.565s), at `.tools/device-tests/20261101-131626`. Test APK/debug lint passed in 58 seconds after the readiness-listener change. The production app source was unchanged after the successful full build.

The repeatable recovery script then passed on that installed app: a new process and bound service appeared at 2,269 ms, with the same recovered process still bound at 7,601 and 12,792 ms. Secure accessibility selections were unchanged. Evidence: `.tools/device-tests/background-recovery-20261101-131939.json` and `.tools/background-startup-recovery-device.log`.

The actual installed Settings row opened its help dialog and Xiaomi's `com.miui.permcenter.autostart.AutoStartManagementActivity`, then returned to WisprLite; the startup switch was not changed by this shortcut check. The private manual check was corrected for uppercase-rendered dialog buttons, the duplicate Settings page heading behind the drawer, and asynchronous activity transition before it recorded PASS (`.tools/background-startup-shortcut-device.log`). A separate ordinary screenshot confirmed the circular floating bubble visible beside a focused empty local test field, with no recording started. Screenshots remain ignored/private.

Latest production-signed APK: 36,138,232 bytes; signature verified; SHA256 `F23343CC8C3E9D8E5DF490B7F46E19CD60CFABDF51971B2325F6B47C7FDA3614`. This supersedes earlier artifact hashes. The user-requested autostart permission remains enabled for WisprLite; other app startup switches and accessibility services were preserved.
