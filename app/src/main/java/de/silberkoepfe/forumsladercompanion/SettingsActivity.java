package de.silberkoepfe.forumsladercompanion;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;

import com.noveogroup.android.log.Logger;
import com.noveogroup.android.log.LoggerManager;

import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatPreferenceActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    private static final Logger logger = LoggerManager.getLogger(SettingsActivity.class);

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_FINE_LOCATION = 2;

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName)
                || DataSyncPreferenceFragment.class.getName().equals(fragmentName)
                || NotificationPreferenceFragment.class.getName().equals(fragmentName);
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        public static final String SERVICE_SWITCH = "service_switch";
        private boolean permissionRequestRunning;

        private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                logger.d("onReceive context=%s, intent=%s", context, intent);
                final int status = intent.getIntExtra("status", 0);
                findPreference(SERVICE_SWITCH).setSummary(status);

                if (status == R.string.status_bluetooth_requested && !permissionRequestRunning) {
                    permissionRequestRunning = requestPermissions();
                }
            }
        };

        private boolean requestPermissions() {
            logger.d("requestPermissons");

            BluetoothManager bluetoothManager = (BluetoothManager) getActivity().getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                requestBluetoothEnable();
                return true;
            } else if (!hasLocationPermissions()) {
                requestLocationPermission();
                return true;
            }
            return false;
        }

        private void requestBluetoothEnable() {
            logger.d("requestBluetoothEnable");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        @TargetApi(Build.VERSION_CODES.M)
        private boolean hasLocationPermissions() {
            logger.d("hasLocationPermissions");
            return getActivity().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        @TargetApi(Build.VERSION_CODES.M)
        private void requestLocationPermission() {
            logger.d("requestLocationPermission");
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            logger.d("onActivityResult requestCode: %d, resultCode: %d, date: %s", requestCode, resultCode, data);

            switch (requestCode) {
                case REQUEST_ENABLE_BT:
                case REQUEST_FINE_LOCATION:
                    if (resultCode == 0) {
                        // user rejected bluetooth or permission enable request
                        SwitchPreference pref = (SwitchPreference) findPreference(SERVICE_SWITCH);
                        pref.setChecked(false);
                        updateService(false);
                    } else {
                        updateService(true);
                    }
                    permissionRequestRunning = false;
                    break;
            }
            super.onActivityResult(requestCode, resultCode, data);
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            logger.d("onRequestPermissionsResult");
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_general);
            setHasOptionsMenu(true);

            final boolean service_switch = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean(SERVICE_SWITCH, false);
            updateService(service_switch);

            findPreference(SERVICE_SWITCH).setOnPreferenceChangeListener((Preference preference, Object active) ->  {
                return !updateService((boolean) active);
            });

            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(messageReceiver,
                    new IntentFilter(BleService.STATUS_CHANGED_ACTION));
        }

        private boolean updateService(boolean active) {
            Intent i = new Intent(getActivity(), BleService.class);
            final SettingsActivity activity = (SettingsActivity) getActivity();
            if (active) {
                i.setAction(BleService.ServiceHandlerCommand.START.name());
                activity.startService(i);
            } else {
                i.setAction(BleService.ServiceHandlerCommand.STOP.name());
                activity.startService(i);
            }
            return false;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(messageReceiver);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows notification preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class NotificationPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_notification);
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * This fragment shows data and sync preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class DataSyncPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_data_sync);
            setHasOptionsMenu(true);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }
}
