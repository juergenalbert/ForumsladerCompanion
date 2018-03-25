package de.silberkoepfe.forumsladercompanion;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import com.noveogroup.android.log.Logger;
import com.noveogroup.android.log.LoggerManager;

import java.util.List;
import java.util.UUID;

class FLDeviceBleImpl implements FLDevice {
    private static final Logger logger = LoggerManager.getLogger(FLDeviceBleImpl.class);
    static UUID RX_TX_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    static UUID RX_TX_SERVICE_CHAR = UUID.fromString("0000ef38-0000-1000-8000-00805f9b34fb");

    private final BluetoothGatt bluetoothGatt;
    private long lastRequest;

    public FLDeviceBleImpl(BleService bleService, BluetoothDevice bluetoothDevice) {
        bleService.setStatus(BleService.Status.CONNECTING);
        bluetoothGatt = bluetoothDevice.connectGatt(bleService, false, new BluetoothGattCallback() {
            @Override
            public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
                logger.d("onPhyUpdate");
                super.onPhyUpdate(gatt, txPhy, rxPhy, status);
            }

            @Override
            public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
                logger.d("onPhyRead txPhy:" + txPhy + " rxPhy:" + rxPhy + " status:" + status);
                super.onPhyRead(gatt, txPhy, rxPhy, status);
            }

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                logger.d("onConnectionStateChange status:" + status + " newState:" + newState);
                super.onConnectionStateChange(gatt, status, newState);

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    // handle anything not SUCCESS as failure
                    bluetoothGatt.disconnect();
                    return;
                }

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    bleService.setStatus(BleService.Status.DISCOVERING_SERVICE);
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    bluetoothGatt.disconnect();
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                logger.d("onServicesDiscovered gatt: %s, status: %d", gatt, status);
                bleService.setStatus(BleService.Status.DISCOVERING_CHARACTERISTIC);

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    final BluetoothGattService bluetoothGattService = bluetoothGatt.getService(RX_TX_SERVICE);
                    if (bluetoothGattService != null) {
                        final BluetoothGattCharacteristic characteristic = bluetoothGattService.getCharacteristic(RX_TX_SERVICE_CHAR);
                        if (characteristic != null) {
                            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                            boolean characteristicWriteSuccess = gatt.setCharacteristicNotification(characteristic, true);
                            if (characteristicWriteSuccess) {
                                List<BluetoothGattDescriptor> descriptorList = characteristic.getDescriptors();
                                for (BluetoothGattDescriptor descriptor : descriptorList) {
                                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                    boolean descriptorWriteInitiated = gatt.writeDescriptor(descriptor);
                                    if (descriptorWriteInitiated) {
                                        bleService.setStatus(BleService.Status.CONNECTED);
                                    } else {
                                        logger.e("cannot write descriptor");
                                    }
                                }
                            } else {
                                logger.e("cannot set characteristic notification");
                            }
                        } else {
                            logger.e("cannot find characteristic %s", RX_TX_SERVICE_CHAR);
                        }
                    } else {
                        logger.e("cannot find service %s", RX_TX_SERVICE);
                    }
                }
                super.onServicesDiscovered(gatt, status);
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                logger.d("onCharacteristicRead");
                super.onCharacteristicRead(gatt, characteristic, status);
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                logger.d("onCharacteristicWrite");
                super.onCharacteristicWrite(gatt, characteristic, status);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                logger.d("onCharacteristicChanged");
                lastRequest = System.currentTimeMillis();
                bleService.setStatus(BleService.Status.RECEIVING);
                super.onCharacteristicChanged(gatt, characteristic);
            }

            @Override
            public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                logger.d("onDescriptorRead");
                super.onDescriptorRead(gatt, descriptor, status);
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                logger.d("onDescriptorWrite");
                super.onDescriptorWrite(gatt, descriptor, status);
            }

            @Override
            public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
                logger.d("onReliableWriteCompleted");
                super.onReliableWriteCompleted(gatt, status);
            }

            @Override
            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
                logger.d("onReadRemoteRssi");
                super.onReadRemoteRssi(gatt, rssi, status);
            }

            @Override
            public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                logger.d("onMtuChanged");
                super.onMtuChanged(gatt, mtu, status);
            }
        });
    }

    @Override
    public void disconnect() {
       bluetoothGatt.disconnect();
       bluetoothGatt.close();
    }

    @Override
    public boolean isDead() {
        return System.currentTimeMillis() - lastRequest > 10000;
    }
}
