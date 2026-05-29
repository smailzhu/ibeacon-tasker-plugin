package io.github.smailzhu.ibeacontasker;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import java.util.List;

public final class BeaconMonitorService extends Service {
    private static final String ACTION_START =
            "io.github.smailzhu.ibeacontasker.action.START_MONITOR";
    private static final String ACTION_STOP =
            "io.github.smailzhu.ibeacontasker.action.STOP_MONITOR";
    private static final String CHANNEL_ID = "ibeacon_monitor";
    private static final int NOTIFICATION_ID = 2001;

    private static volatile boolean running;

    private android.bluetooth.le.BluetoothLeScanner scanner;
    private ScanCallback scanCallback;

    static void start(Context context) {
        Intent intent = new Intent(context, BeaconMonitorService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    static void stop(Context context) {
        running = false;
        context.stopService(new Intent(context, BeaconMonitorService.class).setAction(ACTION_STOP));
    }

    static boolean isRunning(Context context) {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String problem = PermissionUtils.monitorReadinessProblem(this);
        if (problem != null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            startForegroundCompat();
        } catch (RuntimeException ex) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!startScan()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopScan();
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("MissingPermission")
    private boolean startScan() {
        if (scanner != null) {
            return true;
        }

        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            return false;
        }

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            return false;
        }

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                record(result);
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                for (ScanResult result : results) {
                    record(result);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                stopSelf();
            }
        };

        try {
            scanner.startScan(
                    IBeaconScanner.scanFilters(),
                    IBeaconScanner.backgroundMonitorScanSettings(),
                    scanCallback);
            return true;
        } catch (RuntimeException ex) {
            scanner = null;
            scanCallback = null;
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        if (scanner != null && scanCallback != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (RuntimeException ignored) {
            }
        }
        scanner = null;
        scanCallback = null;
    }

    private void record(ScanResult result) {
        IBeacon beacon = IBeaconParser.fromScanResult(result);
        if (beacon != null) {
            BeaconCache.record(this, beacon);
        }
    }

    private void startForegroundCompat() {
        Notification notification = notification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification notification() {
        Intent intent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder
                .setSmallIcon(R.drawable.ic_stat_ibeacon)
                .setContentTitle("iBeacon compatibility monitor")
                .setContentText("Supporting Tasker background scans")
                .setContentIntent(contentIntent)
                .setDefaults(0)
                .setLocalOnly(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(Notification.PRIORITY_LOW);
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_scans),
                NotificationManager.IMPORTANCE_LOW);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        channel.setSound(null, null);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }
}
