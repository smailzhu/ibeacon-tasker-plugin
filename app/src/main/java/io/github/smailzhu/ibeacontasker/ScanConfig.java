package io.github.smailzhu.ibeacontasker;

import android.os.Bundle;

import java.util.Locale;
import java.util.UUID;

final class ScanConfig {
    private static final String KEY_CONFIG_VERSION = "config_version";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_MAJOR = "major";
    private static final String KEY_MINOR = "minor";
    private static final String KEY_DURATION_MS = "duration_ms";
    private static final String KEY_MIN_RSSI = "min_rssi";

    static final int DEFAULT_DURATION_MS = 10000;
    static final int MIN_DURATION_MS = 1000;
    static final int MAX_DURATION_MS = 60000;

    final String uuid;
    final Integer major;
    final Integer minor;
    final int durationMs;
    final Integer minRssi;

    ScanConfig(String uuid, Integer major, Integer minor, int durationMs, Integer minRssi) {
        this.uuid = normalizeUuidOrNull(uuid);
        this.major = major;
        this.minor = minor;
        this.durationMs = Math.max(MIN_DURATION_MS, Math.min(durationMs, MAX_DURATION_MS));
        this.minRssi = minRssi;
    }

    static ScanConfig defaults() {
        return new ScanConfig(null, null, null, DEFAULT_DURATION_MS, null);
    }

    static ScanConfig fromBundle(Bundle bundle) {
        if (bundle == null) {
            return defaults();
        }
        String uuid = emptyToNull(bundle.getString(KEY_UUID));
        Integer major = bundle.containsKey(KEY_MAJOR) ? bundle.getInt(KEY_MAJOR) : null;
        Integer minor = bundle.containsKey(KEY_MINOR) ? bundle.getInt(KEY_MINOR) : null;
        Integer minRssi = bundle.containsKey(KEY_MIN_RSSI) ? bundle.getInt(KEY_MIN_RSSI) : null;
        int durationMs = bundle.getInt(KEY_DURATION_MS, DEFAULT_DURATION_MS);
        return new ScanConfig(uuid, major, minor, durationMs, minRssi);
    }

    static ScanConfig fromInput(String uuidValue, String majorValue, String minorValue,
            String durationSecondsValue, String minRssiValue) {
        String uuid = normalizeUuidOrNull(uuidValue);
        Integer major = parseOptionalUnsigned16("Major", majorValue);
        Integer minor = parseOptionalUnsigned16("Minor", minorValue);
        int durationSeconds = parseDurationSeconds(durationSecondsValue);
        Integer minRssi = parseOptionalRssi(minRssiValue);
        return new ScanConfig(uuid, major, minor, durationSeconds * 1000, minRssi);
    }

    Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt(KEY_CONFIG_VERSION, 1);
        if (uuid != null) {
            bundle.putString(KEY_UUID, uuid);
        }
        if (major != null) {
            bundle.putInt(KEY_MAJOR, major);
        }
        if (minor != null) {
            bundle.putInt(KEY_MINOR, minor);
        }
        bundle.putInt(KEY_DURATION_MS, durationMs);
        if (minRssi != null) {
            bundle.putInt(KEY_MIN_RSSI, minRssi);
        }
        return bundle;
    }

    boolean matches(IBeacon beacon) {
        if (uuid != null && !uuid.equalsIgnoreCase(beacon.uuid)) {
            return false;
        }
        if (major != null && major != beacon.major) {
            return false;
        }
        if (minor != null && minor != beacon.minor) {
            return false;
        }
        return minRssi == null || beacon.rssi >= minRssi;
    }

    String toBlurb() {
        StringBuilder builder = new StringBuilder();
        builder.append(targetSummary());
        builder.append(", ");
        builder.append(durationMs / 1000);
        builder.append("s");
        if (minRssi != null) {
            builder.append(", RSSI >= ");
            builder.append(minRssi);
        }
        return builder.toString();
    }

    String targetSummary() {
        if (uuid == null && major == null && minor == null) {
            return "Any iBeacon";
        }

        StringBuilder builder = new StringBuilder();
        builder.append(uuid == null ? "Any UUID" : uuid);
        if (major != null || minor != null) {
            builder.append(" ");
            builder.append(major == null ? "*" : major);
            builder.append("/");
            builder.append(minor == null ? "*" : minor);
        }
        return builder.toString();
    }

    int durationSeconds() {
        return durationMs / 1000;
    }

    private static Integer parseOptionalUnsigned16(String label, String value) {
        String trimmed = emptyToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(trimmed);
            if (parsed < 0 || parsed > 65535) {
                throw new IllegalArgumentException(label + " must be between 0 and 65535");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a whole number");
        }
    }

    private static int parseDurationSeconds(String value) {
        String trimmed = emptyToNull(value);
        if (trimmed == null) {
            return DEFAULT_DURATION_MS / 1000;
        }
        try {
            int parsed = Integer.parseInt(trimmed);
            if (parsed < 1 || parsed > 60) {
                throw new IllegalArgumentException("Duration must be between 1 and 60 seconds");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Duration must be a whole number");
        }
    }

    private static Integer parseOptionalRssi(String value) {
        String trimmed = emptyToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(trimmed);
            if (parsed < -127 || parsed > 20) {
                throw new IllegalArgumentException("Minimum RSSI must be between -127 and 20");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Minimum RSSI must be a whole number");
        }
    }

    private static String normalizeUuidOrNull(String value) {
        String trimmed = emptyToNull(value);
        if (trimmed == null) {
            return null;
        }
        return UUID.fromString(trimmed).toString().toLowerCase(Locale.US);
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

