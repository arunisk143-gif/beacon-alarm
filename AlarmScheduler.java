package com.beacon.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class AlarmScheduler {

    private static PendingIntent ringIntent(Context c, int id, boolean snooze) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.putExtra("id", id);
        i.putExtra("snooze", snooze);
        return PendingIntent.getBroadcast(c, (snooze ? 100000 : 0) + id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void setAt(Context c, int id, long when, boolean snooze) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(when, show), ringIntent(c, id, snooze));
    }

    public static void schedule(Context c, Alarm a) {
        cancel(c, a);
        if (!a.enabled) return;
        long t = a.nextTrigger(System.currentTimeMillis());
        if (t > 0) setAt(c, a.id, t, false);
    }

    public static void cancel(Context c, Alarm a) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(ringIntent(c, a.id, false));
        am.cancel(ringIntent(c, a.id, true));
    }

    public static void snooze(Context c, int id, int minutes) {
        setAt(c, id, System.currentTimeMillis() + minutes * 60000L, true);
    }
}
