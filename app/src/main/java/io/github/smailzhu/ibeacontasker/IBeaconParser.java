package io.github.smailzhu.ibeacontasker;

import android.annotation.SuppressLint;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;

import java.util.Locale;

final class IBeaconParser {
    private static final int APPLE_COMPANY_ID = 0x004C;
    private static final int IBEACON_TYPE = 0x02;
    private static final int IBEACON_DATA_LENGTH = 0x15;
    private static final int MIN_IBEACON_MANUFACTURER_DATA_BYTES = 23;

    @SuppressLint("MissingPermission")
    static IBeacon fromScanResult(ScanResult result) {
        ScanRecord record = result.getScanRecord();
        if (record == null) {
            return null;
        }

        byte[] data = record.getManufacturerSpecificData(APPLE_COMPANY_ID);
        if (data == null || data.length < MIN_IBEACON_MANUFACTURER_DATA_BYTES) {
            return null;
        }
        if ((data[0] & 0xFF) != IBEACON_TYPE || (data[1] & 0xFF) != IBEACON_DATA_LENGTH) {
            return null;
        }

        String uuid = String.format(Locale.US,
                "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
                data[2] & 0xFF, data[3] & 0xFF, data[4] & 0xFF, data[5] & 0xFF,
                data[6] & 0xFF, data[7] & 0xFF,
                data[8] & 0xFF, data[9] & 0xFF,
                data[10] & 0xFF, data[11] & 0xFF,
                data[12] & 0xFF, data[13] & 0xFF, data[14] & 0xFF, data[15] & 0xFF,
                data[16] & 0xFF, data[17] & 0xFF);
        int major = unsigned16(data[18], data[19]);
        int minor = unsigned16(data[20], data[21]);
        int txPower = data[22];

        String address = null;
        try {
            address = result.getDevice() == null ? null : result.getDevice().getAddress();
        } catch (SecurityException ignored) {
            address = null;
        }

        return new IBeacon(
                uuid,
                major,
                minor,
                txPower,
                result.getRssi(),
                address,
                record.getDeviceName(),
                System.currentTimeMillis());
    }

    private static int unsigned16(byte high, byte low) {
        return ((high & 0xFF) << 8) | (low & 0xFF);
    }

    private IBeaconParser() {
    }
}

