package com.beacon.alarm;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

public class Alarm {
    public int id;
    public int hour = 7;
    public int minute = 0;
    public String label = "";
    public boolean enabled = true;
    public boolean flash = true;
    /** Number of light flashes when ringing. 0 means keep flashing until stopped. */
    public int flashCount = 10;
    public int onMs = 300;
    public int offMs = 300;
    /** Index 0 = Sunday ... 6 = Saturday. All false means ring once. */
    public boolean[] days = new boolean[7];

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("hour", hour);
        o.put("minute", minute);
        o.put("label", label);
        o.put("enabled", enabled);
        o.put("flash", flash);
        o.put("flashCount", flashCount);
        o.put("onMs", onMs);
        o.put("offMs", offMs);
        JSONArray d = new JSONArray();
        for (boolean b : days) d.put(b);
        o.put("days", d);
        return o;
    }

    public static Alarm fromJson(JSONObject o) throws JSONException {
        Alarm a = new Alarm();
        a.id = o.getInt("id");
        a.hour = o.getInt("hour");
        a.minute = o.getInt("minute");
        a.label = o.optString("label", "");
        a.enabled = o.optBoolean("enabled", true);
        a.flash = o.optBoolean("flash", true);
        a.flashCount = o.optInt("flashCount", 10);
        a.onMs = o.optInt("onMs", 300);
        a.offMs = o.optInt("offMs", 300);
        JSONArray d = o.optJSONArray("days");
        if (d != null) {
            for (int i = 0; i < 7 && i < d.length(); i++) a.days[i] = d.getBoolean(i);
        }
        return a;
    }

    public boolean repeats() {
        for (boolean d : days) if (d) return true;
        return false;
    }

    public String timeText() {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }

    /** Next time this alarm should ring after "now", or -1 if none. */
    public long nextTrigger(long now) {
        for (int add = 0; add < 8; add++) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(now);
            c.add(Calendar.DAY_OF_YEAR, add);
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (c.getTimeInMillis() <= now) continue;
            int dow = c.get(Calendar.DAY_OF_WEEK) - 1;
            if (!repeats() || days[dow]) return c.getTimeInMillis();
        }
        return -1;
    }
}
