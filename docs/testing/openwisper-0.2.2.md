# OpenWisper 0.2.2

Notification-only visual correction: both the ongoing dictation-service notification and the service-recovery alert use a dedicated monochrome vector of the supplied logo's ring, segmented waveform and microphone stand. Android tints the transparent icon for the status bar and notification shade; the launcher logo remains full colour. Notification channels, importance and permissions are unchanged.

The same application ID and sideload signing key preserve user data. The update does not change speech models, cloud providers or dictation behavior. Verification and installed-device details follow after the optimized build completes.

Verification: optimized release build and lint passed. Signature and 16 KiB ZIP alignment verified. Pixel 6 updated with install -r and launched normally; its active service notification reports icon resource0x7f07009f, matching drawable/ic_notification_openwisper in the packaged resource table. Own accessibility binding verified and unrelated service retained. No test helper installed for this visual-only change. Existing data preserved.

APK: 37,545,488 bytes; SHA-256 a7a3eabf24b64d3d256b1a78a9d2b88728113cde11975465fad74bba267040ff. Redmi 12 (23053RN02A) subsequently updated from 0.2.0/code3 to 0.2.2/code5 with install -r. Normal launch, microphone permission, bound OpenWisper accessibility service, no crashed service and notification icon0x7f07009f verified. Other accessibility service retained; no test helper installed; owned /data/local/tmp/openwisper-light-ui.xml removed. Main app data was not cleared. Full 154-test/phone regression evidence remains in the 0.2.1 report; this icon change was verified by release build/lint and live notification resource inspection rather than repeating those unrelated tests.
