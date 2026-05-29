package io.github.smailzhu.ibeacontasker;

import java.util.Locale;

final class IBeacon {
    final String uuid;
    final int major;
    final int minor;
    final int txPower;
    final int rssi;
    final String address;
    final String name;
    final long timestampMillis;

    IBeacon(String uuid, int major, int minor, int txPower, int rssi,
            String address, String name, long timestampMillis) {
        this.uuid = uuid;
        this.major = major;
        this.minor = minor;
        this.txPower = txPower;
        this.rssi = rssi;
        this.address = address;
        this.name = name;
        this.timestampMillis = timestampMillis;
    }

    double estimatedDistanceMeters() {
        if (rssi == 0 || txPower == 0) {
            return -1.0;
        }
        double ratio = rssi * 1.0 / txPower;
        if (ratio < 1.0) {
            return Math.pow(ratio, 10);
        }
        return 0.89976 * Math.pow(ratio, 7.7095) + 0.111;
    }

    String displayLine() {
        double distance = estimatedDistanceMeters();
        String distanceText = distance < 0 ? "n/a" : String.format(Locale.US, "%.2fm", distance);
        return uuid + " " + major + "/" + minor
                + " RSSI " + rssi
                + " tx " + txPower
                + " " + distanceText
                + (address == null ? "" : " " + address);
    }
}

