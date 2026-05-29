package io.github.smailzhu.ibeacontasker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class BeaconCache {
    static final long TASKER_MAX_AGE_MS = 30000;

    private static final String PREFS = "beacon_cache";
    private static final String KEY_BEACONS = "beacons";
    private static final long PRUNE_MAX_AGE_MS = 300000;

    static void record(Context context, IBeacon beacon) {
        synchronized (BeaconCache.class) {
            long now = System.currentTimeMillis();
            JSONArray existing = readBeacons(context);
            JSONArray next = new JSONArray();
            String beaconKey = beaconKey(beacon);
            boolean added = false;

            for (int index = 0; index < existing.length(); index++) {
                JSONObject item = existing.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                IBeacon existingBeacon = fromJson(item);
                if (existingBeacon == null || now - existingBeacon.timestampMillis > PRUNE_MAX_AGE_MS) {
                    continue;
                }
                if (beaconKey.equals(beaconKey(existingBeacon))) {
                    put(next, beacon);
                    added = true;
                } else {
                    next.put(item);
                }
            }

            if (!added) {
                put(next, beacon);
            }
            prefs(context).edit().putString(KEY_BEACONS, next.toString()).apply();
        }
    }

    static IBeacon bestMatch(Context context, ScanConfig config, long maxAgeMs) {
        long now = System.currentTimeMillis();
        IBeacon bestBeacon = null;
        JSONArray beacons = readBeacons(context);
        for (int index = 0; index < beacons.length(); index++) {
            IBeacon beacon = fromJson(beacons.optJSONObject(index));
            if (beacon == null
                    || now - beacon.timestampMillis > maxAgeMs
                    || !config.matches(beacon)) {
                continue;
            }
            if (bestBeacon == null || beacon.rssi > bestBeacon.rssi) {
                bestBeacon = beacon;
            }
        }
        return bestBeacon;
    }

    static int recentCount(Context context) {
        long now = System.currentTimeMillis();
        int count = 0;
        JSONArray beacons = readBeacons(context);
        for (int index = 0; index < beacons.length(); index++) {
            IBeacon beacon = fromJson(beacons.optJSONObject(index));
            if (beacon != null && now - beacon.timestampMillis <= TASKER_MAX_AGE_MS) {
                count++;
            }
        }
        return count;
    }

    private static JSONArray readBeacons(Context context) {
        String value = prefs(context).getString(KEY_BEACONS, "[]");
        try {
            return new JSONArray(value);
        } catch (JSONException ex) {
            return new JSONArray();
        }
    }

    private static void put(JSONArray array, IBeacon beacon) {
        try {
            JSONObject object = new JSONObject()
                    .put("uuid", beacon.uuid)
                    .put("major", beacon.major)
                    .put("minor", beacon.minor)
                    .put("txPower", beacon.txPower)
                    .put("rssi", beacon.rssi)
                    .put("timestampMillis", beacon.timestampMillis);
            if (beacon.address != null) {
                object.put("address", beacon.address);
            }
            if (beacon.name != null) {
                object.put("name", beacon.name);
            }
            array.put(object);
        } catch (JSONException ignored) {
        }
    }

    private static IBeacon fromJson(JSONObject object) {
        if (object == null) {
            return null;
        }
        String uuid = object.optString("uuid", null);
        if (uuid == null) {
            return null;
        }
        return new IBeacon(
                uuid,
                object.optInt("major"),
                object.optInt("minor"),
                object.optInt("txPower"),
                object.optInt("rssi"),
                object.optString("address", null),
                object.optString("name", null),
                object.optLong("timestampMillis"));
    }

    private static String beaconKey(IBeacon beacon) {
        return beacon.uuid + "/" + beacon.major + "/" + beacon.minor;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private BeaconCache() {
    }
}
