package asia.eyekandi.emw;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.squareup.otto.Subscribe;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.inject.Inject;

import asia.eyekandi.emw.busevents.BluetoothNotEnabledEvent;
import asia.eyekandi.emw.busevents.ConnectionEvent;
import asia.eyekandi.emw.busevents.DeviceScannedEvent;
import asia.eyekandi.emw.busevents.IntensityChangedEvent;
import asia.eyekandi.emw.busevents.NewGaussEvent;
import asia.eyekandi.emw.busevents.NewSineEvent;
import asia.eyekandi.emw.busevents.NewStepEvent;
import asia.eyekandi.emw.busevents.ServerStateEvent;
import asia.eyekandi.emw.busevents.StopWandEvent;
import asia.eyekandi.emw.busevents.WandStartedEvent;
import asia.eyekandi.emw.busevents.WandStoppedEvent;
import asia.eyekandi.emw.di.EventBus;
import roboguice.util.Ln;

/**
 * Created by mitja on 08/02/16.
 * Copyright AMOK Products ApS
 */
public class BLEScanner2 {
    static final byte VERSION = 0x10;
    static final byte MODULE_MOTOR = 0x01;
    static final byte TIMBRE_LINEAR = 0x00;
    static final byte TIMBRE_GAUSSIAN = 0x02;

    static final int GAUSS_INTENSITY_CURRENT = 0xee;

    static final int STATE_DISCONNECTED = 0;
    static final int STATE_CONNECTING = 1;
    static final int STATE_CONNECTED = 2;
    static final int MESSAGE_ID_REPEATING = 0;
    static final int MESSAGE_ID_NONREPEATING = 1;
    private static final String LED_SERVICE_UUID = "1221";
    private static final String VERSION_UUID = "1222";
    private static final String CLIENT_AUTH_UUID = "1223";
    private static final String SERVER_AUTH_UUID = "1224";
    private static final String WRITE_BUFFER_SEGMENT_UUID = "1225";
    private static final String WRITE_BUFFER_LAST_UUID = "1226";
    private static final String READ_BUFFER_SEGMENT_UUID = "1227";
    private static final String READ_BUFFER_LAST_UUID = "1228";
    private static final String CLIENT_COMMAND_UUID = "1229";
    private static final String STATE_AND_STATUS_UUID = "1230";
    private static final UUID CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final long SCAN_PERIOD = 10000;
    public static int connectionState = ConnectionEvent.EVENT_SEARCHING;
    static protected volatile boolean valueWriteInProgress;
    @Inject
    EventBus bus;

    @Nullable BluetoothGattCharacteristic versionCharacteristic;
    @Nullable BluetoothGattCharacteristic clientAuthCharacteristic;
    @Nullable BluetoothGattCharacteristic serverAuthCharacteristic;
    @Nullable BluetoothGattCharacteristic writeSegmentCharacteristic;
    @Nullable BluetoothGattCharacteristic writeLastSegmentCharacteristic;
    @Nullable BluetoothGattCharacteristic readSegmentCharacteristic;
    @Nullable BluetoothGattCharacteristic readLastSegmentCharacteristic;
    @Nullable BluetoothGattCharacteristic clientCommandCharacteristic;
    @Nullable BluetoothGattCharacteristic stateAndStatusCharacteristic;

    @SuppressWarnings("unused")
    @Nullable
    BluetoothGattService wandService;
    // we should write stop at the next opportunity
    volatile boolean writeStop;
    byte MIC = 100;
    private static final byte MIC_UNCHANGED = (byte) 0xff;
    private static final byte MIC_FORCE_MAX = (byte) 0xee;
    int mConnectionState = STATE_DISCONNECTED;
    BluetoothDevice wandDevice;
    private MyApplication application;
    private boolean mScanning;
    private Handler mHandler = new Handler();
    @Nullable
    private BluetoothGatt mBluetoothGatt;
    private volatile boolean wandRunning;
    private int writeValuesIndex;
    private byte[][] writeValues;
    public boolean timeoutTest = false;
    /*
    private ConcurrentSkipListSet<BluetoothDevice> devices = new ConcurrentSkipListSet<>(new Comparator<BluetoothDevice>() {
        @Override
        public int compare(BluetoothDevice lhs, BluetoothDevice rhs) {
            return lhs.getAddress().compareTo(rhs.getAddress());
        }
    });
    */
    private Runnable stopScanningRunnable = new Runnable() {
        @Override
        public void run() {
            Ln.i("Could not find wand within time");
            restartScan();
        }
    };
    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            /*
            if (device.getName() == null) {
                Ln.i("onLeScan %s %d null", device, rssi);
                return;
            }
            Ln.i("onLeScan %s %d %s", device, rssi, device.getName());
            */
            // Binder_1 onLeScan 00:A0:50:03:23:14 -68 Europe Magic Wand
            if ("EMW-BT".equals(device.getName())) {
                Ln.i("Wand found");
                //devices.add(device);
                stopScan();
                wandDevice = device;
                bus.postOnMain(new DeviceScannedEvent(device.getAddress(), rssi));
            }
        }
    };
    private Runnable startScanningRunnable = new Runnable() {
        @Override
        public void run() {
            Ln.i("Starting scan again from runnable");
            startScan();
        }
    };
    // values to write after they've been written to segment write
    private Runnable nextWriteRunnable = new Runnable() {
        @Override
        public void run() {
            ++writeValuesIndex;
            BluetoothGattCharacteristic characteristic;
            if (writeValuesIndex < writeValues.length - 1) {
                characteristic = writeSegmentCharacteristic;
            }else {
                if (timeoutTest) {
                    characteristic = null;
                    timeoutTest = false;
                }else{
                    characteristic = writeLastSegmentCharacteristic;
                }
            }
            if (characteristic != null)
                characteristic.setValue(writeValues[writeValuesIndex]);
            Ln.i("Writing values #%d: %s", writeValuesIndex+1, Arrays.toString(writeValues[writeValuesIndex]));
            BluetoothGatt gatt = mBluetoothGatt;
            if (gatt != null && characteristic != null) {
                mBluetoothGatt.writeCharacteristic(characteristic);
            }
        }
    };
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Ln.i("onConnectionStateChanged %d %d", status, newState);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Ln.i("Gatt status not success: %d", status);
                restartScan();
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Ln.i("Connected to GATT server");
                List<BluetoothGattService> serviceList = gatt.getServices();
                if (serviceList.isEmpty() == false) {
                    Ln.e("TODO GATT already had services, maybe use them?");
                }
                boolean ret = gatt.discoverServices();
                if (ret == false) {
                    Ln.i("Could not start discovering services");
                    startScan();
                    return;
                }
                Ln.i("Discovering services");
                mConnectionState = STATE_CONNECTED;
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Ln.i("Disconnected");
                // Failed to connect? If you're feeling lucky you could call BluetoothGatt.connect.
                // Some OS versions require you to do this, others will naturally attempt to reconnect
                // until you call close or disconnect. But you have no idea whether or not that's the case,
                // so you just call connect, set a timer, and if X time elapses without another callback,
                // fully close the GATT and start over again.
                startScan();
            } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                Ln.i("Connecting");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                Ln.i("Disconnecting");
            }
        }

        private String getCharacteristicsUUID(BluetoothGattCharacteristic characteristic) {
            return characteristic.getUuid().toString().substring(4, 8).toUpperCase();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Ln.i("onServicesDiscovered");
                for (BluetoothGattService service : gatt.getServices()) {
                    String uuid = service.getUuid().toString().substring(4, 8).toUpperCase();
                    //Ln.i("Found service: %s", uuid);
                    if (uuid.equals(LED_SERVICE_UUID)) {
                        Ln.i("Found Wand service");
                        wandService = service;
                        int found = 0;
                        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                            String characteristicUuid = getCharacteristicsUUID(characteristic);
                            Ln.d("Found characteristic %s", characteristicUuid);
                            switch (characteristicUuid) {
                                case WRITE_BUFFER_LAST_UUID:
                                    found += 1;
                                    writeLastSegmentCharacteristic = characteristic;
                                    break;
                                case WRITE_BUFFER_SEGMENT_UUID:
                                    found += 1;
                                    writeSegmentCharacteristic = characteristic;
                                    break;
                                case STATE_AND_STATUS_UUID:
                                    stateAndStatusCharacteristic = characteristic;
                                    enableNotifications(gatt, characteristic);
                                    found += 1;
                                    break;
                                case READ_BUFFER_LAST_UUID:
                                    found += 1;
                                    readLastSegmentCharacteristic = characteristic;
                                    enableNotifications(gatt, characteristic);
                                    break;
                                case READ_BUFFER_SEGMENT_UUID:
                                    found += 1;
                                    readSegmentCharacteristic = characteristic;
                                    enableNotifications(gatt, characteristic);
                                    break;
                                case CLIENT_AUTH_UUID:
                                    clientAuthCharacteristic = characteristic;
                                    found += 1;
                                    break;
                                case CLIENT_COMMAND_UUID:
                                    clientCommandCharacteristic = characteristic;
                                    found += 1;
                                    break;
                                case SERVER_AUTH_UUID:
                                    serverAuthCharacteristic = characteristic;
                                    enableNotifications(gatt, characteristic);
                                    found += 1;
                                    break;
                            }
                        }
                        Ln.d("Found %d characteristics", found);
                        if (found >= 3) {
                            connectionState = ConnectionEvent.EVENT_CONNECTED;
                            bus.postOnMain(new ConnectionEvent(connectionState));
                        } else {
                            Ln.e("Didn't find all characteristices");
                        }
                    }
                }
            } else {
                Ln.i("onServicesDiscovered error: %d", status);
                startScan();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Ln.i("onCharacteristicRead %s %d %s", characteristic, status, Arrays.toString(characteristic.getValue()));
        }

        void enableNotifications(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            gatt.setCharacteristicNotification(characteristic, true);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            valueWriteInProgress = false;
            String uuid = getCharacteristicsUUID(characteristic);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] valBytes = characteristic.getValue();
                if (valBytes != null) {
                    Ln.i("onCharacteristicWrite uuid %s values: %s", uuid, Arrays.toString(valBytes));
                } else {
                    Ln.i("onCharacteristicWrite uuid %s unknown values", uuid);
                }
                if(WRITE_BUFFER_SEGMENT_UUID.equals(uuid)) {
                    Ln.d("Posting next write");
                    mHandler.post(nextWriteRunnable);
                }
            } else {
                Ln.i("onCharacteristicWrite failed? uuid %s", uuid);
            }
            if (writeStop) {
                writeReset();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String uuid = getCharacteristicsUUID(characteristic);
            if (STATE_AND_STATUS_UUID.equals(uuid)) {
                final byte[] value = characteristic.getValue();
                Ln.i("onCharacteristicChanged status %s", Arrays.toString(value));
                if (value.length < 7) {
                    Ln.e("Too short value");
                }
                bus.postOnMain(new ServerStateEvent(value));
            } else {
                Ln.e("Unknown characteristic changed %s", uuid);
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Ln.i("onDescriptorRead %s %d", descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Ln.i("onDescriptorWrite %s %d %s", descriptor, status, Arrays.toString(descriptor.getValue()));
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            Ln.i("onReliableWriteCompleted %d", status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Ln.i("onReadRemoteRssi %d %d", rssi, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Ln.i("onMtuChanged %d %d", mtu, status);
        }
    };

    @Inject
    public BLEScanner2(MyApplication application) {
        this.application = application;
        application.component().inject(this);
    }

    public static boolean isBLESupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    public static byte[] uint16ToBytes(int val) {
        if (val > 0xffff || val < 0) {
            throw new IllegalArgumentException("Bad int16");
        }
        byte valHigh = (byte) ((val >> 8) & 0xff);
        byte valLow = (byte) (val & 0xff);
        return new byte[]{valHigh, valLow};
    }

    public static boolean isValueWriteInProgress() {
        return valueWriteInProgress;
    }

    void onResume() {
        bus.register(this);
    }

    void onPause() {
        bus.unregister(this);
        stopScan();
        mHandler.removeCallbacks(startScanningRunnable);

        resetValues();
    }

    @Subscribe
    public void onStepFeature(NewStepEvent event) {
        Ln.i("Step feature: %s", event);
        writeGaussValue(GAUSS_INTENSITY_CURRENT, event.end, event.steep*20, 0xffff);
    }

    @Subscribe
    public void onIntensityChanged(IntensityChangedEvent event) {
        int runningIndex = WaveFormTextureView.getRunningThreadIndex();
        if(runningIndex == WaveFormTextureView.INVALID_THREAD_INDEX) {
            Ln.i("Not running");
            return;
        }
        int runningType = WaveFormTextureView.getRunningThreadType();
        Ln.i("Got Intensity change %f running thread %d", event.intensity, runningIndex);
        int amp = 0;
        switch (runningType) {
            case WaveFormTextureView.WAVETYPE_INDEX_LINE:
                Ln.i("New line start");
                int center = (int) (event.intensity*100);
                bus.post(new NewSineEvent(0, 0, center, runningIndex));
                break;
            case WaveFormTextureView.WAVETYPE_INDEX_GAUSS:
                CoordinateFeature feature = WaveFormTextureView.getFirstFeature();
                bus.post(new NewStepEvent(feature, runningIndex));
                break;
            case WaveFormTextureView.WAVETYPE_INDEX_HIGH:
                amp = (int) (25*event.intensity);
                bus.post(new NewSineEvent(amp,6000, 75, runningIndex));
                break;
            case WaveFormTextureView.WAVETYPE_INDEX_MID:
                amp = (int) (50*event.intensity);
                bus.post(new NewSineEvent(amp,6000, 50, runningIndex));
                break;
            case WaveFormTextureView.WAVETYPE_INDEX_LOW:
                amp = (int) (25*event.intensity);
                bus.post(new NewSineEvent(amp,6000, 25, runningIndex));
                break;
        }
        //Boolean threadState = WaveFormTextureView.runningThreadStates.get(event.threadIndex);
    }

    @Subscribe
    public void onStopWand(StopWandEvent event) {
        Ln.i("Got stopWandEvent");
        writeReset();
    }

    private void crashlog(String format) {
        Ln.i(format);
        Crashlytics.log(format);
    }

    private void crashlog(String format, Object... args) {
        Ln.i(format, args);
        Crashlytics.log(String.format(format, args));
    }

    @Subscribe
    public void newGauss(NewGaussEvent event) {
        Ln.i("Got new Gauss %d %d %d", event.start, event.end, event.steep);
        Boolean threadState = WaveFormTextureView.runningThreadStates.get(event.threadIndex);
        if (Boolean.TRUE.equals(threadState) == false) {
            Ln.e("Thread %d no longer running", event.threadIndex);
            return;
        }
        writeGaussValue(event.start, event.end, event.steep, 0);
    }

    @Subscribe
    public void newSine(NewSineEvent event) {
        Ln.i("Got new Sine %d %d %d", event.amp, event.center, event.period);
        Boolean threadState = WaveFormTextureView.runningThreadStates.get(event.threadIndex);
        if (Boolean.TRUE.equals(threadState) == false) {
            Ln.e("Thread %d no longer running", event.threadIndex);
            return;
        }
        writeSinValue(event.amp, event.period, event.center);
    }

    /*
    @SuppressLint("DefaultLocale")
    @Subscribe
    public void newValue(NewValueEvent event) {
        Ln.i(String.format("Got new value %f", event.value));
        Boolean threadState = WaveFormTextureView.runningThreadStates.get(event.index);
        if (Boolean.TRUE.equals(threadState) == false) {
            Ln.e("Thread %d no longer running", event.index);
            return;
        }
        int wandValue = (int) (event.value * 255);
        if (wandValue == 0) {
            // must use writeReset to stop wand
            wandValue = 1;
        }
        writeWandValue(wandValue);
    }
    */

    @Subscribe
    public void deviceScanned(DeviceScannedEvent event) {
        Ln.i("Found device %s", event.address);
        final BluetoothDevice device = wandDevice;
        if (device != null) {
            connectionState = ConnectionEvent.EVENT_CONNECTING;
            bus.post(new ConnectionEvent(connectionState));
            mBluetoothGatt = device.connectGatt(application, false, mGattCallback);
            mConnectionState = STATE_CONNECTING;
        } else {
            Ln.e("No wandDevice");
        }
        /*
        for (BluetoothDevice device : devices) {
            if (device.getAddress().equals(event.address)) {
                Ln.i("Connecting");
                mBluetoothGatt = device.connectGatt(application, false, mGattCallback);
                mConnectionState = STATE_CONNECTING;
            }
        }
        */
    }

    private void resetValues() {
        Ln.i("resetValues");
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
        }

        mBluetoothGatt = null;
        writeLastSegmentCharacteristic = null;
        writeSegmentCharacteristic = null;
        wandService = null;
        writeStop = false;
        valueWriteInProgress = false;
        wandDevice = null;
    }

    public void startScan() {
        Ln.i("startScan");
        if (mScanning == true) {
            Ln.i("Already scanning");
            return;
        }
        resetValues();

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Ln.i("No BT adapter");
            return;
        }
        if (adapter.isEnabled() == false) {
            Ln.i("Adapter not enabled");
            mConnectionState = STATE_DISCONNECTED;
            bus.postOnMain(new BluetoothNotEnabledEvent());
            return;
        }
        connectionState = ConnectionEvent.EVENT_SEARCHING;
        bus.postOnMain(new ConnectionEvent(connectionState));
        // Stops scanning after a pre-defined scan period.
        Ln.i("Starting scan");
        //noinspection deprecation
        adapter.startLeScan(leScanCallback);
        mHandler.removeCallbacks(stopScanningRunnable);
        mHandler.postDelayed(stopScanningRunnable, SCAN_PERIOD);
        mScanning = true;
    }

    public void restartScan() {
        Ln.i("Restarting scan in 1 second");
        stopScan();
        mHandler.postDelayed(startScanningRunnable, 1000);
    }

    void stopScan() {
        mHandler.removeCallbacks(stopScanningRunnable);
        mScanning = false;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            //noinspection deprecation
            adapter.stopLeScan(leScanCallback);
        }
    }

    // patternType is MESSAGE_ID_REPEATING or MESSAGE_ID_NONREPEATING
    public byte writeGaussValue2(int patternType, int start, int end, int steep, int duration, int start2, int end2, int steep2, int duration2) {
        if (valueWriteInProgress == true) {
            // let stop
            Ln.i("Still waiting for value to be written");
            return ServerStateEvent.PATTERN_ERROR_OR_IDLE;
        }
        // copy just in case they get nulled in another thread
        BluetoothGattCharacteristic characteristic = writeSegmentCharacteristic;
        BluetoothGatt gatt = mBluetoothGatt;
        Ln.i("Gauss values %d %d %d %d %d %d %d %d", start, end, steep, duration, start2, end2, steep2, duration2);
        if (characteristic == null || gatt == null) {
            Toast.makeText(application, "Not connected", Toast.LENGTH_LONG).show();
            Ln.e("Cannot write value");
            return ServerStateEvent.PATTERN_ERROR_OR_IDLE;
        }

        if(patternType != MESSAGE_ID_NONREPEATING && patternType != MESSAGE_ID_REPEATING) {
            Ln.e("Bad pattern type");
            return ServerStateEvent.PATTERN_ERROR_OR_IDLE;
        }

        if (start > 100) {
            start = 100;
        } else if (start < 0) {
            start = 0;
        }

        if (end > 100) {
            end = 100;
        } else if (end < 0) {
            end = 0;
        }

        steep = steep / 20;
        if (steep > 255) {
            steep = 255;
        } else if (steep <= 0) {
            steep = 1;
        }

        byte[] durationBytes = uint16ToBytes(duration);
        byte[] durationBytes2 = uint16ToBytes(duration2);
        byte patternId = generatePatternId();

        byte[] writeValue = makeDatagram(new byte[]{
                MODULE_MOTOR,
                (byte) patternType,
                TIMBRE_GAUSSIAN,
                patternId,
                MIC_UNCHANGED, // MIC (100% pattern)
                (byte) start,
                (byte) end,
                durationBytes[0], // durationHigh
                durationBytes[1], // durationLow
                (byte) steep,
                (byte) start2,
                (byte) end2,
                durationBytes2[0],
                durationBytes2[1],
                (byte) steep2
        });

        characteristic.setValue(writeValue);
        valueWriteInProgress = true;
        writeValues = new byte[][] {
                Arrays.copyOfRange(writeValue, 0, 7),
                Arrays.copyOfRange(writeValue, 7, 14),
                Arrays.copyOfRange(writeValue, 14, writeValue.length)
        };
        writeValuesIndex = 0;
        characteristic.setValue(writeValues[0]);
        Ln.i("Writing values %s", Arrays.toString(writeValues));
        gatt.writeCharacteristic(characteristic);
        return patternId;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
    private byte[] makeDatagram(byte[] body) {
        if (body.length > 0xff) {
            throw new IllegalArgumentException("Too long body");
        }
        byte[] header = new byte[]{
                VERSION,
                (byte) body.length
        };
        return concat(header, body);
    }

    public void writeGaussValue(int start, int end, int steep, int duration) {
        if (valueWriteInProgress == true) {
            // let stop
            Ln.i("Still waiting for value to be written");
            return;
        }
        // copy just in case they get nulled in another thread
        BluetoothGattCharacteristic characteristic = writeLastSegmentCharacteristic;
        BluetoothGatt gatt = mBluetoothGatt;
        Ln.i("Maybe Writing values %d %d %d", start, end, steep);
        if (characteristic == null || gatt == null) {
            Toast.makeText(application, "Not connected", Toast.LENGTH_LONG).show();
            Ln.e("Cannot write value");
            return;
        }

        if (start > 100 && start != GAUSS_INTENSITY_CURRENT) {
            start = 100;
        } else if (start < 0) {
            start = 0;
        }

        if (end > 100) {
            end = 100;
        } else if (end < 0) {
            end = 0;
        }

        steep = steep / 20;
        if (steep > 255) {
            steep = 255;
        } else if (steep <= 0) {
            steep = 1;
        }

        byte[] durationBytes = uint16ToBytes(duration);
        byte patternId = generatePatternId();

        byte[] writeValue = makeDatagram(new byte[]{
                MODULE_MOTOR,
                (byte) MESSAGE_ID_REPEATING,
                TIMBRE_GAUSSIAN,
                patternId,
                MIC_UNCHANGED, // MIC (100% pattern)
                (byte) start,
                (byte) end,
                durationBytes[0], // durationHigh
                durationBytes[1], // durationLow
                (byte) steep,
        });
        characteristic.setValue(writeValue);
        Ln.i("Writing values %d %d %d", start, end, steep);
        valueWriteInProgress = true;
        gatt.writeCharacteristic(characteristic);
        bus.postOnMain(new WandStartedEvent());
    }

    public byte writeSinValue(int amp, int period, int center) {
        if (valueWriteInProgress == true) {
            // let stop
            Ln.i("Still waiting for value to be written");
            return ServerStateEvent.PATTERN_ERROR_OR_IDLE;
        }
        // copy just in case they get nulled in another thread
        BluetoothGattCharacteristic characteristic = writeLastSegmentCharacteristic;
        BluetoothGatt gatt = mBluetoothGatt;
        Ln.i("Maybe writing values %d %d %d", amp, center, period);
        if (characteristic == null || gatt == null) {
            Toast.makeText(application, "Not connected", Toast.LENGTH_LONG).show();
            Ln.e("Cannot write value");
            return ServerStateEvent.PATTERN_ERROR_OR_IDLE;
        }

        if (amp > 100) {
            amp = 100;
        } else if (amp < 0) {
            amp = 0;
        }

        if (center > 100) {
            center = 100;
        } else if (center < 0) {
            center = 0;
        }

        if (period > 0xffff) {
            period = 0xffff;
        } else if (period < 0) {
            period = 0;
        }

        byte[] periodBytes = uint16ToBytes(period);

        byte patternId = generatePatternId();

        final byte[] writeValue = new byte[]{
                0x10, // version 1.0
                0x09, // length
                0x01, // module id
                0x00, // msg id
                0x01, // timbre
                patternId,
                MIC_UNCHANGED, // MIC (100% pattern)
                (byte) amp,
                (byte) center,
                periodBytes[0],
                periodBytes[1]
        };
        valueWriteInProgress = true;
        characteristic.setValue(writeValue);
        Ln.i("Writing values %d %d %d", amp, center, period);
        gatt.writeCharacteristic(characteristic);
        bus.postOnMain(new WandStartedEvent());
        return patternId;
    }

    void writeReset() {
        if (valueWriteInProgress == true) {
            Ln.i("Waiting for value to be written, stopping at next notification");
            writeStop = true;
            return;
        }
        writeStop = false;
        BluetoothGattCharacteristic characteristic = writeLastSegmentCharacteristic;
        BluetoothGatt gatt = mBluetoothGatt;
        if (characteristic == null || gatt == null) {
            Ln.e("Cannot write value");
            return;
        }
        final byte[] writeValue = new byte[]{0x10, 0x02, 0x0, 0x0};
        characteristic.setValue(writeValue);
        gatt.writeCharacteristic(characteristic);

        bus.postOnMain(new WandStoppedEvent());
    }

    private byte generatePatternId() {
        // 1 and 2 will be generated more often but who cares
        byte[] patternId = new byte[1];
        new Random().nextBytes(patternId);
        if (patternId[0] == (byte) ServerStateEvent.PATTERN_ERROR_OR_IDLE) {
            return 1;
        }
        if (patternId[0] == ServerStateEvent.PATTERN_SERVER) {
            return 2;
        }
        return patternId[0];
    }

    int getConnectionState() {
        return mConnectionState;
    }

    // returns the randomly generated pattern id
    public byte writeNonRepeating2(int begin1, int begin2, int end1, int end2, int transition1, int transition2) {
        if (valueWriteInProgress == true) {
            // let stop
            Ln.i("Still waiting for value to be written");
            return ServerStateEvent.PATTERN_ERROR_OR_IDLE;
        }
        // copy just in case they get nulled in another thread
        BluetoothGattCharacteristic firstCharacteristic = writeSegmentCharacteristic;
        BluetoothGattCharacteristic lastCharacteristic = writeLastSegmentCharacteristic;
        BluetoothGatt gatt = mBluetoothGatt;
        Ln.i("Maybe writing values %d %d %d %d %d %d", begin1, begin2, end1, end2, transition1, transition2);
        if (firstCharacteristic == null || lastCharacteristic == null || gatt == null) {
            Toast.makeText(application, "Not connected", Toast.LENGTH_LONG).show();
            Ln.e("Cannot write value");
            return ServerStateEvent.PATTERN_ERROR_OR_IDLE;
        }

        byte[] transition1Bytes = uint16ToBytes(transition1);
        byte[] transition2Bytes = uint16ToBytes(transition2);
        byte patternId = generatePatternId();

        final byte[] writeValue = makeDatagram(new byte[]{
                MODULE_MOTOR, // module id
                MESSAGE_ID_NONREPEATING, // msg id
                TIMBRE_LINEAR, // timbre - linear
                patternId, // pattern identifier
                MIC_UNCHANGED, // MIC (100% pattern)
                (byte) begin1,
                (byte) end1,
                transition1Bytes[0],
                transition1Bytes[1],
                (byte) begin2,
                (byte) end2,
                transition2Bytes[0],
                transition2Bytes[1]
        });
        valueWriteInProgress = true;
        writeValues = new byte[][] {
                Arrays.copyOfRange(writeValue, 0, 7),
                Arrays.copyOfRange(writeValue, 7, 14),
                Arrays.copyOfRange(writeValue, 14, writeValue.length)
        };
        writeValuesIndex = 0;
        firstCharacteristic.setValue(writeValues[0]);
        Ln.i("Writing values %s", Arrays.toString(writeValues));
        gatt.writeCharacteristic(firstCharacteristic);

        //bus.postOnMain(new WandStartedEvent());
        return patternId;
    }

    public void sendMICChange() {
        final byte[] write1Value = new byte[]{
                0x10, // version 1.0
                3, // length
                0x01, // module id
                0x04, // msg id
                MIC
        };
        //valueWriteInProgress = true;
        BluetoothGattCharacteristic characteristic = writeLastSegmentCharacteristic;
        BluetoothGatt gatt = mBluetoothGatt;
        if(characteristic != null && gatt != null) {
            characteristic.setValue(write1Value);
            Ln.i("Writing values %s", Arrays.toString(write1Value));
            gatt.writeCharacteristic(characteristic);
        }
    }

    public byte writeRepeating2(int begin1, int begin2, int end1, int end2, int transition1, int transition2) {
        if (valueWriteInProgress == true) {
            // let stop
            Ln.i("Still waiting for value to be written");
            return ServerStateEvent.PATTERN_ERROR_OR_IDLE;
        }
        // copy just in case they get nulled in another thread
        BluetoothGattCharacteristic firstCharacteristic = writeSegmentCharacteristic;
        BluetoothGattCharacteristic lastCharacteristic = writeLastSegmentCharacteristic;
        BluetoothGatt gatt = mBluetoothGatt;
        Ln.i("Maybe writing values %d %d %d %d %d %d", begin1, begin2, end1, end2, transition1, transition2);
        if (firstCharacteristic == null || lastCharacteristic == null || gatt == null) {
            Toast.makeText(application, "Not connected", Toast.LENGTH_LONG).show();
            Ln.e("Cannot write value");
            return ServerStateEvent.PATTERN_ERROR_OR_IDLE;
        }

        byte[] transition1Bytes = uint16ToBytes(transition1);
        byte[] transition2Bytes = uint16ToBytes(transition2);
        byte patternId = generatePatternId();

        final byte[] writeValue = makeDatagram(new byte[]{
                MODULE_MOTOR,
                MESSAGE_ID_REPEATING,
                0x00, // timbre - linear
                patternId, // pattern identifier
                MIC_UNCHANGED, // MIC (100% pattern)
                (byte) begin1,
                (byte) end1,
                transition1Bytes[0],
                transition1Bytes[1],
                (byte) begin2,
                (byte) end2,
                transition2Bytes[0],
                transition2Bytes[1]
        });
        valueWriteInProgress = true;
        writeValues = new byte[][] {
                Arrays.copyOfRange(writeValue, 0, 7),
                Arrays.copyOfRange(writeValue, 7, 14),
                Arrays.copyOfRange(writeValue, 14, writeValue.length)
        };
        writeValuesIndex = 0;
        firstCharacteristic.setValue(writeValues[0]);
        Ln.i("Writing values %s", Arrays.toString(writeValues[0]));
        gatt.writeCharacteristic(firstCharacteristic);
        //bus.postOnMain(new WandStartedEvent());
        return patternId;
    }
}
