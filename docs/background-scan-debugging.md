# Background Scan Debugging Notes

These notes are for checking whether Tasker iBeacon detection works without the optional compatibility monitor.

## Current Behavior

- Tasker-triggered scans first check `BeaconCache` for a matching beacon seen in the last 30 seconds.
- If there is no cache hit, `TaskerFireReceiver` starts an on-demand Bluetooth LE scan.
- The compatibility monitor is optional. Its main purpose is to support devices where Tasker background scans are unreliable by keeping `BeaconCache` warm in a foreground service.
- A successful Tasker result soon after another scan may be coming from the 30-second cache, not from a fresh scan.

## Check Whether The Monitor Is Running

Use ADB while the phone is connected:

```bash
adb shell dumpsys activity services io.github.smailzhu.ibeacontasker | grep -i BeaconMonitorService
```

Expected when the monitor is stopped: no output.

Check for the compatibility monitor notification:

```bash
adb shell dumpsys notification --noredact | grep -i "iBeacon compatibility monitor"
```

Expected when the monitor is stopped: no output.

## Avoid Cache False Positives

After any successful scan, wait at least 35 seconds before testing again.

The Tasker cache max age is currently:

```java
BeaconCache.TASKER_MAX_AGE_MS = 30000
```

So a result inside 30 seconds may not prove that a fresh background scan worked.

## Test Matrix Before Removing The Monitor

Run all tests with the compatibility monitor stopped.

1. App open, Tasker trigger works.
2. App sent to background, wait 2 minutes, Tasker trigger works.
3. App removed from recent apps, wait 2 minutes, Tasker trigger works.
4. Screen off, wait 10-30 minutes, Tasker trigger works.
5. Beacon turned off or far away, wait 35 seconds, Tasker reports `ibeacon_found=false`.
6. Phone rebooted, do not open the app, Tasker trigger works.

Do not use Android Settings > Force stop as the main test. Force stop puts the package into a stopped state and can block normal background delivery until the app is opened again.

## If All Tests Pass

It should be reasonable to remove:

- `BeaconMonitorService`
- the Start/Stop compatibility monitor button
- the foreground-service notification and notification channel
- `POST_NOTIFICATIONS`
- `FOREGROUND_SERVICE_CONNECTED_DEVICE`
- monitor-specific permission checks

Keep background location permission unless testing proves Tasker-triggered scans still work while the app is not visible without it.

## Useful Files

- `app/src/main/java/io/github/smailzhu/ibeacontasker/TaskerFireReceiver.java`
- `app/src/main/java/io/github/smailzhu/ibeacontasker/BeaconCache.java`
- `app/src/main/java/io/github/smailzhu/ibeacontasker/BeaconMonitorService.java`
- `app/src/main/java/io/github/smailzhu/ibeacontasker/PermissionUtils.java`
- `app/src/main/AndroidManifest.xml`
