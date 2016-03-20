package com.example.KurtPower;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.*;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;
import android.widget.Toast;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeSpeedDistancePcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc;
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;

public class AntService extends Service
{

    public static final String ACTION_HEART_RATE = "com.example.kurtpower.heartrate";
    public static final String ACTION_POWER = "com.example.kurtpower.power";
    public static final String ACTION_CHRONOMETER_TIME = "com.example.kurtpower.chronometer";
    public static final String ACTION_TOGGLE_TEST_MODE = "toggleTestMode";
    public static final String ACTION_TOGGLE_CHRONOMETER = "toggleChronometer";
    public static final String EXTRA_HEARTRATE = "heartrate";
    public static final String EXTRA_POWER = "power";
    public static final String ACTION_CONNECT_ANT_DEVICE = "connectAntDevice";
    public static final String EXTRA_ANT_DEVICE_NUMBER = "antDeviceNumber";
    public static final String EXTRA_ANT_DEVICE_TYPE = "antDeviceType";
    public static final String EXTRA_SPEED_TYPE = "speedDistance";
    public static final String EXTRA_HEART_RATE_TYPE = "heartRate";
    public static final int NOTIFICATION_ID = 1;
    public static final String LOG_TAG = AntService.class.getSimpleName();
    public static final String EXTRA_RESET_CHRONOMETER = "reset";
    public static final String EXTRA_CHRONOMETER_TIME = "chronometerTime";
    public static final String DEFAULT_WHEEL_CIRCUMFERENCE = "2.095";

    private PccReleaseHandle<AntPlusHeartRatePcc> heartRateReleaseHandle;
    private PccReleaseHandle<AntPlusBikeSpeedDistancePcc> speedReleaseHandle;

    private ServiceHandler serviceHandler;

    private AntPluginPcc.IPluginAccessResultReceiver<AntPlusHeartRatePcc> heartRateReceiver = new AntPluginPcc.IPluginAccessResultReceiver<AntPlusHeartRatePcc>()
    {
        @Override
        public void onResultReceived(AntPlusHeartRatePcc antPlusHeartRatePcc, RequestAccessResult requestAccessResult, DeviceState deviceState)
        {
            Log.d(LOG_TAG, "onResultReceived: " + requestAccessResult + ":" + antPlusHeartRatePcc.getAntDeviceNumber());
            switch (requestAccessResult)
            {
                case SUCCESS:
                    antPlusHeartRatePcc.subscribeHeartRateDataEvent(new AntPlusHeartRatePcc.IHeartRateDataReceiver()
                    {
                        @Override
                        public void onNewHeartRateData(final long estTimestamp, EnumSet<EventFlag> eventFlags,
                                                       final int computedHeartRate, final long heartBeatCount,
                                                       final BigDecimal heartBeatEventTime, final AntPlusHeartRatePcc.DataState dataState)
                        {
                            //Log.d("KurtPower", "onNewHeartRateData: " + computedHeartRate);
                            if (dataState == AntPlusHeartRatePcc.DataState.LIVE_DATA)
                            {
                                sendBroadcast(new Intent(ACTION_HEART_RATE).putExtra(EXTRA_HEARTRATE, computedHeartRate));
                            }
                        }
                    });
                    Toast.makeText(AntService.this, "Connected to " + antPlusHeartRatePcc.getDeviceName(), Toast.LENGTH_LONG).show();
                    break;
                default:
                    Log.d(LOG_TAG, "onResultReceived: " + requestAccessResult.toString());
            }
        }
    };

    private AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeSpeedDistancePcc> speedReceiver = new AntPluginPcc.IPluginAccessResultReceiver<AntPlusBikeSpeedDistancePcc>()
    {
        @Override
        public void onResultReceived(AntPlusBikeSpeedDistancePcc antPlusBikeSpeedDistancePcc, RequestAccessResult requestAccessResult, DeviceState deviceState)
        {
            Log.d(LOG_TAG, "onResultReceived: " + requestAccessResult + ":" + antPlusBikeSpeedDistancePcc.getAntDeviceNumber());
            switch (requestAccessResult)
            {
                case SUCCESS:
                    Double wheelCircumference = getWheelCircumference();
                    Log.d(LOG_TAG, "Using wheel circumference: " + wheelCircumference);
                    antPlusBikeSpeedDistancePcc.subscribeCalculatedSpeedEvent(new AntPlusBikeSpeedDistancePcc.CalculatedSpeedReceiver(new BigDecimal(wheelCircumference))
                    {
                        @Override
                        public void onNewCalculatedSpeed(final long estTimestamp,
                                                         final EnumSet<EventFlag> eventFlags, final BigDecimal calculatedSpeed)
                        {
                            //Log.d("KurtPower", "onNewCalculatedSpeed" + calculatedSpeed);
                            final double speedInMph = meterPerSecondToMilesPerHour(calculatedSpeed.doubleValue());
                            final Double power = toKurtKineticPower(speedInMph);
                            sendBroadcast(new Intent(ACTION_POWER).putExtra(EXTRA_POWER, power.intValue()));
                        }
                    });
                    Toast.makeText(AntService.this, "Connected to " + antPlusBikeSpeedDistancePcc.getDeviceName(), Toast.LENGTH_LONG).show();
                    break;
                default:
                    Log.d(LOG_TAG, "onResultReceived: " + requestAccessResult.toString());
            }
        }
    };

    private Double getWheelCircumference()
    {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String wheelCircumferenceString = sharedPref.getString(SettingsActivity.WHEEL_CIRCUMFERENCE_KEY, DEFAULT_WHEEL_CIRCUMFERENCE);
        return Double.valueOf(wheelCircumferenceString);
    }

    private AntPluginPcc.IDeviceStateChangeReceiver stateReceiver = new AntPluginPcc.IDeviceStateChangeReceiver()
    {
        @Override
        public void onDeviceStateChange(final DeviceState deviceState)
        {
            Log.d(LOG_TAG, "onDeviceStateChange: " + deviceState);
        }
    };
    private boolean chronometerRunning = false;
    private long chronometerStoppedAt = 0;
    private KurtChronometer chronometer;

    private static double meterPerSecondToMilesPerHour(double ms)
    {
        return 2.23693629 * ms;
    }

    private static double toKurtKineticPower(double speedMph)
    {
        return (5.244820) * speedMph + (0.019168) * Math.pow(speedMph, 3);
    }

    @Override
    public void onCreate()
    {
        Log.d(LOG_TAG, "onCreate");
        super.onCreate();
        Intent notificationIntent = new Intent(this, KurtPower.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification notification = new Notification.Builder(this)
                .setContentTitle("Kurt Power running...")
                .setContentText("Click to open")
                .setSmallIcon(R.drawable.ic_stat_maps_directions_bike)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        chronometer = new KurtChronometer(this);
        // Set to visible manually otherwise the chronometer wont tick
        chronometer.onWindowVisibilityChanged(View.VISIBLE);
        chronometer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener()
        {
            @Override
            public void onChronometerTick(Chronometer chronometer)
            {
                Log.d(LOG_TAG, "onChronometerTick " + chronometer.getText());
                sendBroadcast(new Intent(ACTION_CHRONOMETER_TIME).putExtra(EXTRA_CHRONOMETER_TIME, chronometer.getText()));
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d(LOG_TAG, "onStartCommand " + intent.getAction());

        if (intent != null)
        {
            if (ACTION_CONNECT_ANT_DEVICE.equals(intent.getAction()))
            {
                int antDeviceNumber = intent.getIntExtra(EXTRA_ANT_DEVICE_NUMBER, 0);
                String antDeviceType = intent.getStringExtra(EXTRA_ANT_DEVICE_TYPE);
                if (EXTRA_SPEED_TYPE.equals(antDeviceType))
                {
                    connectSpeed(antDeviceNumber);
                }
                else if (EXTRA_HEART_RATE_TYPE.equals(antDeviceType))
                {
                    connectHeartRate(antDeviceNumber);
                }
            }
            else if (ACTION_TOGGLE_TEST_MODE.equals(intent.getAction()))
            {
                toggleTestMode();
            }
            else if (ACTION_TOGGLE_CHRONOMETER.equals(intent.getAction()))
            {
                if (intent.getBooleanExtra(EXTRA_RESET_CHRONOMETER, false))
                {
                    resetChronometer();
                }
                else
                {
                    toggleChrometer();
                }
            }
        }

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    private void toggleChrometer()
    {
        if (chronometerRunning)
        {
            Log.d(LOG_TAG, "stopping chronometer");
            chronometer.stop();
            chronometerStoppedAt = SystemClock.elapsedRealtime() - chronometer.getBase();
            chronometerRunning = false;
        }
        else
        {
            Log.d(LOG_TAG, "starting chronometer");
            chronometer.setBase(SystemClock.elapsedRealtime() - chronometerStoppedAt);
            chronometer.start();
            chronometerRunning = true;
        }
    }

    private void resetChronometer()
    {
        Log.d(LOG_TAG, "resetting chronometer");
        chronometer.stop();
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometerStoppedAt = 0;
        chronometerRunning = false;
        sendBroadcast(new Intent(ACTION_CHRONOMETER_TIME).putExtra(EXTRA_CHRONOMETER_TIME, chronometer.getText()));
    }

    private void toggleTestMode()
    {
        if (serviceHandler == null)
        {
            //Enable test mode

            // Close ANT connections
            closeHeartRateHandle();
            closeSpeedHandle();

            HandlerThread thread = new HandlerThread("ServiceStartArguments",
                    Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();

            // Get the HandlerThread's Looper and use it for our Handler
            Looper serviceLooper = thread.getLooper();
            serviceHandler = new ServiceHandler(serviceLooper);
            Message message = serviceHandler.obtainMessage();
            message.arg1 = 100;
            message.arg2 = 180;
            serviceHandler.sendMessage(message);
        }
        else
        {
            // Disable test mode
            serviceHandler.getLooper().quit();
            serviceHandler = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy()
    {
        Log.d(LOG_TAG, "onDestroy");
        closeHeartRateHandle();
        closeSpeedHandle();
        if (chronometer != null)
        {
            chronometer.stop();
        }
    }

    private void connectHeartRate(int antDeviceNumber)
    {
        Log.d(LOG_TAG, "connectHeartRate: " + antDeviceNumber);
        closeHeartRateHandle();

        heartRateReleaseHandle = AntPlusHeartRatePcc.requestAccess(this, antDeviceNumber, -1, heartRateReceiver, stateReceiver);
    }

    private void connectSpeed(int antDeviceNumber)
    {
        Log.d(LOG_TAG, "connectSpeed: " + antDeviceNumber);
        closeSpeedHandle();

        speedReleaseHandle = AntPlusBikeSpeedDistancePcc.requestAccess(this, antDeviceNumber, -1, false, speedReceiver, stateReceiver);
    }

    private void closeSpeedHandle()
    {
        if (speedReleaseHandle != null)
        {
            speedReleaseHandle.close();
            speedReleaseHandle = null;
        }
    }

    private void closeHeartRateHandle()
    {
        if (heartRateReleaseHandle != null)
        {
            heartRateReleaseHandle.close();
            heartRateReleaseHandle = null;
        }
    }

    private final class ServiceHandler extends Handler
    {
        public ServiceHandler(Looper looper)
        {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg)
        {
            sendBroadcast(new Intent(ACTION_HEART_RATE).putExtra(EXTRA_HEARTRATE, msg.arg1));
            sendBroadcast(new Intent(ACTION_POWER).putExtra(EXTRA_POWER, msg.arg2));

            Message newMsg = obtainMessage();
            newMsg.arg1 = msg.arg1 >= 200 ? 100 : msg.arg1 + 1;
            newMsg.arg2 = msg.arg2 >= 500 ? 180 : msg.arg2 + 3;
            sendMessageDelayed(newMsg, 500);
        }
    }
}
