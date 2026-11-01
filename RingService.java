package com.beacon.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class RingService extends Service {
    public static final String ACT_STOP = "com.beacon.alarm.STOP";
    public static final String ACT_SNOOZE = "com.beacon.alarm.SNOOZE";
    public static final String BROADCAST_DONE = "com.beacon.alarm.DONE";
    private static final String CHANNEL = "ring";
    private static final int NOTIF_ID = 42;
    private static final int SNOOZE_MINUTES = 5;
    private static final long MAX_RING_MS = 2 * 60 * 1000L;

    private MediaPlayer player;
    private Torch torch;
    private Vibrator vib;
    private PowerManager.WakeLock wl;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Alarm alarm;
    private boolean ringing;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        String act = i == null ? null : i.getAction();
        if (ACT_STOP.equals(act)) {
            finishRing();
            return START_NOT_STICKY;
        }
        if (ACT_SNOOZE.equals(act)) {
            if (alarm != null) AlarmScheduler.snooze(this, alarm.id, SNOOZE_MINUTES);
            finishRing();
            return START_NOT_STICKY;
        }
        if (i == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Alarm a = AlarmStore.find(this, i.getIntExtra("id", -1));
        if (a == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ringing) return START_NOT_STICKY;
        alarm = a;
        ringing = true;
        startRing();
        return START_NOT_STICKY;
    }

    private void startRing() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Alarm", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);

        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "beacon:ring");
        wl.acquire(3 * 60 * 1000L);

        playSound();
        vibrate();
        if (alarm.flash) {
            torch = new Torch(this);
            torch.flash(alarm.flashCount, alarm.onMs, alarm.offMs, null);
        }
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                finishRing();
            }
        }, MAX_RING_MS);
    }

    private Notification buildNotification() {
        Intent ra = new Intent(this, RingActivity.class);
        ra.putExtra("id", alarm.id);
        ra.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent full = PendingIntent.getActivity(this, 1, ra,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, RingService.class).setAction(ACT_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent snooze = PendingIntent.getService(this, 3,
                new Intent(this, RingService.class).setAction(ACT_SNOOZE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = alarm.label == null || alarm.label.isEmpty() ? "Alarm" : alarm.label;
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(alarm.timeText())
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setContentIntent(full)
                .setFullScreenIntent(full, true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                        "Stop", stop).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, android.R.drawable.ic_menu_recent_history),
                        "Snooze " + SNOOZE_MINUTES + " min", snooze).build())
                .build();
    }

    private void playSound() {
        try {
            Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, u);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception ignored) {
        }
    }

    private void vibrate() {
        try {
            vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 700, 500}, 0));
        } catch (Exception ignored) {
        }
    }

    private void cleanup() {
        h.removeCallbacksAndMessages(null);
        try {
            if (player != null) {
                player.stop();
                player.release();
            }
        } catch (Exception ignored) {
        }
        player = null;
        try {
            if (vib != null) vib.cancel();
        } catch (Exception ignored) {
        }
        if (torch != null) {
            torch.release();
            torch = null;
        }
        try {
            if (wl != null && wl.isHeld()) wl.release();
        } catch (Exception ignored) {
        }
    }

    private void finishRing() {
        cleanup();
        ringing = false;
        sendBroadcast(new Intent(BROADCAST_DONE).setPackage(getPackageName()));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }
}
