package com.example.KurtPower;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import com.dsi.ant.plugins.antplus.pcc.AntPlusBikeSpeedDistancePcc;
import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc;
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult;
import com.dsi.ant.plugins.antplus.pccbase.AntPlusBikeSpdCadCommonPcc;
import com.dsi.ant.plugins.antplus.pccbase.AsyncScanController;

public class KurtPower extends Activity
{

    public static final String LOG_TAG = "KurtPower";
    public static final String DEFAULT_MAX_HEART_RATE = "200";
    public static final String DEFAULT_FTP = "300";
    private TextView tvHeartRate;
    private TextView tvPower;
    private TextView tvChronometer;
    private AntPlusBikeSpdCadCommonPcc.BikeSpdCadAsyncScanController<AntPlusBikeSpeedDistancePcc> speedScanController;
    private AsyncScanController<AntPlusHeartRatePcc> heartRateScanController;
    private MessageReceiver messageReceiver;
    private boolean receiverIsRegistered = false;
    private Integer maxHeartRate;
    private Integer ftp;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Log.d(LOG_TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        messageReceiver = new MessageReceiver();

        tvChronometer = (TextView) findViewById(R.id.chronometer);
        tvChronometer.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startService(new Intent(KurtPower.this, AntService.class).setAction(AntService.ACTION_TOGGLE_CHRONOMETER));
            }
        });
        tvChronometer.setOnLongClickListener(new View.OnLongClickListener()
        {
            @Override
            public boolean onLongClick(View v)
            {
                startService(new Intent(KurtPower.this, AntService.class).setAction(AntService.ACTION_TOGGLE_CHRONOMETER).putExtra(AntService.EXTRA_RESET_CHRONOMETER, true));
                return true;
            }
        });
        tvHeartRate = (TextView) findViewById(R.id.heartRate);
        tvPower = (TextView) findViewById(R.id.power);

        registerMessageReceiver();
    }

    @Override
    protected void onResume()
    {
        Log.d(LOG_TAG, "onResume");
        super.onResume();
        registerMessageReceiver();
    }

    private void registerMessageReceiver()
    {
        if (!receiverIsRegistered)
        {
            registerReceiver(messageReceiver, new IntentFilter(AntService.ACTION_HEART_RATE));
            registerReceiver(messageReceiver, new IntentFilter(AntService.ACTION_POWER));
            registerReceiver(messageReceiver, new IntentFilter(AntService.ACTION_CHRONOMETER_TIME));
            receiverIsRegistered = true;
        }
    }

    @Override
    protected void onPause()
    {
        Log.d(LOG_TAG, "onPause");
        super.onPause();
        unregisterMessageReceiver();
    }

    private void unregisterMessageReceiver()
    {
        if (receiverIsRegistered)
        {
            unregisterReceiver(messageReceiver);
            receiverIsRegistered = false;
        }
    }

    protected void connectHeartRate()
    {
        closeHeartRateScanController();

        heartRateScanController = AntPlusHeartRatePcc.requestAsyncScanController(this, 0, new AsyncScanController.IAsyncScanResultReceiver()
        {
            @Override
            public void onSearchStopped(RequestAccessResult requestAccessResult)
            {
                Log.d(LOG_TAG, "onSearchStopped: " + requestAccessResult);
                closeHeartRateScanController();
            }

            @Override
            public void onSearchResult(AsyncScanController.AsyncScanResultDeviceInfo deviceInfo)
            {
                Log.d(LOG_TAG, "Device found: " + deviceInfo.getDeviceDisplayName() + ":" + deviceInfo.getAntDeviceNumber());
                closeHeartRateScanController();
                Intent intent = new Intent(KurtPower.this, AntService.class).setAction(AntService.ACTION_CONNECT_ANT_DEVICE);
                intent.putExtra(AntService.EXTRA_ANT_DEVICE_NUMBER, deviceInfo.getAntDeviceNumber());
                intent.putExtra(AntService.EXTRA_ANT_DEVICE_TYPE, AntService.EXTRA_HEART_RATE_TYPE);
                startService(intent);
            }
        });
    }

    private void connectSpeed()
    {
        closeSpeedScanController();

        speedScanController = AntPlusBikeSpeedDistancePcc.requestAsyncScanController(this, 0, new AntPlusBikeSpdCadCommonPcc.IBikeSpdCadAsyncScanResultReceiver()
        {
            @Override
            public void onSearchStopped(RequestAccessResult requestAccessResult)
            {
                Log.d(LOG_TAG, "onSearchStopped: " + requestAccessResult);
                closeSpeedScanController();
            }

            @Override
            public void onSearchResult(AntPlusBikeSpdCadCommonPcc.BikeSpdCadAsyncScanResultDeviceInfo deviceInfo)
            {
                Log.d(LOG_TAG, "Device found: " + deviceInfo.resultInfo.getDeviceDisplayName() + ":" + deviceInfo.resultInfo.getAntDeviceNumber());
                closeSpeedScanController();
                Intent intent = new Intent(KurtPower.this, AntService.class).setAction(AntService.ACTION_CONNECT_ANT_DEVICE);
                intent.putExtra(AntService.EXTRA_ANT_DEVICE_NUMBER, deviceInfo.resultInfo.getAntDeviceNumber());
                intent.putExtra(AntService.EXTRA_ANT_DEVICE_TYPE, AntService.EXTRA_SPEED_TYPE);
                startService(intent);
            }
        });
    }

    private void closeHeartRateScanController()
    {
        if (heartRateScanController != null)
        {
            heartRateScanController.closeScanController();
            heartRateScanController = null;
        }
    }

    private void closeSpeedScanController()
    {
        if (speedScanController != null)
        {
            speedScanController.closeScanController();
            speedScanController = null;
        }
    }

    @Override
    protected void onDestroy()
    {
        Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
        closeHeartRateScanController();
        closeSpeedScanController();
        unregisterMessageReceiver();
    }

    private void toggleTestMode()
    {
        Intent intent = new Intent(KurtPower.this, AntService.class).setAction(AntService.ACTION_TOGGLE_TEST_MODE);
        startService(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.menu_find_heart_rate:
                connectHeartRate();
                return true;
            case R.id.menu_find_power:
                connectSpeed();
                return true;
            case R.id.menu_test:
                toggleTestMode();
                return true;
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateChronometer(Intent intent)
    {
        tvChronometer.setText(intent.getStringExtra(AntService.EXTRA_CHRONOMETER_TIME));
    }

    private void updatePower(Intent intent)
    {
        int power = intent.getIntExtra(AntService.EXTRA_POWER, -1);
        String textPower = power >= 0 ? Integer.toString(power) : "*";
        tvPower.setText(textPower);

        int ftp = getFtp();
        if (power < 0.9 * ftp)
        {
            tvPower.setTextColor(Color.GREEN);
        }
        else if (power < 1.05 * ftp)
        {
            tvPower.setTextColor(Color.YELLOW);
        }
        else if (power < 1.2 * ftp)
        {
            tvPower.setTextColor(Color.rgb(255, 165, 0));
        }
        else
        {
            tvPower.setTextColor(Color.RED);
        }
    }

    private int getFtp()
    {
        if (ftp == null)
        {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String ftpString = sharedPref.getString(SettingsActivity.FTP_KEY, DEFAULT_FTP);
            ftp = Integer.valueOf(ftpString);
        }
        return ftp;
    }

    private void updateHeartRate(Intent intent)
    {
        int heartRate = intent.getIntExtra(AntService.EXTRA_HEARTRATE, 0);
        String textHeartRate = heartRate > 0 ? Integer.toString(heartRate) : "*";
        tvHeartRate.setText(textHeartRate);

        int maxHeartRate = getMaxHeartRate();
        if (heartRate < 0.7 * maxHeartRate)
        {
            tvHeartRate.setTextColor(Color.GREEN);
        }
        else if (heartRate < 0.8 * maxHeartRate)
        {
            tvHeartRate.setTextColor(Color.YELLOW);
        }
        else if (heartRate < 0.9 * maxHeartRate)
        {
            tvHeartRate.setTextColor(Color.rgb(255, 165, 0));
        }
        else
        {
            tvHeartRate.setTextColor(Color.RED);
        }
    }

    private int getMaxHeartRate()
    {
        if (maxHeartRate == null)
        {
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String maxHeartRateString = sharedPref.getString(SettingsActivity.MAX_HEART_RATE_KEY, DEFAULT_MAX_HEART_RATE);
            maxHeartRate = Integer.valueOf(maxHeartRateString);
        }
        return maxHeartRate;
    }

    private class MessageReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (AntService.ACTION_HEART_RATE.equals(intent.getAction()))
            {
                updateHeartRate(intent);
            }
            else if (AntService.ACTION_POWER.equals(intent.getAction()))
            {
                updatePower(intent);
            }
            else if (AntService.ACTION_CHRONOMETER_TIME.equals(intent.getAction()))
            {
                updateChronometer(intent);
            }
        }
    }

}
