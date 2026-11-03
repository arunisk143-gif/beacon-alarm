package com.beacon.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Re-schedules every enabled alarm after a reboot, and after the time zone or the
 * system clock changes (alarms are stored as absolute times, so they would otherwise
 * ring at the wrong local time).
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String act = i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(act)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(act)
                && !Intent.ACTION_TIME_CHANGED.equals(act)) {
            return;
        }
        for (Alarm a : AlarmStore.load(c)) {
            if (a.enabled) AlarmScheduler.schedule(c, a);
        }
    }
}
