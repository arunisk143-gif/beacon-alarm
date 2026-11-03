package com.beacon.alarm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_RINGTONE = 11;
    private static final int REQ_FILE = 12;
    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;
    /** Days are stored Sunday = 0. The screen shows Monday first. */
    private static final int[] DAY_ORDER = {1, 2, 3, 4, 5, 6, 0};
    private static final String[] DAY_LETTERS = {"M", "T", "W", "T", "F", "S", "S"};
    private static final String[] DAY_NAMES = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    private static final String[] ICON_ALIASES = {"IconClassic", "IconBell", "IconMoon", "IconSun"};
    private static final String[] ICON_LABELS = {"Classic", "Bell", "Moon", "Sunrise"};
    private static final int[] ICON_FG = {R.drawable.ic_launcher_foreground, R.drawable.ic_bell_foreground,
            R.drawable.ic_moon_foreground, R.drawable.ic_sun_foreground};
    private static final int[] ICON_BG = {0xFF161D29, 0xFF2B1824, 0xFF171227, 0xFF112B24};

    private AppTheme t;
    private Torch torch;
    private TextView torchBtn;
    private TextView nextChip;
    private Button flashBtn;
    private EditText flCount, flOn, flOff;
    private LinearLayout listBox;
    private boolean flashing;
    private List<Alarm> alarms;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateNext();
            handler.postDelayed(this, 20000);
        }
    };

    // state of the alarm editor that is currently open
    private String editSound = "";
    private String editSoundName = "";
    private int editAlarmId;
    private TextView soundLabel;

    // ---------- small view helpers ----------

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams lp(int w, int h, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(topDp);
        return p;
    }

    private GradientDrawable shape(int fill, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView tv(String s, int sp, int color) {
        TextView x = new TextView(this);
        x.setText(s);
        x.setTextSize(sp);
        x.setTextColor(color);
        return x;
    }

    private TextView caps(String s) {
        TextView x = tv(s.toUpperCase(Locale.ROOT), 12, t.muted);
        x.setLetterSpacing(0.12f);
        x.setTypeface(Typeface.DEFAULT_BOLD);
        return x;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(shape(t.surface, 22, t.line));
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        return c;
    }

    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? t.accentFg : t.fg);
        b.setBackground(shape(primary ? t.accent : t.chip, 16, 0));
        b.setStateListAnimator(null);
        return b;
    }

    private TextView chipView(String s) {
        TextView c = tv(s, 14, t.fg);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(16), dp(8), dp(16), dp(8));
        c.setBackground(shape(t.chip, 14, 0));
        return c;
    }

    private EditText field(String text, int inputType, String hint) {
        EditText e = new EditText(this);
        e.setInputType(inputType);
        e.setText(text);
        e.setHint(hint);
        e.setTextColor(t.fg);
        e.setHintTextColor(t.muted);
        e.setTextSize(16);
        e.setSingleLine(true);
        e.setBackground(shape(t.bg, 12, t.line));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        return e;
    }

    private EditText numberField(int value) {
        return field(String.valueOf(value), InputType.TYPE_CLASS_NUMBER, "");
    }

    private LinearLayout labeled(String label, EditText field) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(tv(label, 14, t.muted), new LinearLayout.LayoutParams(0, WRAP, 1f));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(110), WRAP);
        p.leftMargin = dp(12);
        row.addView(field, p);
        return row;
    }

    private int num(EditText e, int def) {
        try {
            return Math.max(0, Integer.parseInt(e.getText().toString().trim()));
        } catch (Exception ex) {
            return def;
        }
    }

    private ColorStateList states(int on, int off) {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{on, off});
    }

    private int dialogTheme() {
        return AppTheme.isDark(t.bg)
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
    }

    /** A dialog with no frame of its own: the content view draws the rounded card. */
    private AlertDialog dialogFor(View content) {
        ScrollView sv = new ScrollView(this);
        sv.addView(content);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(12), dp(12), dp(12), dp(12));
        wrap.addView(sv);
        AlertDialog d = new AlertDialog.Builder(this).setView(wrap).create();
        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        return d;
    }

    private void showCard(AlertDialog d) {
        d.show();
        d.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }

    private LinearLayout dialogBody() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(shape(t.surface, 26, t.line));
        v.setPadding(dp(20), dp(20), dp(20), dp(20));
        return v;
    }

    // ---------- screen ----------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        t = AppTheme.load(this);
        getWindow().setStatusBarColor(t.bg);
        getWindow().setNavigationBarColor(t.bg);
        if (!AppTheme.isDark(t.bg)) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        torch = new Torch(this);
        torch.setListener(new Torch.Listener() {
            @Override
            public void onChanged(boolean on) {
                updateTorchUi();
            }
        });

        final ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(t.bg);
        sv.setFillViewport(true);
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        sv.addView(root);

        // header
        TextView brand = tv("MY ALARM", 13, t.accent);
        brand.setLetterSpacing(0.3f);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand, lp(MATCH, WRAP, 0));

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("hh:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(84);
        clock.setTextColor(t.fg);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        timeRow.addView(clock);
        if (!DateFormat.is24HourFormat(this)) {
            TextClock ampm = new TextClock(this);
            ampm.setFormat12Hour("a");
            ampm.setFormat24Hour("a");
            ampm.setTextSize(20);
            ampm.setTextColor(t.muted);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(WRAP, WRAP);
            ap.leftMargin = dp(8);
            ap.bottomMargin = dp(14);
            timeRow.addView(ampm, ap);
        }
        root.addView(timeRow, lp(MATCH, WRAP, 8));

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(15);
        date.setTextColor(t.muted);
        date.setGravity(Gravity.CENTER);
        root.addView(date, lp(MATCH, WRAP, 4));

        nextChip = tv("", 14, t.accent);
        nextChip.setTypeface(Typeface.DEFAULT_BOLD);
        nextChip.setGravity(Gravity.CENTER);
        nextChip.setPadding(dp(16), dp(8), dp(16), dp(8));
        nextChip.setBackground(shape(AppTheme.mix(t.bg, t.accent, 0.16f), 20, 0));
        LinearLayout.LayoutParams np = lp(WRAP, WRAP, 14);
        np.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(nextChip, np);

        // flashlight card
        LinearLayout lc = card();
        lc.addView(caps("Flashlight"));
        torchBtn = tv("", 18, t.fg);
        torchBtn.setGravity(Gravity.CENTER);
        torchBtn.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(128), dp(128));
        tp.gravity = Gravity.CENTER_HORIZONTAL;
        tp.topMargin = dp(14);
        lc.addView(torchBtn, tp);
        TextView hint = tv("Uses the LED on the back of your phone", 12, t.muted);
        hint.setGravity(Gravity.CENTER);
        lc.addView(hint, lp(MATCH, WRAP, 10));

        SharedPreferences sp = AlarmStore.prefs(this);
        flCount = numberField(sp.getInt("fl_count", 5));
        flOn = numberField(sp.getInt("fl_on", 300));
        flOff = numberField(sp.getInt("fl_off", 300));

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setGravity(Gravity.CENTER_HORIZONTAL);
        presets.addView(speedChip("Slow", 600, 600));
        presets.addView(speedChip("Normal", 300, 300));
        presets.addView(speedChip("Fast", 120, 120));
        lc.addView(presets, lp(MATCH, WRAP, 16));

        lc.addView(labeled("Flashes (0 = until stopped)", flCount), lp(MATCH, WRAP, 12));
        lc.addView(labeled("Light on (ms)", flOn), lp(MATCH, WRAP, 8));
        lc.addView(labeled("Light off (ms)", flOff), lp(MATCH, WRAP, 8));
        flashBtn = button("Start flashing", false);
        lc.addView(flashBtn, lp(MATCH, dp(52), 14));
        root.addView(lc, lp(MATCH, WRAP, 18));

        // alarms card
        LinearLayout ac = card();
        ac.addView(caps("Alarms"));
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        ac.addView(listBox, lp(MATCH, WRAP, 4));
        Button add = button("Add alarm", true);
        ac.addView(add, lp(MATCH, dp(52), 12));
        root.addView(ac, lp(MATCH, WRAP, 14));

        // look card
        root.addView(buildLookCard(), lp(MATCH, WRAP, 14));

        TextView note = tv("Alarms ring with sound and the phone light even when the app is closed. "
                + "Allow notifications, and set battery to Unrestricted if your phone stops background apps.",
                12, t.muted);
        root.addView(note, lp(MATCH, WRAP, 16));

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

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private TextView speedChip(String label, final int on, final int off) {
        TextView c = chipView(label);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(WRAP, WRAP);
        p.leftMargin = dp(4);
        p.rightMargin = dp(4);
        c.setLayoutParams(p);
        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                flOn.setText(String.valueOf(on));
                flOff.setText(String.valueOf(off));
            }
        });
        return c;
    }

    private void updateTorchUi() {
        boolean on = torch.isOn();
        torchBtn.setText(on ? "Light\nON" : "Light\nOFF");
        torchBtn.setTextColor(on ? t.accentFg : t.fg);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? t.accent : t.chip);
        g.setStroke(dp(3), t.accent);
        torchBtn.setBackground(g);
    }

    private void updateFlashUi() {
        flashBtn.setText(flashing ? "Stop flashing" : "Start flashing");
    }

    private void updateNext() {
        if (alarms == null) return;
        long now = System.currentTimeMillis();
        long best = -1;
        Alarm which = null;
        for (Alarm a : alarms) {
            if (!a.enabled) continue;
            long tr = a.nextTrigger(now);
            if (tr > 0 && (best < 0 || tr < best)) {
                best = tr;
                which = a;
            }
        }
        if (which == null) {
            nextChip.setText("No alarm set");
        } else {
            long mins = Math.max(0, (best - now) / 60000L);
            String in = (mins / 60 > 0 ? (mins / 60) + " h " : "") + (mins % 60) + " min";
            nextChip.setText("Next alarm in " + in + "  ·  " + which.timeText());
        }
    }

    // ---------- alarm list ----------

    private void refresh() {
        alarms = AlarmStore.load(this);
        Collections.sort(alarms, new Comparator<Alarm>() {
            @Override
            public int compare(Alarm a, Alarm b) {
                return (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute);
            }
        });
        listBox.removeAllViews();
        updateNext();
        if (alarms.isEmpty()) {
            TextView e = tv("No alarms yet. Tap Add alarm to make one.", 14, t.muted);
            e.setPadding(0, dp(12), 0, dp(4));
            listBox.addView(e);
            return;
        }
        for (int i = 0; i < alarms.size(); i++) {
            final Alarm a = alarms.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(14), 0, dp(14));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setAlpha(a.enabled ? 1f : 0.5f);
            TextView time = tv(a.timeText(), 36, t.fg);
            time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            info.addView(time);
            TextView days = tv("", 13, t.muted);
            days.setText(dayLine(a));
            info.addView(days, lp(WRAP, WRAP, 2));
            String meta = metaText(a);
            if (!meta.isEmpty()) info.addView(tv(meta, 12, t.muted), lp(WRAP, WRAP, 2));
            row.addView(info, new LinearLayout.LayoutParams(0, WRAP, 1f));

            Switch sw = new Switch(this);
            sw.setChecked(a.enabled);
            sw.setThumbTintList(states(t.accent, t.muted));
            sw.setTrackTintList(states(AppTheme.mix(t.accent, t.surface, 0.55f), t.line));
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton b, boolean checked) {
                    a.enabled = checked;
                    AlarmStore.update(MainActivity.this, a);
                    if (checked) AlarmScheduler.schedule(MainActivity.this, a);
                    else AlarmScheduler.cancel(MainActivity.this, a);
                    refresh();
                }
            });
            row.addView(sw);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEditor(a, false);
                }
            });
            listBox.addView(row, lp(MATCH, WRAP, 0));

            if (i < alarms.size() - 1) {
                View line = new View(this);
                line.setBackgroundColor(t.line);
                listBox.addView(line, new LinearLayout.LayoutParams(MATCH, dp(1)));
            }
        }
    }

    private CharSequence dayLine(Alarm a) {
        if (!a.repeats()) return "Once";
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < 7; i++) {
            int s = sb.length();
            sb.append(DAY_LETTERS[i]);
            boolean on = a.days[DAY_ORDER[i]];
            sb.setSpan(new ForegroundColorSpan(on ? t.accent : AppTheme.mix(t.muted, t.surface, 0.55f)),
                    s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (on) {
                sb.setSpan(new StyleSpan(Typeface.BOLD), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (i < 6) sb.append("   ");
        }
        return sb;
    }

    private String metaText(Alarm a) {
        StringBuilder sb = new StringBuilder();
        if (a.label != null && !a.label.isEmpty()) sb.append(a.label);
        if (a.flash) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("Light ").append(a.flashCount == 0 ? "until stopped" : a.flashCount + "×");
        }
        if (sb.length() > 0) sb.append(" · ");
        sb.append("Snooze ").append(a.snoozeMin).append(" min");
        if (a.soundName != null && !a.soundName.isEmpty()) {
            sb.append(" · ").append(a.soundName);
        }
        return sb.toString();
    }

    // ---------- alarm editor ----------

    private void styleDay(TextView c, boolean on) {
        c.setBackground(shape(on ? t.accent : t.chip, 20, 0));
        c.setTextColor(on ? t.accentFg : t.muted);
    }

    private void updateSoundLabel() {
        if (soundLabel == null) return;
        soundLabel.setText(editSoundName == null || editSoundName.isEmpty()
                ? "Default alarm sound" : editSoundName);
    }

    private void showEditor(final Alarm a, final boolean isNew) {
        final int[] hm = {a.hour, a.minute};
        final boolean[] ds = a.days.clone();
        editAlarmId = a.id;
        editSound = a.sound == null ? "" : a.sound;
        editSoundName = a.soundName == null ? "" : a.soundName;

        LinearLayout v = dialogBody();
        final AlertDialog d = dialogFor(v);

        TextView title = tv(isNew ? "New alarm" : "Edit alarm", 20, t.fg);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        v.addView(title);

        final TextView timeBtn = tv(String.format(Locale.US, "%02d:%02d", hm[0], hm[1]), 52, t.fg);
        timeBtn.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        timeBtn.setGravity(Gravity.CENTER);
        timeBtn.setPadding(0, dp(10), 0, dp(10));
        timeBtn.setBackground(shape(t.chip, 18, 0));
        timeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new TimePickerDialog(MainActivity.this, dialogTheme(),
                        new TimePickerDialog.OnTimeSetListener() {
                            @Override
                            public void onTimeSet(android.widget.TimePicker p, int h, int m) {
                                hm[0] = h;
                                hm[1] = m;
                                timeBtn.setText(String.format(Locale.US, "%02d:%02d", h, m));
                            }
                        }, hm[0], hm[1], DateFormat.is24HourFormat(MainActivity.this)).show();
            }
        });
        v.addView(timeBtn, lp(MATCH, WRAP, 14));

        final EditText label = field(a.label, InputType.TYPE_CLASS_TEXT, "Label (optional)");
        v.addView(label, lp(MATCH, WRAP, 12));

        LinearLayout daysRow = new LinearLayout(this);
        daysRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 7; i++) {
            final int idx = DAY_ORDER[i];
            final TextView chip = tv(DAY_LETTERS[i], 14, t.fg);
            chip.setGravity(Gravity.CENTER);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            styleDay(chip, ds[idx]);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ds[idx] = !ds[idx];
                    styleDay(chip, ds[idx]);
                }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            cp.leftMargin = dp(2);
            cp.rightMargin = dp(2);
            daysRow.addView(chip, cp);
        }
        v.addView(daysRow, lp(MATCH, WRAP, 12));
        v.addView(tv("No day selected means it rings once.", 12, t.muted), lp(MATCH, WRAP, 6));

        v.addView(caps("Sound"), lp(MATCH, WRAP, 18));
        LinearLayout soundRow = new LinearLayout(this);
        soundRow.setOrientation(LinearLayout.HORIZONTAL);
        soundRow.setGravity(Gravity.CENTER_VERTICAL);
        soundLabel = tv("", 15, t.fg);
        soundLabel.setMaxLines(1);
        soundLabel.setEllipsize(TextUtils.TruncateAt.END);
        updateSoundLabel();
        soundRow.addView(soundLabel, new LinearLayout.LayoutParams(0, WRAP, 1f));
        Button change = button("Change", false);
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(WRAP, dp(44));
        chp.leftMargin = dp(12);
        soundRow.addView(change, chp);
        v.addView(soundRow, lp(MATCH, WRAP, 8));
        change.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chooseSound();
            }
        });

        v.addView(caps("Phone light"), lp(MATCH, WRAP, 18));
        final CheckBox flash = new CheckBox(this);
        flash.setText("Flash the light when ringing");
        flash.setTextColor(t.fg);
        flash.setChecked(a.flash);
        flash.setButtonTintList(states(t.accent, t.muted));
        v.addView(flash, lp(MATCH, WRAP, 4));
        final EditText count = numberField(a.flashCount);
        final EditText on = numberField(a.onMs);
        final EditText off = numberField(a.offMs);
        v.addView(labeled("Flashes (0 = until stopped)", count), lp(MATCH, WRAP, 8));
        v.addView(labeled("Light on (ms)", on), lp(MATCH, WRAP, 8));
        v.addView(labeled("Light off (ms)", off), lp(MATCH, WRAP, 8));

        v.addView(caps("Snooze"), lp(MATCH, WRAP, 18));
        final EditText snooze = numberField(a.snoozeMin);
        v.addView(labeled("Snooze time (minutes)", snooze), lp(MATCH, WRAP, 8));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        if (!isNew) {
            Button del = button("Delete", false);
            del.setTextColor(0xFFFF6B60);
            LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(0, dp(52), 1f);
            dp1.rightMargin = dp(6);
            btns.addView(del, dp1);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    AlarmScheduler.cancel(MainActivity.this, a);
                    List<Alarm> all = AlarmStore.load(MainActivity.this);
                    for (int i = 0; i < all.size(); i++) {
                        if (all.get(i).id == a.id) {
                            all.remove(i);
                            break;
                        }
                    }
                    AlarmStore.save(MainActivity.this, all);
                    deleteSoundFile(a.sound);
                    d.dismiss();
                    refresh();
                }
            });
        }
        Button cancel = button("Cancel", false);
        LinearLayout.LayoutParams cap = new LinearLayout.LayoutParams(0, dp(52), 1f);
        cap.leftMargin = dp(6);
        cap.rightMargin = dp(6);
        btns.addView(cancel, cap);
        Button save = button("Save", true);
        LinearLayout.LayoutParams sap = new LinearLayout.LayoutParams(0, dp(52), 1f);
        sap.leftMargin = dp(6);
        btns.addView(save, sap);
        v.addView(btns, lp(MATCH, WRAP, 22));

        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                d.dismiss();
            }
        });
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                a.hour = hm[0];
                a.minute = hm[1];
                a.label = label.getText().toString().trim();
                a.days = ds;
                a.flash = flash.isChecked();
                a.flashCount = num(count, 10);
                a.onMs = Math.max(50, num(on, 300));
                a.offMs = Math.max(50, num(off, 300));
                a.snoozeMin = Math.min(60, Math.max(1, num(snooze, 5)));
                if (a.sound != null && !a.sound.equals(editSound)) deleteSoundFile(a.sound);
                a.sound = editSound;
                a.soundName = editSoundName;
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
                long tr = a.nextTrigger(System.currentTimeMillis());
                if (tr > 0) {
                    long mins = (tr - System.currentTimeMillis()) / 60000L;
                    Toast.makeText(MainActivity.this, "Rings in " + (mins / 60) + " h "
                            + (mins % 60) + " min", Toast.LENGTH_SHORT).show();
                }
                d.dismiss();
                refresh();
            }
        });

        showCard(d);
    }

    // ---------- sound choice ----------

    private void chooseSound() {
        final String[] items = {"Default alarm sound", "Phone ringtones and alarms", "Pick an audio file"};
        new AlertDialog.Builder(this, dialogTheme())
                .setTitle("Alarm sound")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            editSound = "";
                            editSoundName = "";
                            updateSoundLabel();
                        } else if (which == 1) {
                            Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                            i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
                            i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                            i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                            startActivityForResult(i, REQ_RINGTONE);
                        } else {
                            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                            i.setType("audio/*");
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            startActivityForResult(Intent.createChooser(i, "Choose alarm sound"), REQ_FILE);
                        }
                    }
                })
                .show();
    }

    private String titleOf(Uri u) {
        try {
            android.media.Ringtone r = RingtoneManager.getRingtone(this, u);
            if (r != null) return r.getTitle(this);
        } catch (Exception ignored) {
        }
        return "Selected sound";
    }

    private String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) {
        }
        return "Custom sound";
    }

    private void deleteSoundFile(String uri) {
        try {
            if (uri == null || !uri.startsWith("file:")) return;
            File f = new File(Uri.parse(uri).getPath());
            if (f.getParentFile() != null && f.getParentFile().equals(getFilesDir())) f.delete();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_RINGTONE) {
            Uri u = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (u == null) {
                editSound = "";
                editSoundName = "";
            } else {
                editSound = u.toString();
                editSoundName = titleOf(u);
            }
            updateSoundLabel();
        } else if (req == REQ_FILE) {
            final Uri src = data.getData();
            if (src == null) return;
            final String name = displayName(src);
            final int id = editAlarmId;
            Toast.makeText(this, "Loading sound...", Toast.LENGTH_SHORT).show();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final File out = new File(getFilesDir(),
                            "sound_" + id + "_" + System.currentTimeMillis() + ".audio");
                    boolean ok = false;
                    try (InputStream in = getContentResolver().openInputStream(src);
                         FileOutputStream os = new FileOutputStream(out)) {
                        byte[] buf = new byte[16384];
                        long total = 0;
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            total += n;
                            if (total > 25L * 1024 * 1024) throw new IOException("too large");
                            os.write(buf, 0, n);
                        }
                        ok = total > 0;
                    } catch (Exception e) {
                        ok = false;
                    }
                    final boolean good = ok;
                    if (!good) out.delete();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (good) {
                                editSound = Uri.fromFile(out).toString();
                                editSoundName = name;
                                updateSoundLabel();
                            } else {
                                Toast.makeText(MainActivity.this, "Could not use that file",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }).start();
        }
    }

    // ---------- look and theme ----------

    private LinearLayout buildLookCard() {
        LinearLayout c = card();
        c.addView(caps("Look"));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout rowS = new LinearLayout(this);
        rowS.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(rowS);
        for (final String name : AppTheme.PRESETS) {
            AppTheme p = AppTheme.byName(this, name);
            boolean selected = name.equals(t.name);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);

            TextView circle = tv(selected ? "✓" : ("Custom".equals(name) ? "+" : ""), 18, p.accent);
            circle.setGravity(Gravity.CENTER);
            circle.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(p.bg);
            g.setStroke(dp(4), p.accent);
            circle.setBackground(g);
            item.addView(circle, new LinearLayout.LayoutParams(dp(52), dp(52)));

            TextView nm = tv(name, 11, selected ? t.fg : t.muted);
            nm.setGravity(Gravity.CENTER);
            if (selected) nm.setTypeface(Typeface.DEFAULT_BOLD);
            item.addView(nm, lp(WRAP, WRAP, 6));

            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(WRAP, WRAP);
            ip.rightMargin = dp(14);
            rowS.addView(item, ip);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if ("Custom".equals(name)) {
                        showCustomTheme();
                    } else {
                        AppTheme.save(MainActivity.this, name);
                        if (flashing) torch.stop();
                        recreate();
                    }
                }
            });
        }
        c.addView(hs, lp(MATCH, WRAP, 14));
        c.addView(caps("App icon"), lp(MATCH, WRAP, 20));
        c.addView(buildIconRow(), lp(MATCH, WRAP, 12));
        return c;
    }

    private LinearLayout buildIconRow() {
        int current = AlarmStore.prefs(this).getInt("icon", 0);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < ICON_ALIASES.length; i++) {
            final int which = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);

            FrameLayout frame = new FrameLayout(this);
            GradientDrawable g = shape(ICON_BG[i], 16, 0);
            if (i == current) g.setStroke(dp(3), t.accent);
            frame.setBackground(g);
            ImageView img = new ImageView(this);
            img.setImageResource(ICON_FG[i]);
            img.setScaleX(1.4f);
            img.setScaleY(1.4f);
            frame.addView(img, new FrameLayout.LayoutParams(MATCH, MATCH));
            item.addView(frame, new LinearLayout.LayoutParams(dp(60), dp(60)));

            TextView nm = tv(ICON_LABELS[i], 11, i == current ? t.fg : t.muted);
            nm.setGravity(Gravity.CENTER);
            if (i == current) nm.setTypeface(Typeface.DEFAULT_BOLD);
            item.addView(nm, lp(WRAP, WRAP, 6));

            row.addView(item, new LinearLayout.LayoutParams(0, WRAP, 1f));
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    setAppIcon(which);
                }
            });
        }
        return row;
    }

    private void setAppIcon(int which) {
        PackageManager pm = getPackageManager();
        pm.setComponentEnabledSetting(
                new ComponentName(this, "com.beacon.alarm." + ICON_ALIASES[which]),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        for (int i = 0; i < ICON_ALIASES.length; i++) {
            if (i == which) continue;
            pm.setComponentEnabledSetting(
                    new ComponentName(this, "com.beacon.alarm." + ICON_ALIASES[i]),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
        AlarmStore.prefs(this).edit().putInt("icon", which).apply();
        Toast.makeText(this, "Icon changed. Your home screen may take a few seconds to update.",
                Toast.LENGTH_LONG).show();
        recreate();
    }

    private View colorDot(final int color, final EditText target) {
        TextView dot = new TextView(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setStroke(dp(1), t.line);
        dot.setBackground(g);
        dot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                target.setText(AppTheme.hex(color));
            }
        });
        return dot;
    }

    private LinearLayout dotRow(int[] colors, EditText target) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int c : colors) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(34), dp(34));
            p.rightMargin = dp(10);
            row.addView(colorDot(c, target), p);
        }
        return row;
    }

    private void showCustomTheme() {
        SharedPreferences sp = AlarmStore.prefs(this);
        int cbg = sp.getInt("c_bg", 0xFF101820);
        int cac = sp.getInt("c_accent", 0xFFFFB020);

        LinearLayout v = dialogBody();
        final AlertDialog d = dialogFor(v);
        TextView title = tv("Custom theme", 20, t.fg);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        v.addView(title);
        v.addView(tv("Type a color like #FFB020, or tap a dot.", 13, t.muted), lp(MATCH, WRAP, 4));

        final EditText bgF = field(AppTheme.hex(cbg), InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, "#101820");
        final EditText acF = field(AppTheme.hex(cac), InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, "#FFB020");

        v.addView(caps("Background"), lp(MATCH, WRAP, 16));
        v.addView(bgF, lp(MATCH, WRAP, 8));
        v.addView(dotRow(new int[]{0xFF101820, 0xFF000000, 0xFF1A1024, 0xFF0B1F1A, 0xFFF2F4F7},
                bgF), lp(WRAP, WRAP, 10));

        v.addView(caps("Accent"), lp(MATCH, WRAP, 16));
        v.addView(acF, lp(MATCH, WRAP, 8));
        v.addView(dotRow(new int[]{0xFFFFB020, 0xFFFF6B6B, 0xFF5BD68A, 0xFF4DB8FF, 0xFFA78BFA},
                acF), lp(WRAP, WRAP, 10));
        v.addView(dotRow(new int[]{0xFFFF6B9A, 0xFFFFD400, 0xFF2DD4BF, 0xFFFF8A3D, 0xFFE5E7EB},
                acF), lp(WRAP, WRAP, 10));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = button("Cancel", false);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        cp.rightMargin = dp(6);
        btns.addView(cancel, cp);
        Button apply = button("Apply", true);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(52), 1f);
        ap.leftMargin = dp(6);
        btns.addView(apply, ap);
        v.addView(btns, lp(MATCH, WRAP, 22));

        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                d.dismiss();
            }
        });
        apply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Integer bg = AppTheme.parseHex(bgF.getText().toString());
                Integer ac = AppTheme.parseHex(acF.getText().toString());
                if (bg == null || ac == null) {
                    Toast.makeText(MainActivity.this, "Use a 6-digit color like #FFB020",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                AppTheme.saveCustom(MainActivity.this, bg, ac);
                d.dismiss();
                if (flashing) torch.stop();
                recreate();
            }
        });
        showCard(d);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (torch != null) {
            torch.setListener(null);
            torch.release();
        }
        super.onDestroy();
    }
}
