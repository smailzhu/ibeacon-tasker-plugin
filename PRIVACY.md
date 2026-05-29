# Privacy Policy

iBeacon Tasker Plugin scans nearby Bluetooth LE advertisements so Tasker can
react to matching iBeacon broadcasts.

## Data Collection

The app does not collect, sell, transmit, or share personal data. It does not
request Internet access and does not include analytics, ads, or crash reporting
services.

## Local Scan Data

Bluetooth LE scan results are processed on the device. Matching iBeacon details
can be returned to Tasker or another Locale-compatible automation host as local
variables.

The optional compatibility monitor may keep recent iBeacon observations in
local app storage for a short time so Tasker scans can use recently seen
beacons. This cache stays on the device.

## Permissions

Android treats Bluetooth LE scan results as location-capable, so the app
requests Bluetooth and location permissions needed for scanning. Background
location supports Tasker-triggered scans while the app is not visible.

Notification permission is requested only when starting the optional
compatibility monitor, so Android can show its foreground-service notification.

## Data Control

You can clear local app data or uninstall the app to remove local settings and
cached scan data.

