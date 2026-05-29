package io.github.smailzhu.ibeacontasker;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.BroadcastReceiver.PendingResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class TaskerFireReceiver extends BroadcastReceiver {
    private static final String TAG = "TaskerFireReceiver";
    private static final String ACTION_SCAN_RESULT =
            "io.github.smailzhu.ibeacontasker.ACTION_SCAN_RESULT";
    private static final String ACTION_SCAN_TIMEOUT =
            "io.github.smailzhu.ibeacontasker.ACTION_SCAN_TIMEOUT";
    private static final String EXTRA_SCAN_ID = "scan_id";
    private static final String SCAN_PREFS = "tasker_active_scans";
    private static final String KEY_COMPLETION_INTENT = "completion_intent";
    private static final String KEY_EXPIRES_AT = "expires_at";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_HAS_MAJOR = "has_major";
    private static final String KEY_MAJOR = "major";
    private static final String KEY_HAS_MINOR = "has_minor";
    private static final String KEY_MINOR = "minor";
    private static final String KEY_DURATION_MS = "duration_ms";
    private static final String KEY_HAS_MIN_RSSI = "has_min_rssi";
    private static final String KEY_MIN_RSSI = "min_rssi";
    private static final AtomicInteger NEXT_SCAN_ID =
            new AtomicInteger((int) (System.currentTimeMillis() & 0x3FFFFFFF));
    private static final Map<Integer, ActiveScan> ACTIVE_SCANS =
            Collections.synchronizedMap(new HashMap<>());

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        if (ACTION_SCAN_RESULT.equals(action)) {
            handleScanResult(context.getApplicationContext(), intent);
            return;
        }

        if (ACTION_SCAN_TIMEOUT.equals(action)) {
            handleScanTimeout(context.getApplicationContext(), intent);
            return;
        }

        if (!LocaleConstants.ACTION_FIRE_SETTING.equals(action)) {
            return;
        }

        startTaskerScan(context.getApplicationContext(), intent, isOrderedBroadcast());
    }

    private void startTaskerScan(Context context, Intent intent, boolean orderedBroadcast) {
        boolean completionExpected = TaskerCompat.Setting.hasCompletionIntent(intent);
        if (!completionExpected && !orderedBroadcast) {
            Log.w(TAG, "Tasker fire intent has no completion intent and is not ordered");
            return;
        }

        PendingResult pendingResult = completionExpected ? null : goAsync();
        Intent fireIntent = new Intent(intent);
        ScanConfig config = ScanConfig.fromBundle(
                fireIntent.getBundleExtra(LocaleConstants.EXTRA_BUNDLE));
        int scanId = NEXT_SCAN_ID.incrementAndGet();
        long expiresAtElapsed = SystemClock.elapsedRealtime() + config.durationMs;
        ActiveScan activeScan = new ActiveScan(
                scanId,
                context,
                fireIntent,
                pendingResult,
                completionExpected,
                config,
                expiresAtElapsed);
        ACTIVE_SCANS.put(scanId, activeScan);

        if (completionExpected && orderedBroadcast) {
            setResultCode(TaskerCompat.Setting.RESULT_CODE_PENDING);
        }

        new Handler(Looper.getMainLooper()).post(() -> beginScan(activeScan));
    }

    @SuppressLint("MissingPermission")
    private static void beginScan(ActiveScan activeScan) {
        String readinessProblem = PermissionUtils.taskerScanReadinessProblem(activeScan.context);
        if (readinessProblem != null) {
            finishScan(activeScan, TaskerCompat.Setting.RESULT_CODE_FAILED,
                    TaskerVariables.forError(readinessProblem));
            return;
        }

        BluetoothManager manager = activeScan.context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            finishScan(activeScan, TaskerCompat.Setting.RESULT_CODE_FAILED,
                    TaskerVariables.forError("This device does not have Bluetooth"));
            return;
        }

        IBeacon cachedBeacon = BeaconCache.bestMatch(
                activeScan.context,
                activeScan.config,
                BeaconCache.TASKER_MAX_AGE_MS);
        if (cachedBeacon != null) {
            finishScan(activeScan, TaskerCompat.Setting.RESULT_CODE_OK,
                    TaskerVariables.forBeacon(cachedBeacon));
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            beginCallbackScan(activeScan);
            return;
        }

        activeScan.scanner = adapter.getBluetoothLeScanner();
        if (activeScan.scanner == null) {
            finishScan(activeScan, TaskerCompat.Setting.RESULT_CODE_FAILED,
                    TaskerVariables.forError("Bluetooth LE scanner is unavailable"));
            return;
        }

        activeScan.scanResultIntent = scanResultPendingIntent(activeScan.context, activeScan.scanId);
        try {
            int startResult = activeScan.scanner.startScan(
                    IBeaconScanner.scanFilters(),
                    IBeaconScanner.scanSettings(),
                    activeScan.scanResultIntent);
            if (startResult != 0) {
                finishScan(activeScan, TaskerCompat.Setting.RESULT_CODE_FAILED,
                        TaskerVariables.forError("BLE scan failed with code " + startResult));
                return;
            }
            persistActiveScan(activeScan);
            scheduleTimeout(activeScan);
            activeScan.handler.postDelayed(
                    () -> finishScan(activeScan, TaskerCompat.Setting.RESULT_CODE_OK,
                            TaskerVariables.forBeacon(activeScan.bestBeacon)),
                    remainingMs(activeScan));
        } catch (SecurityException ex) {
            String message = ex.getMessage() == null
                    ? "Bluetooth scan permission is not granted"
                    : ex.getMessage();
            finishScan(activeScan, TaskerCompat.Setting.RESULT_CODE_FAILED,
                    TaskerVariables.forError(message));
        } catch (RuntimeException ex) {
            String message = ex.getMessage() == null ? "Unable to start BLE scan" : ex.getMessage();
            finishScan(activeScan, TaskerCompat.Setting.RESULT_CODE_FAILED,
                    TaskerVariables.forError(message));
        }
    }

    private static void beginCallbackScan(ActiveScan activeScan) {
        activeScan.callbackScanner = new IBeaconScanner(
                activeScan.context,
                activeScan.config,
                new IBeaconScanner.Listener() {
                    @Override
                    public void onBeacon(IBeacon beacon) {
                    }

                    @Override
                    public void onCompleted(IBeacon bestBeacon) {
                        finishScan(activeScan, TaskerCompat.Setting.RESULT_CODE_OK,
                                TaskerVariables.forBeacon(bestBeacon));
                    }

                    @Override
                    public void onError(String message) {
                        finishScan(activeScan, TaskerCompat.Setting.RESULT_CODE_FAILED,
                                TaskerVariables.forError(message));
                    }
                });
        persistActiveScan(activeScan);
        scheduleTimeout(activeScan);
        activeScan.callbackScanner.start();
    }

    private static void handleScanResult(Context context, Intent intent) {
        int scanId = intent.getIntExtra(EXTRA_SCAN_ID, -1);
        ActiveScan activeScan = activeScan(context, scanId);
        if (activeScan == null) {
            return;
        }

        if (intent.hasExtra(BluetoothLeScanner.EXTRA_ERROR_CODE)) {
            int errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1);
            finishScan(activeScan, TaskerCompat.Setting.RESULT_CODE_FAILED,
                    TaskerVariables.forError("BLE scan failed with code " + errorCode));
            return;
        }

        ArrayList<ScanResult> results = scanResults(intent);
        if (results == null || results.isEmpty()) {
            return;
        }

        boolean foundMatch = false;
        for (ScanResult result : results) {
            IBeacon beacon = IBeaconParser.fromScanResult(result);
            if (beacon == null) {
                continue;
            }
            BeaconCache.record(activeScan.context, beacon);
            if (!activeScan.config.matches(beacon)) {
                continue;
            }
            foundMatch = true;
            if (activeScan.bestBeacon == null || beacon.rssi > activeScan.bestBeacon.rssi) {
                activeScan.bestBeacon = beacon;
            }
        }

        if (foundMatch) {
            finishScan(activeScan, TaskerCompat.Setting.RESULT_CODE_OK,
                    TaskerVariables.forBeacon(activeScan.bestBeacon));
        }
    }

    private static void handleScanTimeout(Context context, Intent intent) {
        int scanId = intent.getIntExtra(EXTRA_SCAN_ID, -1);
        ActiveScan activeScan = activeScan(context, scanId);
        if (activeScan == null) {
            return;
        }
        finishScan(activeScan, TaskerCompat.Setting.RESULT_CODE_OK,
                TaskerVariables.forBeacon(activeScan.bestBeacon));
    }

    @SuppressLint("MissingPermission")
    private static void finishScan(ActiveScan activeScan, int resultCode, Bundle variables) {
        if (activeScan.finished) {
            return;
        }
        activeScan.finished = true;
        ACTIVE_SCANS.remove(activeScan.scanId);
        activeScan.handler.removeCallbacksAndMessages(null);
        removePersistedScan(activeScan.context, activeScan.scanId);
        cancelTimeout(activeScan.context, activeScan.scanId);

        if (activeScan.callbackScanner != null) {
            activeScan.callbackScanner.cancel();
        }

        stopPendingIntentScan(activeScan);

        if (activeScan.completionExpected) {
            TaskerCompat.Setting.signalFinish(
                    activeScan.context,
                    activeScan.fireIntent,
                    resultCode,
                    variables);
            return;
        }

        if (activeScan.pendingResult == null) {
            return;
        }
        activeScan.pendingResult.setResultCode(resultCode);
        if (TaskerCompat.Setting.hostSupportsVariableReturn(activeScan.fireIntent.getExtras())) {
            TaskerCompat.addVariableBundle(activeScan.pendingResult.getResultExtras(true), variables);
        }
        try {
            activeScan.pendingResult.finish();
        } catch (RuntimeException ignored) {
        }
    }

    @SuppressLint("MissingPermission")
    private static void stopPendingIntentScan(ActiveScan activeScan) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        PendingIntent pendingIntent = activeScan.scanResultIntent;
        if (pendingIntent == null) {
            pendingIntent = scanResultPendingIntent(activeScan.context, activeScan.scanId);
        }

        BluetoothLeScanner scanner = activeScan.scanner;
        if (scanner == null) {
            BluetoothManager manager =
                    activeScan.context.getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            scanner = adapter == null ? null : adapter.getBluetoothLeScanner();
        }

        if (scanner != null) {
            try {
                scanner.stopScan(pendingIntent);
            } catch (RuntimeException ignored) {
            }
        }
        pendingIntent.cancel();
    }

    private static PendingIntent scanResultPendingIntent(Context context, int scanId) {
        Intent intent = new Intent(context, TaskerFireReceiver.class)
                .setAction(ACTION_SCAN_RESULT)
                .putExtra(EXTRA_SCAN_ID, scanId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(context, scanId, intent, flags);
    }

    private static PendingIntent scanTimeoutPendingIntent(Context context, int scanId) {
        Intent intent = new Intent(context, TaskerFireReceiver.class)
                .setAction(ACTION_SCAN_TIMEOUT)
                .putExtra(EXTRA_SCAN_ID, scanId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, scanId, intent, flags);
    }

    private static void scheduleTimeout(ActiveScan activeScan) {
        AlarmManager alarmManager = activeScan.context.getSystemService(AlarmManager.class);
        if (alarmManager == null) {
            return;
        }
        PendingIntent pendingIntent =
                scanTimeoutPendingIntent(activeScan.context, activeScan.scanId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    activeScan.expiresAtElapsed,
                    pendingIntent);
        } else {
            alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    activeScan.expiresAtElapsed,
                    pendingIntent);
        }
    }

    private static void cancelTimeout(Context context, int scanId) {
        AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        PendingIntent pendingIntent = scanTimeoutPendingIntent(context, scanId);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
        pendingIntent.cancel();
    }

    private static ActiveScan activeScan(Context context, int scanId) {
        ActiveScan activeScan = ACTIVE_SCANS.get(scanId);
        if (activeScan != null) {
            return activeScan;
        }
        ActiveScan restoredScan = restoreActiveScan(context, scanId);
        if (restoredScan == null) {
            return null;
        }
        ACTIVE_SCANS.put(scanId, restoredScan);
        restoredScan.handler.postDelayed(
                () -> finishScan(restoredScan, TaskerCompat.Setting.RESULT_CODE_OK,
                        TaskerVariables.forBeacon(restoredScan.bestBeacon)),
                remainingMs(restoredScan));
        return restoredScan;
    }

    private static long remainingMs(ActiveScan activeScan) {
        return Math.max(0, activeScan.expiresAtElapsed - SystemClock.elapsedRealtime());
    }

    private static void persistActiveScan(ActiveScan activeScan) {
        if (!activeScan.completionExpected) {
            return;
        }

        String completionIntentString =
                TaskerCompat.Setting.completionIntentString(activeScan.fireIntent);
        if (completionIntentString == null) {
            return;
        }

        ScanConfig config = activeScan.config;
        SharedPreferences.Editor editor = scanPrefs(activeScan.context).edit();
        String prefix = scanPrefix(activeScan.scanId);
        editor.putString(prefix + KEY_COMPLETION_INTENT, completionIntentString);
        editor.putLong(prefix + KEY_EXPIRES_AT, activeScan.expiresAtElapsed);
        if (config.uuid != null) {
            editor.putString(prefix + KEY_UUID, config.uuid);
        } else {
            editor.remove(prefix + KEY_UUID);
        }
        editor.putBoolean(prefix + KEY_HAS_MAJOR, config.major != null);
        if (config.major != null) {
            editor.putInt(prefix + KEY_MAJOR, config.major);
        } else {
            editor.remove(prefix + KEY_MAJOR);
        }
        editor.putBoolean(prefix + KEY_HAS_MINOR, config.minor != null);
        if (config.minor != null) {
            editor.putInt(prefix + KEY_MINOR, config.minor);
        } else {
            editor.remove(prefix + KEY_MINOR);
        }
        editor.putInt(prefix + KEY_DURATION_MS, config.durationMs);
        editor.putBoolean(prefix + KEY_HAS_MIN_RSSI, config.minRssi != null);
        if (config.minRssi != null) {
            editor.putInt(prefix + KEY_MIN_RSSI, config.minRssi);
        } else {
            editor.remove(prefix + KEY_MIN_RSSI);
        }
        editor.commit();
    }

    private static ActiveScan restoreActiveScan(Context context, int scanId) {
        SharedPreferences preferences = scanPrefs(context);
        String prefix = scanPrefix(scanId);
        String completionIntentString =
                preferences.getString(prefix + KEY_COMPLETION_INTENT, null);
        if (completionIntentString == null) {
            return null;
        }

        ScanConfig config = new ScanConfig(
                preferences.getString(prefix + KEY_UUID, null),
                preferences.getBoolean(prefix + KEY_HAS_MAJOR, false)
                        ? preferences.getInt(prefix + KEY_MAJOR, 0)
                        : null,
                preferences.getBoolean(prefix + KEY_HAS_MINOR, false)
                        ? preferences.getInt(prefix + KEY_MINOR, 0)
                        : null,
                preferences.getInt(prefix + KEY_DURATION_MS, ScanConfig.DEFAULT_DURATION_MS),
                preferences.getBoolean(prefix + KEY_HAS_MIN_RSSI, false)
                        ? preferences.getInt(prefix + KEY_MIN_RSSI, 0)
                        : null);
        return new ActiveScan(
                scanId,
                context,
                TaskerCompat.Setting.fireIntentForCompletionString(completionIntentString),
                null,
                true,
                config,
                preferences.getLong(prefix + KEY_EXPIRES_AT, SystemClock.elapsedRealtime()));
    }

    private static void removePersistedScan(Context context, int scanId) {
        String prefix = scanPrefix(scanId);
        scanPrefs(context).edit()
                .remove(prefix + KEY_COMPLETION_INTENT)
                .remove(prefix + KEY_EXPIRES_AT)
                .remove(prefix + KEY_UUID)
                .remove(prefix + KEY_HAS_MAJOR)
                .remove(prefix + KEY_MAJOR)
                .remove(prefix + KEY_HAS_MINOR)
                .remove(prefix + KEY_MINOR)
                .remove(prefix + KEY_DURATION_MS)
                .remove(prefix + KEY_HAS_MIN_RSSI)
                .remove(prefix + KEY_MIN_RSSI)
                .apply();
    }

    private static SharedPreferences scanPrefs(Context context) {
        return context.getSharedPreferences(SCAN_PREFS, Context.MODE_PRIVATE);
    }

    private static String scanPrefix(int scanId) {
        return scanId + ".";
    }

    @SuppressWarnings("deprecation")
    private static ArrayList<ScanResult> scanResults(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableArrayListExtra(
                    BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
                    ScanResult.class);
        }
        return intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT);
    }

    private static final class ActiveScan {
        final int scanId;
        final Context context;
        final Intent fireIntent;
        final PendingResult pendingResult;
        final boolean completionExpected;
        final ScanConfig config;
        final long expiresAtElapsed;
        final Handler handler = new Handler(Looper.getMainLooper());

        BluetoothLeScanner scanner;
        PendingIntent scanResultIntent;
        IBeaconScanner callbackScanner;
        IBeacon bestBeacon;
        boolean finished;

        ActiveScan(int scanId, Context context, Intent fireIntent, PendingResult pendingResult,
                boolean completionExpected, ScanConfig config, long expiresAtElapsed) {
            this.scanId = scanId;
            this.context = context;
            this.fireIntent = fireIntent;
            this.pendingResult = pendingResult;
            this.completionExpected = completionExpected;
            this.config = config;
            this.expiresAtElapsed = expiresAtElapsed;
        }
    }
}
