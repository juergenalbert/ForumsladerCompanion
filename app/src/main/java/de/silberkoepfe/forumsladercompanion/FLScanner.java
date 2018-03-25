package de.silberkoepfe.forumsladercompanion;

public interface FLScanner {

    FLDevice findDevice() throws FLScannerException;
    boolean isScanning();
}
