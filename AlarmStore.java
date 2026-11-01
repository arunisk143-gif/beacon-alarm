package com.beacon.alarm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AlarmStore {
    private static final String PREFS = "beacon";
    private static final String KEY = "alarms";

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<Alarm> load(Context c) {
        List<Alarm> list = new ArrayList<>();
        String raw = prefs(c).getString(KEY, null);
        if (raw == null) return list;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Alarm.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static void save(Context c, List<Alarm> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Alarm a : list) arr.put(a.toJson());
            prefs(c).edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) {
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
