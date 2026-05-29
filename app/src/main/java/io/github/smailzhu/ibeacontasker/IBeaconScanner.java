package io.github.smailzhu.ibeacontasker;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.Collections;
import java.util.List;

final class IBeaconScanner {
    interface Listener {
        void onBeacon(IBeacon beacon);

        void onCompleted(IBeacon bestBeacon);

        void onError(String message);
    }

    private static final int APPLE_COMPANY_ID = 0x004C;
    private static final byte[] IBEACON_PREFIX = {(byte) 0x02, (byte) 0x15};
    private static final byte[] IBEACON_PREFIX_MASK = {(byte) 0xFF, (byte) 0xFF};

    private final Context context;
    private final ScanConfig config;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private android.bluetooth.le.BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private IBeacon bestBeacon;
    private boolean finished;

    IBeaconScanner(Context context, ScanConfig config, Listener listener) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.listener = listener;
    }

    @SuppressLint("MissingPermission")
    void start() {
        String readinessProblem = PermissionUtils.scanReadinessProblem(context);
        if (readinessProblem != null) {
            finishWithError(readinessProblem);
            return;
        }

        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            finishWithError("This device does not have Bluetooth");
            return;
        }

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            finishWithError("Bluetooth LE scanner is unavailable");
            return;
        }

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                handleResult(result);
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                for (ScanResult result : results) {
                    handleResult(result);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                finishWithError("BLE scan failed with code " + errorCode);
            }
        };

        try {
            scanner.startScan(scanFilters(), scanSettings(), scanCallback);
            handler.postDelayed(this::finishSuccessfully, config.durationMs);
        } catch (SecurityException ex) {
            finishWithError("Bluetooth scan permission is not granted");
        } catch (RuntimeException ex) {
            finishWithError(ex.getMessage() == null ? "Unable to start BLE scan" : ex.getMessage());
        }
    }

    void stop() {
        finishSuccessfully();
    }

    void cancel() {
        if (finished) {
            return;
        }
        finished = true;
        handler.removeCallbacksAndMessages(null);
        stopScanner();
    }

    @SuppressLint("MissingPermission")
    private void handleResult(ScanResult result) {
        if (finished) {
            return;
        }
        IBeacon beacon = IBeaconParser.fromScanResult(result);
        if (beacon == null) {
            return;
        }
        BeaconCache.record(context, beacon);
        if (!config.matches(beacon)) {
            return;
        }
        if (bestBeacon == null || beacon.rssi > bestBeacon.rssi) {
            bestBeacon = beacon;
        }
        listener.onBeacon(beacon);
    }

    static List<ScanFilter> scanFilters() {
        ScanFilter filter = new ScanFilter.Builder()
                .setManufacturerData(APPLE_COMPANY_ID, IBEACON_PREFIX, IBEACON_PREFIX_MASK)
                .build();
        return Collections.singletonList(filter);
    }

    static ScanSettings scanSettings() {
        return scanSettings(ScanSettings.SCAN_MODE_LOW_LATENCY);
    }

    static ScanSettings backgroundMonitorScanSettings() {
        return scanSettings(ScanSettings.SCAN_MODE_BALANCED);
    }

    private static ScanSettings scanSettings(int scanMode) {
        return new ScanSettings.Builder()
                .setScanMode(scanMode)
                .build();
    }

    @SuppressLint("MissingPermission")
    private void finishSuccessfully() {
        if (finished) {
            return;
        }
        finished = true;
        handler.removeCallbacksAndMessages(null);
        stopScanner();
        listener.onCompleted(bestBeacon);
    }

    @SuppressLint("MissingPermission")
    private void finishWithError(String message) {
        if (finished) {
            return;
        }
        finished = true;
        handler.removeCallbacksAndMessages(null);
        stopScanner();
        listener.onError(message);
    }

    @SuppressLint("MissingPermission")
    private void stopScanner() {
        if (scanner == null || scanCallback == null) {
            return;
        }
        try {
            scanner.stopScan(scanCallback);
        } catch (RuntimeException ignored) {
        }
        scanner = null;
        scanCallback = null;
    }
}
