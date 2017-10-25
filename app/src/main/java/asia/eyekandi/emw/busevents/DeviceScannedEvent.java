package asia.eyekandi.emw.busevents;

/**
 * Created by mitja on 07/02/16.
 * Copyright(C) AMOK Products ApS
 */
public class DeviceScannedEvent {
    public final String address;
    public final int rssi;

    public DeviceScannedEvent(String address, int rssi) {
        this.address = address;
        this.rssi = rssi;
    }
}
