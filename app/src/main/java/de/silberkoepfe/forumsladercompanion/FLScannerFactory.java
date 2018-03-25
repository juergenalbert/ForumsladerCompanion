package de.silberkoepfe.forumsladercompanion;

import android.content.Context;

public class FLScannerFactory {
    public static FLScanner getScanner(BleService bleService) {
        return FLScannerBleImpl.getInstance(bleService);
    }
}
