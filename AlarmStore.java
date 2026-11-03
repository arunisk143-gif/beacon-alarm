package com.beacon.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class AlarmStore {
    private static final String PREFS = "beacon";
    private static final String KEY = "alarms";
    private static final String KEY_BACKUP = "alarms_backup";
    private static final String TAG = "BeaconAlarmStore";

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<Alarm> load(Context c) {
        List<Alarm> list = new ArrayList<>();
        String raw = prefs(c).getString(KEY, null);
        if (raw == null) return list;
        JSONArray arr;
        try {
            arr = new JSONArray(raw);
        } catch (Exception e) {
            // Unreadable data: keep a copy so the next save() can't destroy it.
            Log.e(TAG, "Could not parse saved alarms", e);
            backup(c, raw);
            return list;
        }
        boolean skipped = false;
        for (int i = 0; i < arr.length(); i++) {
            try {
                list.add(Alarm.fromJson(arr.getJSONObject(i)));
            } catch (Exception e) {
                // One bad entry must not hide all the others.
                Log.e(TAG, "Skipping unreadable alarm at index " + i, e);
                skipped = true;
            }
        }
        if (skipped) backup(c, raw);
        return list;
    }

    private static void backup(Context c, String raw) {
        SharedPreferences p = prefs(c);
        // Never overwrite an earlier backup; the first copy is the least damaged.
        if (!p.contains(KEY_BACKUP)) p.edit().putString(KEY_BACKUP, raw).apply();
    }

    public static void save(Context c, List<Alarm> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Alarm a : list) arr.put(a.toJson());
            prefs(c).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Could not save alarms", e);
        }
    }

    public static Alarm find(Context c, int id) {
        for (Alarm a : load(c)) if (a.id == id) return a;
        return null;
    }

    public static void update(Context c, Alarm updated) {
        List<Alarm> list = load(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == updated.id) {
                list.set(i, updated);
                save(c, list);
                return;
            }
        }
    }

    public static int nextId(List<Alarm> list) {
        int max = 0;
        for (Alarm a : list) if (a.id > max) max = a.id;
        return max + 1;
    }
}
