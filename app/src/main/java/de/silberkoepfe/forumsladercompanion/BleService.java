package de.silberkoepfe.forumsladercompanion;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;

import com.noveogroup.android.log.Logger;
import com.noveogroup.android.log.LoggerManager;

import java.util.Random;

public class BleService extends Service {
    private static final Logger logger = LoggerManager.getLogger(BleService.class);

    private ServiceHandler serviceHandler;
    private FLDevice device;
    private Status status = Status.STOPPED;
    private BroadcastReceiver broadcastReceiver;

    enum Status {
        STARTED(R.string.status_started),
        SCANNING(R.string.status_scanning),
        CONNECTING(R.string.status_connecting),
        CONNECTED(R.string.status_connected),
        STOPPING(R.string.status_stopping),
        STOPPED(R.string.status_stopped),
        DISCOVERING_SERVICE(R.string.status_discovering_service),
        DISCOVERING_CHARACTERISTIC(R.string.status_discovering_characteristic),
        RECEIVING(R.string.status_receiving),
        BLUETOOTH_REQUESTED(R.string.status_bluetooth_requested),
        WAITING_FOR_BLUETOOTH(R.string.waiting_for_bluetooth);
        final int resId;

        Status(int resId) {
            this.resId = resId;
        }
    }
    enum ServiceHandlerCommand { UNKNOWN, START, STOP, RETRY, WAIT_FOR_BLUETOOTH, RETRY_AFTER_BLUETOOTH_REBIRTH, WATCH };

    static final String STATUS_CHANGED_ACTION = "status-changed";

    void setStatus(Status status) {
        this.status = status;
        logger.d("setState status=%s", status);
        Intent intent = new Intent(STATUS_CHANGED_ACTION);
        intent.putExtra("status", status.resId);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private boolean isStatusIn(Status... statusList) {
        for (Status check:
             statusList) {
            if (status == check)
                return true;
        }
        return false;
    }

    class ServiceHandler extends Handler {
        public static final int RETRY_DELAY = 5000;
        final FLScanner scanner = FLScannerFactory.getScanner(BleService.this);
        private boolean retrySceduled;

        ServiceHandler(Looper looper) {
            super(looper);
        }

        public void sendCommand(ServiceHandlerCommand command) {
            final Message msg = serviceHandler.obtainMessage(command.ordinal());
            sendMessage(msg);
        }

        public void sendCommandDelayed(ServiceHandlerCommand command, int millis) {
            final Message msg = serviceHandler.obtainMessage(command.ordinal());
            sendMessageDelayed(msg, millis);
        }

        @Override
        public void handleMessage(Message msg) {
            logger.d("handleMessage msg=%s means %s", msg, ServiceHandlerCommand.values()[msg.what]);

            switch (ServiceHandlerCommand.values()[msg.what]) {
                case START:
                    if (retrySceduled)
                        break;
                    else
                        System.out.print(true);
                case RETRY:
                    if (!isStatusIn(Status.WAITING_FOR_BLUETOOTH)) {
                        try {
                            device = scanner.findDevice();
                            setStatus(Status.STARTED);
                        } catch (FLScannerException e) {
                            setStatus(Status.BLUETOOTH_REQUESTED);
                        }

                        if (device == null) {
                            logger.v("no device found");
                            // retry in a few seconds
                            serviceHandler.sendCommandDelayed(ServiceHandlerCommand.RETRY, RETRY_DELAY);
                            retrySceduled = true;
                        } else {
                            serviceHandler.sendCommandDelayed(ServiceHandlerCommand.WATCH, 5000);
                            retrySceduled = false;
                        }
                    }
                    break;
                case WAIT_FOR_BLUETOOTH:
                    setStatus(Status.WAITING_FOR_BLUETOOTH);
                    break;
                case RETRY_AFTER_BLUETOOTH_REBIRTH:
                    setStatus(Status.STARTED);
                    serviceHandler.sendCommandDelayed(ServiceHandlerCommand.RETRY, RETRY_DELAY);
                    break;
                case WATCH:
                    if (device == null || device.isDead()) {
                        serviceHandler.sendCommandDelayed(ServiceHandlerCommand.RETRY, RETRY_DELAY);
                    }
                    break;
                case STOP:
                    deviceDisconnect();
                    setStatus(Status.STOPPING);
                    stopSelf();
                    break;
                default:
                    throw new IllegalArgumentException("unknown message argument");
            }
        }
    }

    class BTSystemBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            logger.d("onReceive context: %s, intent: %s", context, intent);
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        logger.v("Bluetooth off");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        logger.v("Turning Bluetooth off...");
                        serviceHandler.sendCommand(ServiceHandlerCommand.WAIT_FOR_BLUETOOTH);
                        break;
                    case BluetoothAdapter.STATE_ON:
                        logger.v("Bluetooth on");
                        serviceHandler.sendCommand(ServiceHandlerCommand.RETRY_AFTER_BLUETOOTH_REBIRTH);
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        logger.v("Turning Bluetooth on...");
                        break;
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        logger.d("onCreate");

        HandlerThread backgroundThread = new HandlerThread(
                "ServiceThread" + new Random().nextInt(),
                android.os.Process.THREAD_PRIORITY_BACKGROUND
        );
        backgroundThread.start();
        serviceHandler = new ServiceHandler(backgroundThread.getLooper());

        // act on bluetooth state change
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        broadcastReceiver = new BTSystemBroadcastReceiver();
        registerReceiver(broadcastReceiver, filter);
    }

    @Override
    public void onDestroy() {
        logger.d("onDestroy");

        unregisterReceiver(broadcastReceiver);
        setStatus(Status.STOPPED);
        serviceHandler.getLooper().quitSafely();
        deviceDisconnect();

        super.onDestroy();
    }

    synchronized private void deviceDisconnect() {
        if (device != null) {
            device.disconnect();
            device = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        logger.d("onBind");
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logger.d("onStartCommand intent=%s flags=%x startId=%d", intent, flags, startId);

        final ServiceHandlerCommand command;
        if (intent != null) {
            command = ServiceHandlerCommand.valueOf(intent.getAction());
        } else {
            command = ServiceHandlerCommand.START;
        }
        serviceHandler.sendCommand(command);

        return Service.START_STICKY;
    }
}
