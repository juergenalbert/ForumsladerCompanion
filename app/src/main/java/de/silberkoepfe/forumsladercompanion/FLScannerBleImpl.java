package de.silberkoepfe.forumsladercompanion;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;

import com.noveogroup.android.log.Logger;
import com.noveogroup.android.log.LoggerManager;

import java.util.ArrayList;
import java.util.List;

import static android.content.Context.BLUETOOTH_SERVICE;

public class FLScannerBleImpl implements FLScanner {

    private static final Logger logger = LoggerManager.getLogger(FLScannerBleImpl.class);
    private static FLScannerBleImpl instance;

    private final BleService bleService;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothAdapter bluetoothAdapter;
    private ScanCallback scanCallback = new ScanCallback();
    private boolean scanning;
    private FLDeviceBleImpl device;

    synchronized static FLScanner getInstance(BleService bleService) {
        if (instance != null) {
            return instance;
        } else {
            instance = new FLScannerBleImpl(bleService);
            return instance;
        }
    }

    private FLScannerBleImpl(BleService bleService) {
        this.bleService = bleService;
        BluetoothManager bluetoothManager = (BluetoothManager) this.bleService.getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    @Override
    public boolean isScanning() {
        return scanning;
    }

    @Override
    synchronized public FLDevice findDevice() throws FLScannerException {
        logger.d("findDevice");

        checkPermissions();

        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        scanning = true;
        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(FLDeviceBleImpl.RX_TX_SERVICE))
                .build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(scanFilter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();

        try {
            bleService.setStatus(BleService.Status.SCANNING);
            bluetoothLeScanner.startScan(filters, settings, scanCallback);
            wait(5000);
        } catch (InterruptedException e) {
            logger.v("findDeviceThread interrupted");
        } finally {
            if (bluetoothAdapter.isEnabled()) {
                bluetoothLeScanner.stopScan(scanCallback);
                bluetoothLeScanner.flushPendingScanResults(scanCallback);
            }
        }

        return device;
    }

    private void checkPermissions() throws FLScannerException {
        logger.d("hasPermissons");

        //BluetoothManager bluetoothManager = (BluetoothManager) bleService.getSystemService(BLUETOOTH_SERVICE);
        //BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            throw new FLScannerException();
        } else if (!hasLocationPermissions()) {
            throw new FLScannerException();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean hasLocationPermissions() {
        logger.d("hasLocationPermissions");
        return bleService.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    synchronized private void addScanResult(ScanResult result) {
        logger.d("addScanResult result={}", result);
        final BluetoothDevice bluetoothDevice = result.getDevice();

        device = new FLDeviceBleImpl(bleService, bluetoothDevice);
        notify();
    }

    private class ScanCallback extends android.bluetooth.le.ScanCallback {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            bluetoothLeScanner.stopScan(this);
            scanning = false;
            logger.d( "onScanResult");
            addScanResult(result);
            super.onScanResult(callbackType, result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            logger.d("onBatchScanResults");
            bluetoothLeScanner.stopScan(this);
            scanning = false;
            for (ScanResult result : results) {
                addScanResult(result);
            }
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            logger.d("onScanFailed errorCode:" + errorCode);
            bluetoothLeScanner.stopScan(this);
            scanning = false;
            super.onScanFailed(errorCode);
        }
    }
}
