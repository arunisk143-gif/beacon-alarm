package com.beacon.alarm;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class RingActivity extends Activity {
    private final BroadcastReceiver doneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            finish();
        }
    };

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setTextColor(primary ? 0xFF1A1100 : 0xFFE8EDF4);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(primary ? 0xFFFFB020 : 0xFF243040);
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(0xFF0B0F15);
        getWindow().setNavigationBarColor(0xFF0B0F15);

        Alarm a = AlarmStore.find(this, getIntent().getIntExtra("id", -1));

        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0F15);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER);
        root.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView time = new TextView(this);
        time.setText(a == null ? "Alarm" : a.timeText());
        time.setTextSize(64);
        time.setTypeface(Typeface.MONOSPACE);
        time.setTextColor(Color.WHITE);
        time.setGravity(Gravity.CENTER);
        top.addView(time);

        TextView label = new TextView(this);
        label.setText(a == null || a.label.isEmpty() ? "Time to wake up" : a.label);
        label.setTextSize(22);
        label.setTextColor(0xFFFFB020);
        label.setGravity(Gravity.CENTER);
        top.addView(label);

        Button snooze = button("Snooze 5 min", true);
        Button stop = button("Stop", false);
        root.addView(snooze);
        root.addView(stop);

        snooze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(RingActivity.this, RingService.class)
                        .setAction(RingService.ACT_SNOOZE));
                finish();
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(RingActivity.this, RingService.class)
                        .setAction(RingService.ACT_STOP));
                finish();
            }
        });

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets ins) {
                v.setPadding(dp(24) + ins.getSystemWindowInsetLeft(),
                        dp(24) + ins.getSystemWindowInsetTop(),
                        dp(24) + ins.getSystemWindowInsetRight(),
                        dp(24) + ins.getSystemWindowInsetBottom());
                return ins;
            }
        });
        setContentView(root);

        registerReceiver(doneReceiver, new IntentFilter(RingService.BROADCAST_DONE),
                Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(doneReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
