package de.silberkoepfe.forumsladercompanion;

import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.support.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import static android.content.Context.BLUETOOTH_SERVICE;
import static org.junit.Assert.*;

public class FLScannerBleImplTest {
    private Context context;

    @Before
    public void setup() {
        context = InstrumentationRegistry.getContext();
    }
    @Test
    public void testScan() {
        final FLScannerBleImpl flScanner = new FLScannerBleImpl(context);
    }
}