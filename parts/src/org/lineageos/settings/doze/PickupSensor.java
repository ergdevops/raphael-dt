package org.lineageos.settings.doze;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PickupSensor implements SensorEventListener {
    private static final String TAG = "PickupSensor";
    private static final boolean DEBUG = false;

    private static final int MIN_PULSE_INTERVAL_MS = 2500;
    private static final int MIN_TIME_BETWEEN_SIGNS_MS = 500;

    private final SensorManager mSensorManager;
    private final Sensor mSensor;
    private final PowerManager mPowerManager;
    private final WakeLock mWakeLock;

    private final ExecutorService mExecutorService;

    private long mEntryTimestamp;

    private Future<?> mPendingSensingTask;
    private boolean mSensing;

    public PickupSensor(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PickupSensor");

        mExecutorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float z = event.values[2];

        // Check if the device is facing up and the z value is positive
        if (z >= SensorManager.GRAVITY_EARTH * 0.5f) {
            final long now = SystemClock.uptimeMillis();
            if (now - mEntryTimestamp >= MIN_TIME_BETWEEN_SIGNS_MS) {
                mEntryTimestamp = now;
                pulse();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No-op
    }

    public void enable() {
        if (!mSensing) {
            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
            mSensing = true;
        }
    }

    public void disable() {
        if (mSensing) {
            mSensorManager.unregisterListener(this);
            mSensing = false;
        }
    }

    private void pulse() {
        if (DEBUG) {
            Log.d(TAG, "pulse");
        }

        if (mWakeLock.isHeld()) {
            return;
        }

        mWakeLock.acquire();

        if (mPendingSensingTask != null) {
            mPendingSensingTask.cancel(false);
        }

        mPendingSensingTask = mExecutorService.submit(() -> {
            try {
                Thread.sleep(MIN_PULSE_INTERVAL_MS);
            } catch (InterruptedException e) {
                // Ignore
            }

            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        });
    }
}
