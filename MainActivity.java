package com.beacon.alarm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends Activity {
    private static final int BG = 0xFF0B0F15;
    private static final int SURFACE = 0xFF131923;
    private static final int FG = 0xFFE8EDF4;
    private static final int MUTED = 0xFF8B97A8;
    private static final int ACCENT = 0xFFFFB020;
    private static final int LINE = 0xFF243040;
    private static final String[] DAY_LETTERS = {"S", "M", "T", "W", "T", "F", "S"};

    private Torch torch;
    private Button torchBtn, flashBtn;
    private EditText flCount, flOn, flOff;
    private LinearLayout listBox;
    private boolean flashing;
    private List<Alarm> alarms;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(SURFACE);
        g.setCornerRadius(dp(14));
        g.setStroke(dp(1), LINE);
        p.setBackground(g);
        p.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        p.setLayoutParams(lp);
        return p;
    }

    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(primary ? 0xFF1A1100 : FG);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        g.setColor(primary ? ACCENT : LINE);
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private EditText numberField(int value) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(value));
        e.setTextColor(FG);
        e.setSingleLine(true);
        return e;
    }

    private LinearLayout labeled(String label, EditText field) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text(label, 14, MUTED);
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(field, new LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private int num(EditText e, int def) {
        try {
            return Math.max(0, Integer.parseInt(e.getText().toString().trim()));
        } catch (Exception ex) {
            return def;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        torch = new Torch(this);
        torch.setListener(new Torch.Listener() {
            @Override
            public void onChanged(boolean on) {
                updateTorchUi();
            }
        });

        final ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        sv.addView(root);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("hh:mm:ss a");
        clock.setFormat24Hour("HH:mm:ss");
        clock.setTextSize(42);
        clock.setTypeface(Typeface.MONOSPACE);
        clock.setTextColor(FG);
        clock.setGravity(Gravity.CENTER);
        root.addView(clock);
        TextView title = text("Beacon Alarm", 14, MUTED);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        // Flashlight panel
        LinearLayout lp = panel();
        lp.addView(text("FLASHLIGHT", 12, MUTED));
        torchBtn = button("Turn light on", true);
        lp.addView(torchBtn);
        SharedPreferences sp = AlarmStore.prefs(this);
        flCount = numberField(sp.getInt("fl_count", 5));
        flOn = numberField(sp.getInt("fl_on", 300));
        flOff = numberField(sp.getInt("fl_off", 300));
        lp.addView(labeled("Flashes (0 = until stopped)", flCount));
        lp.addView(labeled("Light on (ms)", flOn));
        lp.addView(labeled("Light off (ms)", flOff));
        flashBtn = button("Start flashing", false);
        lp.addView(flashBtn);
        root.addView(lp);

        // Alarms panel
        LinearLayout ap = panel();
        ap.addView(text("ALARMS", 12, MUTED));
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        ap.addView(listBox);
        Button add = button("Add alarm", true);
        ap.addView(add);
        root.addView(ap);

        TextView note = text("Alarms ring with sound and the phone light even when the app is closed. "
                + "Allow notifications, and set battery to Unrestricted if your phone kills background apps.",
                12, MUTED);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);

        sv.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets ins) {
                root.setPadding(dp(16) + ins.getSystemWindowInsetLeft(),
                        dp(24) + ins.getSystemWindowInsetTop(),
                        dp(16) + ins.getSystemWindowInsetRight(),
                        dp(32) + ins.getSystemWindowInsetBottom());
                return ins;
            }
        });
        setContentView(sv);

        torchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!torch.hasFlash()) {
                    Toast.makeText(MainActivity.this, "This phone has no flash", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (flashing) {
                    torch.stop();
                    flashing = false;
                    torch.set(true);
                    updateFlashUi();
                } else {
                    torch.set(!torch.isOn());
                }
            }
        });
        flashBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!torch.hasFlash()) {
                    Toast.makeText(MainActivity.this, "This phone has no flash", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (flashing) {
                    torch.stop();
                    flashing = false;
                    updateFlashUi();
                    return;
                }
                int c = num(flCount, 5);
                int on = Math.max(50, num(flOn, 300));
                int off = Math.max(50, num(flOff, 300));
                AlarmStore.prefs(MainActivity.this).edit()
                        .putInt("fl_count", c).putInt("fl_on", on).putInt("fl_off", off).apply();
                flashing = true;
                updateFlashUi();
                torch.flash(c, on, off, new Runnable() {
                    @Override
                    public void run() {
                        flashing = false;
                        updateFlashUi();
                    }
                });
            }
        });
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Alarm a = new Alarm();
                a.id = AlarmStore.nextId(alarms);
                showEditor(a, true);
            }
        });

        refresh();
        updateTorchUi();
        updateFlashUi();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    private void updateTorchUi() {
        torchBtn.setText(torch.isOn() ? "Turn light off" : "Turn light on");
    }

    private void updateFlashUi() {
        flashBtn.setText(flashing ? "Stop flashing" : "Start flashing");
    }

    private void refresh() {
        alarms = AlarmStore.load(this);
        Collections.sort(alarms, new Comparator<Alarm>() {
            @Override
            public int compare(Alarm a, Alarm b) {
                return (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute);
            }
        });
        listBox.removeAllViews();
        if (alarms.isEmpty()) {
            TextView t = text("No alarms yet.", 14, MUTED);
            t.setPadding(0, dp(8), 0, 0);
            listBox.addView(t);
            return;
        }
        for (final Alarm a : alarms) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView time = text(a.timeText(), 28, a.enabled ? FG : MUTED);
            time.setTypeface(Typeface.MONOSPACE);
            info.addView(time);
            info.addView(text(summary(a), 12, MUTED));
            row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Switch sw = new Switch(this);
            sw.setChecked(a.enabled);
            sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                    a.enabled = checked;
                    AlarmStore.update(MainActivity.this, a);
                    if (checked) AlarmScheduler.schedule(MainActivity.this, a);
                    else AlarmScheduler.cancel(MainActivity.this, a);
                    refresh();
                }
            });
            row.addView(sw);

            Button del = new Button(this);
            del.setText("×");
            del.setTextSize(20);
            del.setTextColor(MUTED);
            del.setBackgroundColor(0x00000000);
            del.setMinWidth(dp(44));
            del.setMinimumWidth(dp(44));
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlarmScheduler.cancel(MainActivity.this, a);
                    List<Alarm> all = AlarmStore.load(MainActivity.this);
                    for (int i = 0; i < all.size(); i++) {
                        if (all.get(i).id == a.id) {
                            all.remove(i);
                            break;
                        }
                    }
                    AlarmStore.save(MainActivity.this, all);
                    refresh();
                }
            });
            row.addView(del);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEditor(a, false);
                }
            });
            listBox.addView(row);
        }
    }

    private String summary(Alarm a) {
        StringBuilder sb = new StringBuilder();
        if (a.label != null && !a.label.isEmpty()) sb.append(a.label).append(" · ");
        if (!a.repeats()) {
            sb.append("Once");
        } else {
            String[] names = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
            int count = 0;
            StringBuilder d = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                if (a.days[i]) {
                    if (count > 0) d.append(", ");
                    d.append(names[i]);
                    count++;
                }
            }
            sb.append(count == 7 ? "Every day" : d.toString());
        }
        if (a.flash) {
            sb.append(" · light ").append(a.flashCount == 0 ? "until stopped" : a.flashCount + "x")
                    .append(" (").append(a.onMs).append("/").append(a.offMs).append(" ms)");
        }
        return sb.toString();
    }

    private void showEditor(final Alarm a, final boolean isNew) {
        final int[] hm = {a.hour, a.minute};
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(20), dp(8), dp(20), 0);

        final Button timeBtn = new Button(this);
        timeBtn.setAllCaps(false);
        timeBtn.setTextSize(28);
        timeBtn.setTypeface(Typeface.MONOSPACE);
        timeBtn.setText(String.format("%02d:%02d", hm[0], hm[1]));
        timeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new TimePickerDialog(MainActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(android.widget.TimePicker p, int h, int m) {
                        hm[0] = h;
                        hm[1] = m;
                        timeBtn.setText(String.format("%02d:%02d", h, m));
                    }
                }, hm[0], hm[1], DateFormat.is24HourFormat(MainActivity.this)).show();
            }
        });
        v.addView(timeBtn);

        final EditText label = new EditText(this);
        label.setHint("Label (optional)");
        label.setText(a.label);
        label.setSingleLine(true);
        v.addView(label);

        LinearLayout daysRow = new LinearLayout(this);
        daysRow.setOrientation(LinearLayout.HORIZONTAL);
        final CheckBox[] dayBoxes = new CheckBox[7];
        for (int i = 0; i < 7; i++) {
            CheckBox cb = new CheckBox(this);
            cb.setText(DAY_LETTERS[i]);
            cb.setChecked(a.days[i]);
            dayBoxes[i] = cb;
            daysRow.addView(cb, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        v.addView(daysRow);
        v.addView(text("No day ticked = ring once", 12, MUTED));

        final CheckBox flash = new CheckBox(this);
        flash.setText("Flash the phone light when ringing");
        flash.setChecked(a.flash);
        v.addView(flash);

        final EditText count = numberField(a.flashCount);
        final EditText on = numberField(a.onMs);
        final EditText off = numberField(a.offMs);
        v.addView(labeled("Flashes (0 = until stopped)", count));
        v.addView(labeled("Light on (ms)", on));
        v.addView(labeled("Light off (ms)", off));

        ScrollView sv = new ScrollView(this);
        sv.addView(v);

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(isNew ? "New alarm" : "Edit alarm")
                .setView(sv)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        a.hour = hm[0];
                        a.minute = hm[1];
                        a.label = label.getText().toString().trim();
                        for (int i = 0; i < 7; i++) a.days[i] = dayBoxes[i].isChecked();
                        a.flash = flash.isChecked();
                        a.flashCount = num(count, 10);
                        a.onMs = Math.max(50, num(on, 300));
                        a.offMs = Math.max(50, num(off, 300));
                        a.enabled = true;
                        List<Alarm> all = AlarmStore.load(MainActivity.this);
                        boolean found = false;
                        for (int i = 0; i < all.size(); i++) {
                            if (all.get(i).id == a.id) {
                                all.set(i, a);
                                found = true;
                                break;
                            }
                        }
                        if (!found) all.add(a);
                        AlarmStore.save(MainActivity.this, all);
                        AlarmScheduler.schedule(MainActivity.this, a);
                        long t = a.nextTrigger(System.currentTimeMillis());
                        if (t > 0) {
                            long mins = (t - System.currentTimeMillis()) / 60000L;
                            Toast.makeText(MainActivity.this, "Rings in " + (mins / 60) + " h "
                                    + (mins % 60) + " min", Toast.LENGTH_SHORT).show();
                        }
                        refresh();
                    }
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        if (torch != null) torch.setListener(null);
        super.onDestroy();
    }
}
