//  Copyright (c) 2020 KHN Solutions LLC. All rights reserved.

package com.tss.alcoholtesting.activities;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.tss.alcoholtesting.R;

import BACtrackAPI.API.BACtrackAPI;
import BACtrackAPI.API.BACtrackAPICallbacks;
import BACtrackAPI.Constants.BACTrackDeviceType;
import BACtrackAPI.Constants.BACtrackUnit;
import BACtrackAPI.Constants.Errors;
import BACtrackAPI.Exceptions.BluetoothLENotSupportedException;
import BACtrackAPI.Exceptions.BluetoothNotEnabledException;
import BACtrackAPI.Exceptions.LocationServicesNotEnabledException;

public class CheckingActivity extends Activity {

    private static final byte PERMISSIONS_FOR_SCAN = 100;

    private static String TAG = "MainActivity";

    private TextView statusMessageTextView;
    private TextView batteryLevelTextView;

    private BACtrackAPI mAPI;
    private String currentFirmware;
    private Button serialNumberButton;
    private Button useCountButton;
    private Button connectButton;
    private Button disconnectButton;
    private boolean waitingForBlow;
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checking);

        this.statusMessageTextView = (TextView)this.findViewById(R.id.status_message_text_view_id);
        this.batteryLevelTextView = (TextView)this.findViewById(R.id.battery_level_text_view_id);

        this.setStatus(R.string.TEXT_DISCONNECTED);

        this.serialNumberButton = (Button) findViewById(R.id.get_serial_number_button_id);
        this.useCountButton = (Button) findViewById(R.id.get_use_count_button_id);
        this.connectButton = (Button) findViewById(R.id.connect_nearest_button_id);
        this.disconnectButton = (Button) findViewById(R.id.disconnect_button_id);
        disconnectButton.setEnabled(false);

        waitingForBlow = false;

        this.registerReceiver(mBluetoothReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            this.registerReceiver(mLocationServicesReceiver,
                    new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
        }

        initBacTrackAPI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(mBluetoothReceiver);
    }

    protected void initBacTrackAPI() {
        String apiKey = "8994f5202fea469a903cb731f64cfc";//"a70f68a7e8c54c749842f99de1d89e9c";

        try {
            mAPI = new BACtrackAPI(this, mCallbacks, apiKey);
            context = this;
        } catch (BluetoothLENotSupportedException e) {
            e.printStackTrace();
            this.setStatus(R.string.TEXT_ERR_BLE_NOT_SUPPORTED);
        } catch (BluetoothNotEnabledException e) {
            e.printStackTrace();
            this.setStatus(R.string.TEXT_ERR_BT_NOT_ENABLED);
        } catch (LocationServicesNotEnabledException e) {
            e.printStackTrace();
            this.setStatus(R.string.TEXT_ERR_LOCATIONS_NOT_ENABLED);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_FOR_SCAN: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (mAPI == null)
                        initBacTrackAPI();
                    if (mAPI != null)   // check for success in case it was null before
                        mAPI.connectToNearestBreathalyzer();
                }
            }
        }
    }

    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED))
            {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);

                switch (state)
                {
                    case BluetoothAdapter.STATE_OFF:
                        setStatus(R.string.TEXT_ERR_BT_NOT_ENABLED);
                        break;
                    case BluetoothAdapter.STATE_ON:
                        handleBluetoothOn();
                        break;
                }
            }
        }
    };

    protected void handleBluetoothOn() {
        if (mAPI == null) {
            initBacTrackAPI();
        } else if (!areLocationServicesAvailable(context)) {
            this.setStatus(R.string.TEXT_ERR_LOCATIONS_NOT_ENABLED);
        } else {
            this.setStatus(R.string.TEXT_DISCONNECTED);
        }
    }

    private final BroadcastReceiver mLocationServicesReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();

            if (!areLocationServicesAvailable(context)) {
                setStatus(R.string.TEXT_ERR_LOCATIONS_NOT_ENABLED);
            }
            else if (mAPI == null) {
                initBacTrackAPI();
            }
            else if (!isBluetoothEnabled()){
                setStatus(R.string.TEXT_ERR_BT_NOT_ENABLED);
            }
            else {
                setStatus(R.string.TEXT_DISCONNECTED);
            }
        }
    };

    protected boolean isBluetoothEnabled()
    {
        BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

        return (bluetoothManager.getAdapter().isEnabled());
    }


    protected static boolean areLocationServicesAvailable(Context context)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            return lm.isLocationEnabled();
        }
        else
        {
            int mode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE,
                    Settings.Secure.LOCATION_MODE_OFF);
            return  (mode != Settings.Secure.LOCATION_MODE_OFF);
        }
    }

    public void connectNearestClicked(View v) {
        setStatus(R.string.TEXT_CONNECTING);
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(CheckingActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(CheckingActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(CheckingActivity.this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_FOR_SCAN);
        } else if (mAPI != null) {
            /**
             * Permission already granted, start scan.
             */
            mAPI.connectToNearestBreathalyzer();
            connectButton.setEnabled(false);
        }
    }

    public void disconnectClicked(View v) {
        if (mAPI != null) {
            mAPI.disconnect();
        }
    }

    public void getFirmwareVersionClicked(View v) {
        boolean result = false;
        if (mAPI != null) {
            result = mAPI.getFirmwareVersion();
        }
        if (!result)
            Log.e(TAG, "mAPI.getFirmwareVersion() failed");
        else
            Log.d(TAG, "Firmware version requested");
    }

    public void getSerialNumberClicked(View view) {
        boolean result = false;
        if (mAPI != null) {
            result = mAPI.getSerialNumber();
        }
        if (!result)
            Log.e(TAG, "mAPI.getSerialNumber() failed");
        else
            Log.d(TAG, "Serial Number requested");
    }

    public void requestUseCountClicked(View view) {
        boolean result = false;
        if (mAPI != null) {
            result = mAPI.getUseCount();
        }
        if (!result)
            Log.e(TAG, "mAPI.requestUseCount() failed");
        else
            Log.d(TAG, "Use count requested");
    }

    public void requestBatteryVoltageClicked(View view) {
        boolean result = false;
        if (mAPI != null) {
            result = mAPI.getBreathalyzerBatteryVoltage();
        }
        if (!result)
            Log.e(TAG, "mAPI.getBreathalyzerBatteryVoltage() failed");
        else
            Log.d(TAG, "Battery voltage requested");
    }


    public void startBlowProcessClicked(View v) {
        boolean result = false;
        if (mAPI != null) {
            result = mAPI.startCountdown();
        }
        if (!result)
            Log.e(TAG, "mAPI.startCountdown() failed");
        else
            Log.d(TAG, "Blow process start requested");
    }

    private void setStatus(int resourceId) {
        this.setStatus(this.getResources().getString(resourceId));
    }

    private void setStatus(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, message);
                statusMessageTextView.setText(String.format("Status:\n%s", message));
            }
        });
    }

    private void setBatteryStatus(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, message);
                batteryLevelTextView.setText(String.format("\n%s", message));
            }
        });
    }

    private class APIKeyVerificationAlert extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            return urls[0];
        }

        @Override
        protected void onPostExecute(String result) {
            AlertDialog.Builder apiApprovalAlert = new AlertDialog.Builder(context);
            apiApprovalAlert.setTitle("API Approval Failed");
            apiApprovalAlert.setMessage(result);
            apiApprovalAlert.setPositiveButton(
                    "Ok",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            mAPI.disconnect();
                            setStatus(R.string.TEXT_DISCONNECTED);
                            dialog.cancel();
                        }
                    });

            apiApprovalAlert.create();
            apiApprovalAlert.show();
        }
    }

    private final BACtrackAPICallbacks mCallbacks = new BACtrackAPICallbacks() {

        @Override
        public void BACtrackAPIKeyDeclined(String errorMessage) {
            APIKeyVerificationAlert verify = new APIKeyVerificationAlert();
            verify.execute(errorMessage);
        }

        @Override
        public void BACtrackAPIKeyAuthorized() {
            Log.e("APIKeyAuthorized", "success");
        }

        @Override
        public void BACtrackConnected(BACTrackDeviceType bacTrackDeviceType) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setStatus(R.string.TEXT_CONNECTED);
                    disconnectButton.setEnabled(true);
                }
            });
        }

        @Override
        public void BACtrackDidConnect(String s) {
            setStatus(R.string.TEXT_DISCOVERING_SERVICES);
        }

        @Override
        public void BACtrackDisconnected() {
            runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                      if (areLocationServicesAvailable(context) && isBluetoothEnabled())
                          setStatus(R.string.TEXT_DISCONNECTED);    // else, other routines will tell the user to enable bt/loc
                      setBatteryStatus("");
                      setCurrentFirmware(null);
                      connectButton.setEnabled(true);
                      disconnectButton.setEnabled(false);
                  }
              });
        }
        @Override
        public void BACtrackConnectionTimeout() {
            setStatus("Connection timed out");

            runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                      connectButton.setEnabled(true);
                      disconnectButton.setEnabled(false);
                  }
              });
        }

        @Override
        public void BACtrackFoundBreathalyzer(BACtrackAPI.BACtrackDevice device) {
            Log.d(TAG, "Found breathalyzer : " + device.toString());
        }

        @Override
        public void BACtrackCountdown(int currentCountdownCount) {
            setStatus(getString(R.string.TEXT_COUNTDOWN) + " " + currentCountdownCount);
        }

        @Override
        public void BACtrackStart() {
            waitingForBlow = true;
            setStatus(R.string.TEXT_BLOW_NOW);
        }

        @Override
        public void BACtrackBlow(float breathVolumeRemaining) {
            if (waitingForBlow)
                setStatus(String.format("Keep Blowing (%d%%)", 100 - (int)(100.0 * breathVolumeRemaining)));
        }

        @Override
        public void BACtrackAnalyzing() {
            waitingForBlow = false;
            setStatus(R.string.TEXT_ANALYZING);
        }

        @Override
        public void BACtrackResults(float measuredBac) {
            setStatus(getString(R.string.TEXT_FINISHED) + " " + measuredBac);
        }

        @Override
        public void BACtrackFirmwareVersion(String version) {
            setCurrentFirmware(version);
            setStatus(getString(R.string.TEXT_FIRMWARE_VERSION) + " " + version);
        }

        @Override
        public void BACtrackSerial(String serialHex) {
            setStatus(getString(R.string.TEXT_SERIAL_NUMBER) + " " + serialHex);
        }

        @Override
        public void BACtrackUseCount(int useCount) {
            Log.d(TAG, "UseCount: " + useCount);
            // C6/C8 bug in hardware does not allow getting use count
            if (useCount == 4096)
            {
                setStatus("Cannot retrieve use count for C6/C8 devices");
            }
            else
            {
                setStatus(getString(R.string.TEXT_USE_COUNT) + " " + useCount);
            }
        }

        @Override
        public void BACtrackBatteryVoltage(float voltage) {

        }

        @Override
        public void BACtrackBatteryLevel(int level) {
            setBatteryStatus(getString(R.string.TEXT_BATTERY_LEVEL) + " " + level);
        }

        @Override
        public void BACtrackError(int errorCode) {
            waitingForBlow = false;

            switch (errorCode)
            {
                case Errors.ERROR_TIME_OUT:
                    setStatus(R.string.TEXT_ERR_TIMEOUT_ERROR);
                    break;
                case Errors.ERROR_BLOW_ERROR:
                    setStatus(R.string.TEXT_ERR_BLOW_ERROR);
                    break;
                case Errors.ERROR_LOW_BATTERY:
                    setStatus(R.string.TEXT_ERR_LOW_BATTERY);
                    break;
                case Errors.ERROR_MAX_BAC_EXCEEDED_ERROR:
                    setStatus(R.string.TEXT_ERR_EXCEEDED_MAX_BAC);
                    break;
                default:
                    setStatus(R.string.TEXT_ERR_UNKNOWN_PREFIX);
            }
        }

        @Override
        public void BACtrackUnits(BACtrackUnit units) {

        }
    };


    public void setCurrentFirmware(@Nullable String currentFirmware) {
        this.currentFirmware = currentFirmware;
    }
}
