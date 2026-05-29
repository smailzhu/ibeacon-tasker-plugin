package io.github.smailzhu.ibeacontasker;

import android.os.Bundle;

import java.util.Locale;

final class TaskerVariables {
    static final String[] RELEVANT_VARIABLES = {
            "%ibeacon_found\nBeacon Found\ntrue when a matching iBeacon was seen during the scan",
            "%ibeacon_uuid\nBeacon UUID\nUUID of the matching iBeacon",
            "%ibeacon_major\nBeacon Major\nMajor value of the matching iBeacon",
            "%ibeacon_minor\nBeacon Minor\nMinor value of the matching iBeacon",
            "%ibeacon_rssi\nBeacon RSSI\nRSSI in dBm for the matching iBeacon",
            "%ibeacon_tx_power\nBeacon TX Power\nAdvertised measured power for the matching iBeacon",
            "%ibeacon_distance\nBeacon Distance\nEstimated distance in meters",
            "%ibeacon_address\nBeacon Address\nBluetooth device address when Android exposes it",
            "%ibeacon_name\nBeacon Name\nAdvertised Bluetooth device name when present",
            "%ibeacon_timestamp\nBeacon Timestamp\nUnix time in milliseconds when the beacon was seen",
            "%errmsg\nError Message\nOnly set when the scan fails"
    };

    static Bundle forBeacon(IBeacon beacon) {
        Bundle vars = new Bundle();
        vars.putString("%ibeacon_found", beacon == null ? "false" : "true");
        if (beacon != null) {
            vars.putString("%ibeacon_uuid", beacon.uuid);
            vars.putString("%ibeacon_major", Integer.toString(beacon.major));
            vars.putString("%ibeacon_minor", Integer.toString(beacon.minor));
            vars.putString("%ibeacon_rssi", Integer.toString(beacon.rssi));
            vars.putString("%ibeacon_tx_power", Integer.toString(beacon.txPower));
            double distance = beacon.estimatedDistanceMeters();
            vars.putString("%ibeacon_distance", distance < 0
                    ? ""
                    : String.format(Locale.US, "%.2f", distance));
            vars.putString("%ibeacon_address", beacon.address == null ? "" : beacon.address);
            vars.putString("%ibeacon_name", beacon.name == null ? "" : beacon.name);
            vars.putString("%ibeacon_timestamp", Long.toString(beacon.timestampMillis));
        }
        return vars;
    }

    static Bundle forError(String message) {
        Bundle vars = new Bundle();
        vars.putString("%ibeacon_found", "false");
        vars.putString(TaskerCompat.Setting.VARNAME_ERROR_MESSAGE, message);
        return vars;
    }

    private TaskerVariables() {
    }
}
