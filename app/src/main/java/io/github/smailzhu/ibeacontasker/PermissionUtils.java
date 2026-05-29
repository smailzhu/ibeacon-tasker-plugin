package io.github.smailzhu.ibeacontasker;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

final class PermissionUtils {
    static String[] requiredRuntimePermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        return permissions.toArray(new String[0]);
    }

    static String[] appRuntimePermissions() {
        return requiredRuntimePermissions();
    }

    static boolean hasScanPermissions(Context context) {
        for (String permission : requiredRuntimePermissions()) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    static String[] notificationRuntimePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return new String[0];
        }
        return new String[]{Manifest.permission.POST_NOTIFICATIONS};
    }

    static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static String[] backgroundLocationRuntimePermissions() {
        if (!needsBackgroundLocationPermission()) {
            return new String[0];
        }
        return new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION};
    }

    static boolean needsBackgroundLocationPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    static boolean canRequestBackgroundLocationDirectly() {
        return Build.VERSION.SDK_INT == Build.VERSION_CODES.Q;
    }

    static boolean hasBackgroundLocationPermission(Context context) {
        if (!needsBackgroundLocationPermission()) {
            return true;
        }
        return context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    static String scanReadinessProblem(Context context) {
        if (!hasScanPermissions(context)) {
            return "Bluetooth scan permissions are not granted";
        }

        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            return "This device does not have Bluetooth";
        }
        try {
            if (!adapter.isEnabled()) {
                return "Bluetooth is turned off";
            }
        } catch (SecurityException ex) {
            return "Bluetooth connect permission is not granted";
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isLocationEnabled(context)) {
            return "Location is turned off; Android requires it for BLE scans";
        }
        return null;
    }

    static String taskerScanReadinessProblem(Context context) {
        String problem = scanReadinessProblem(context);
        if (problem != null) {
            return problem;
        }
        if (!hasBackgroundLocationPermission(context)) {
            return "Background location is not granted; open location settings and choose "
                    + backgroundLocationOptionLabel(context);
        }
        return null;
    }

    static String monitorReadinessProblem(Context context) {
        String problem = taskerScanReadinessProblem(context);
        if (problem != null) {
            return problem;
        }
        if (!hasNotificationPermission(context)) {
            return "Notifications are not granted; grant notifications to show the compatibility monitor";
        }
        return null;
    }

    static Intent appSettingsIntent(Context context) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.getPackageName(), null));
    }

    static String backgroundLocationSettingsHint(Context context) {
        return "Open Permissions > Location and choose "
                + backgroundLocationOptionLabel(context)
                + " for Tasker scans while the app is hidden.";
    }

    private static String backgroundLocationOptionLabel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            CharSequence label = context.getPackageManager().getBackgroundPermissionOptionLabel();
            if (label != null && label.length() > 0) {
                return label.toString();
            }
        }
        return "Allow all the time";
    }

    private static boolean isLocationEnabled(Context context) {
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return manager.isLocationEnabled();
        }
        try {
            return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private PermissionUtils() {
    }
}
