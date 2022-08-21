package com.example.sensor_data_logger2;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.security.Key;
import java.security.KeyException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class IMUSession implements SensorEventListener {
    // properties
    private final static String LOG_TAG = IMUSession.class.getName();

    private MainActivity mContext;
    private SensorManager mSensorManager;
    private HashMap<String, Sensor> mSensors = new HashMap<>();
    private float mInitialStepCount = -1;
    private FileStreamer mFileStreamer = null;

    private AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private AtomicBoolean mIsWritingFile = new AtomicBoolean(false);

    private float[] mAcceMeasure = new float[3];
    private float[] mGyroMeasure = new float[3];
    private float[] mMagnetMeasure = new float[3];

    // constructor
    public IMUSession(MainActivity context) {

        // initialize object and sensor manager
        mContext = context;
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);

        // setup and register various sensors
        mSensors.put("acce", mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
        mSensors.put("gyro", mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));
        mSensors.put("magnet", mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
        registerSensors();
    }

    // methods
    public void registerSensors() {
        for (Sensor eachSensor : mSensors.values()) {
            //mSensorManager.registerListener(this, eachSensor, SensorManager.SENSOR_DELAY_GAME);
            mSensorManager.registerListener(this, eachSensor, 33000);
        }
    }

    public void unregisterSensors() {
        for (Sensor eachSensor : mSensors.values()) {
            mSensorManager.unregisterListener(this, eachSensor);
        }
    }

    public void startSession(String streamFolder) {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        String formattedDate = df.format(c.getTime());

        // initialize text file streams
        if (streamFolder != null) {
            mFileStreamer = new FileStreamer(mContext, streamFolder);
            try {
                mFileStreamer.addFile("acce", "acce.csv");
                mFileStreamer.addFile("gyro", "gyro.csv");
                mFileStreamer.addFile("magnet", "magnet.csv");
                mIsWritingFile.set(true);
            } catch (IOException e) {
                mContext.showToast("Error occurs when creating output IMU files.");
                e.printStackTrace();
            }
        }
        mIsRecording.set(true);
    }

    public void stopSession() {

        mIsRecording.set(false);
        if (mIsWritingFile.get()) {

            // close all recorded text files
            try {
                mFileStreamer.endFiles();
            } catch (IOException e) {
                mContext.showToast("Error occurs when finishing IMU text files.");
                e.printStackTrace();
            }

            // copy accelerometer calibration file to the streaming folder
            try {
                File acceCalibFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/acce_calib.txt");
                File outAcceCalibFile = new File(mFileStreamer.getOutputFolder() + "/acce_calib.txt");
                if (acceCalibFile.exists()) {
                    FileInputStream istr = new FileInputStream(acceCalibFile);
                    FileOutputStream ostr = new FileOutputStream(outAcceCalibFile);
                    FileChannel ichn = istr.getChannel();
                    FileChannel ochn = ostr.getChannel();
                    ichn.transferTo(0, ichn.size(), ochn);
                    istr.close();
                    ostr.close();

                    Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    scanIntent.setData(Uri.fromFile(outAcceCalibFile));
                    mContext.sendBroadcast(scanIntent);
                }
            } catch (IOException e) {
                mContext.showToast("Error occurs when copying accelerometer calibration text files.");
                e.printStackTrace();
            }

            // reset some properties
            mIsWritingFile.set(false);
            mFileStreamer = null;
        }
        mInitialStepCount = -1;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // set some variables
        float[] values = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
        boolean isFileSaved = (mIsRecording.get() && mIsWritingFile.get());

//        Calendar c = Calendar.getInstance();
//        SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
//        String formattedDate = df.format(c.getTime());
//        long num = Long.parseLong(formattedDate);

        // update each sensor measurements
        long timeMilli = System.currentTimeMillis();
        long timestamp = sensorEvent.timestamp;
        Sensor eachSensor = sensorEvent.sensor;
        try {
            switch (eachSensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    mAcceMeasure[0] = sensorEvent.values[0];
                    mAcceMeasure[1] = sensorEvent.values[1];
                    mAcceMeasure[2] = sensorEvent.values[2];
                    if (isFileSaved) {
                        mFileStreamer.addRecord(timeMilli, "acce", 3, sensorEvent.values);
                        //mFileStreamer.addRecord(timestamp, "acce", 3, sensorEvent.values);
                        //mFileStreamer.addRecord(Long.parseLong(formattedDate), "acce", 3, sensorEvent.values);
                        //mFileStreamer.addRecord(num, "acce", 3, sensorEvent.values);
                    }
                    break;

                case Sensor.TYPE_GYROSCOPE:
                    mGyroMeasure[0] = sensorEvent.values[0];
                    mGyroMeasure[1] = sensorEvent.values[1];
                    mGyroMeasure[2] = sensorEvent.values[2];
                    if (isFileSaved) {
                        mFileStreamer.addRecord(timeMilli, "gyro", 3, sensorEvent.values);
                        //mFileStreamer.addRecord(timestamp, "gyro", 3, sensorEvent.values);
                        //mFileStreamer.addRecord(Long.parseLong(formattedDate), "gyro", 3, sensorEvent.values);
                        //mFileStreamer.addRecord(num, "gyro", 3, sensorEvent.values);
                    }
                    break;

                case Sensor.TYPE_MAGNETIC_FIELD:
                    mMagnetMeasure[0] = sensorEvent.values[0];
                    mMagnetMeasure[1] = sensorEvent.values[1];
                    mMagnetMeasure[2] = sensorEvent.values[2];
                    if (isFileSaved) {
                        mFileStreamer.addRecord(timeMilli, "magnet", 3, sensorEvent.values);
                        //mFileStreamer.addRecord(timestamp, "magnet", 3, sensorEvent.values);
                        //mFileStreamer.addRecord(Long.parseLong(formattedDate), "magnet", 3, sensorEvent.values);
                        //mFileStreamer.addRecord(num, "magnet", 3, sensorEvent.values);
                    }
                    break;
            }
        } catch (IOException | KeyException e) {
            Log.d(LOG_TAG, "onSensorChanged: Something is wrong.");
            e.printStackTrace();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // getter and setter
    public boolean isRecording() {
        return mIsRecording.get();
    }

    public float[] getAcceMeasure() {
        return mAcceMeasure;
    }

    public float[] getGyroMeasure() {
        return mGyroMeasure;
    }

    public float[] getMagnetMeasure() {
        return mMagnetMeasure;
    }
}
