package com.beacon.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int id = i.getIntExtra("id", -1);
        boolean snooze = i.getBooleanExtra("snooze", false);
        Alarm a = AlarmStore.find(c, id);
        if (a == null) return;

        if (!snooze) {
            if (a.repeats()) {
                AlarmScheduler.schedule(c, a);
            } else {
                a.enabled = false;
                AlarmStore.update(c, a);
            }
        }

        Intent s = new Intent(c, RingService.class);
        s.putExtra("id", id);
        c.startForegroundService(s);
    }
}
