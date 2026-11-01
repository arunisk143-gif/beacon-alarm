package com.beacon.alarm;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Looper;

/** Controls the phone's real LED flashlight. No camera permission is needed. */
public class Torch {
    public interface Listener {
        void onChanged(boolean on);
    }

    private final CameraManager cm;
    private String camId;
    private final Handler h = new Handler(Looper.getMainLooper());
    private volatile boolean on;
    private boolean running;
    private Listener listener;
    private Runnable step;
    private final CameraManager.TorchCallback callback;

    public Torch(Context c) {
        cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics ch = cm.getCameraCharacteristics(id);
                Boolean f = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (f != null && f) {
                    camId = id;
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        callback = new CameraManager.TorchCallback() {
            @Override
            public void onTorchModeChanged(String id, boolean enabled) {
                if (id.equals(camId)) {
                    on = enabled;
                    if (listener != null) listener.onChanged(enabled);
                }
            }
        };
        try {
            cm.registerTorchCallback(callback, h);
        } catch (Exception ignored) {
        }
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public boolean hasFlash() {
        return camId != null;
    }

    public boolean isOn() {
        return on;
    }

    public void set(boolean value) {
        if (camId == null) return;
        try {
            cm.setTorchMode(camId, value);
        } catch (Exception ignored) {
        }
    }

    /** Flash the light. count 0 means keep going until stop() is called. */
    public void flash(final int count, final int onMs, final int offMs, final Runnable done) {
        stop();
        running = true;
        final int[] n = {0};
        final boolean[] lit = {false};
        step = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                if (!lit[0]) {
                    if (count > 0 && n[0] >= count) {
                        running = false;
                        set(false);
                        if (done != null) done.run();
                        return;
                    }
                    set(true);
                    lit[0] = true;
                    h.postDelayed(this, Math.max(50, onMs));
                } else {
                    set(false);
                    lit[0] = false;
                    n[0]++;
                    h.postDelayed(this, Math.max(50, offMs));
                }
            }
        };
        h.post(step);
    }

    /** Stops any flashing and turns the light off. */
    public void stop() {
        running = false;
        if (step != null) h.removeCallbacks(step);
        set(false);
    }

    public void release() {
        stop();
        try {
            cm.unregisterTorchCallback(callback);
        } catch (Exception ignored) {
        }
    }
}
