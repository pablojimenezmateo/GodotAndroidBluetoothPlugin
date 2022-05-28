package com.example.godotbluetooth344;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BluetoothManager extends GodotPlugin {

    // General
    private Context context;
    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
    private LocationManager locationManager;
    private Handler handler = new Handler();
    private static final long SCAN_PERIOD = 10000;
    private BluetoothGatt bluetoothGatt; // This is a reference to the connected device

    // Specific
    private boolean scanning = false;
    private Map<String, ScanResult> devices = new HashMap<String, ScanResult>(); // Key is the address

    // Permissions related functions
    public boolean hasLocationPermissions() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            return false;
        } else {
            return true;
        }
    }

    public BluetoothManager(Godot godot) {

        super(godot);

        // Get the context
        this.context = getActivity().getApplicationContext();

        // Get the location manager
        this.locationManager = (LocationManager)this.context.getSystemService(Context.LOCATION_SERVICE);

        // Register the listener to the Bluetooth Status
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(mReceiver, filter);

        // Register the listener to the Location Status
        filter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
        context.registerReceiver(mGpsSwitchStateReceiver, filter);
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GodotBluetooth344";
    }

    @SuppressWarnings("deprecation")
    @NonNull
    @Override
    public List<String> getPluginMethods() {
        return Arrays.asList("sendDebugSignal",
                "bluetoothStatus",
                "scan",
                "stopScan",
                "hasLocationPermissions",
                "locationStatus",
                "connect",
                "disconnect");
    }

    public void sendDebugSignal(String s) {

        emitSignal("_on_debug_message", s);
    }

    public void sendNewDevice(ScanResult newDevice) {

        org.godotengine.godot.Dictionary deviceData = new org.godotengine.godot.Dictionary();

        deviceData.put("name", newDevice.getScanRecord().getDeviceName());
        deviceData.put("address", newDevice.getDevice().getAddress());
        deviceData.put("rssi", newDevice.getRssi());

        emitSignal("_on_device_found", deviceData);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo>  signals = new ArraySet<>();

        signals.add(new SignalInfo("_on_debug_message", String.class));
        signals.add(new SignalInfo("_on_device_found", org.godotengine.godot.Dictionary.class));
        signals.add(new SignalInfo("_on_bluetooth_status_change", String.class));
        signals.add(new SignalInfo("_on_location_status_change", String.class));
        signals.add(new SignalInfo("_on_connection_status_change", String.class));
        signals.add(new SignalInfo("_on_characteristic_read", org.godotengine.godot.Dictionary.class));

        return signals;
    }

    @SuppressLint("MissingPermission")
    public void scan() {

        if (hasLocationPermissions()) {

            if (!scanning) {

                // Stops scanning after a predefined scan period.
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        sendDebugSignal("Stopping scan");

                        bluetoothLeScanner.stopScan(leScanCallback);
                    }
                }, SCAN_PERIOD);

                sendDebugSignal("Start scan");

                scanning = true;
                bluetoothLeScanner.startScan(leScanCallback);
            }
        } else {
            sendDebugSignal("Cannot start a scan because you do not have location permissions");
        }
    }

    @SuppressLint("MissingPermission")
    public void stopScan() {

        if (scanning) {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    private ScanCallback leScanCallback =
            new ScanCallback() {

                @Override
                public void onScanResult(int callbackType, ScanResult result) {

                    // We are only interested in devices with name
                    if (result != null && result.getDevice() != null && result.getDevice().getAddress() != null && result.getScanRecord().getDeviceName() != null) {

                        if (!devices.containsKey(result.getDevice().getAddress())) {

                            devices.put(result.getDevice().getAddress(), result);
                            sendNewDevice(result);
                        }
                    }
                }
            };

    // Status functions
    public boolean bluetoothStatus() {

        return mBluetoothAdapter.isEnabled();
    }

    public boolean locationStatus() {

        Boolean gps_enabled = false;
        Boolean network_enabled = false;

        try {
            gps_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
        }

        try {
            network_enabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
        }

        if (!gps_enabled && !network_enabled) {
            return false;
        }
        return true;
    }

    // This monitors the bluetooth status
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        emitSignal("_on_bluetooth_status_change", "off");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        emitSignal("_on_bluetooth_status_change", "turning_off");
                        break;
                    case BluetoothAdapter.STATE_ON:
                        emitSignal("_on_bluetooth_status_change", "on");
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        emitSignal("_on_bluetooth_status_change", "turning_on");
                        break;
                }
            }
        }
    };

    // This monitors the location status
    private BroadcastReceiver mGpsSwitchStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(LocationManager.PROVIDERS_CHANGED_ACTION)) {

                boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

                if (isGpsEnabled || isNetworkEnabled) {
                    emitSignal("_on_location_status_change", "on");
                } else {
                    emitSignal("_on_location_status_change", "off");
                }
            }
        }
    };

    // Device connect call back
    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        @Override
        // Called when a devices connects or disconnects
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {

            if (status == BluetoothGatt.GATT_SUCCESS) {

                switch (newState) {
                    case BluetoothProfile.STATE_DISCONNECTED:

                        emitSignal("_on_connection_status_change", "disconnected");
                        break;
                    case BluetoothProfile.STATE_CONNECTED:
                        emitSignal("_on_connection_status_change", "connected");

                        // Discover services and characteristics for this device
                        bluetoothGatt.discoverServices();
                        break;
                }
            } else { // There was an issue connecting

                sendDebugSignal(Integer.toString(status));
            }
        }

        @Override
        // Called after a BluetoothGatt.discoverServices() call
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {

            sendDebugSignal("onServicesDiscovered");
            listServicesAndCharacteristics(bluetoothGatt.getServices());
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         //byte[] value, For Android Tiramisu we need this
                                         int status) {

            sendDebugSignal("onCharacteristicRead");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                sendDebugSignal("onCharacteristicRead: SUCCESS");
            }
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            sendDebugSignal("onCharacteristicWrite");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                //broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                sendDebugSignal("onCharacteristicWrite: SUCCESS");
            }
        }

        @Override
        // Result of a characteristic read/write operation
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic
                                            //,byte[] value, For Android Tiramisu we need this
        ) {
            sendDebugSignal("onCharacteristicChanged");
        }
    };

    @SuppressLint("MissingPermission")
    public void connect(String address) {
        sendDebugSignal("Connecting to device with address " + address);
        bluetoothGatt = devices.get(address).getDevice().connectGatt(context, false, btleGattCallback);
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        sendDebugSignal("Disconnecting device");
        bluetoothGatt.disconnect();
    }

    private void listServicesAndCharacteristics(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {

            final String serviceUuid = gattService.getUuid().toString();

            sendDebugSignal("Service discovered: " + serviceUuid);

            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();

            org.godotengine.godot.Dictionary characteristicData;

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic :
                    gattCharacteristics) {

                characteristicData = new org.godotengine.godot.Dictionary();

                final String characteristicUuid = gattCharacteristic.getUuid().toString();

                characteristicData.put("service_uuid", serviceUuid);
                characteristicData.put("characteristic_uuid", characteristicUuid);
                characteristicData.put("real_mask", gattCharacteristic.getProperties());

                // Set all 3 properties to false
                characteristicData.put("readable", false);
                characteristicData.put("writable", false);
                characteristicData.put("writable_no_response", false);


                if ((gattCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                    characteristicData.put("readable", true);
                }

                if ((gattCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                    characteristicData.put("writable", true);
                }

                if ((gattCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    characteristicData.put("writable_no_response", true);
                }

                sendDebugSignal("Characteristic discovered for service: " + characteristicUuid);
                emitSignal("_on_characteristic_read", characteristicData);
            }
        }
    }
}

/*


Received F3:60:26:8A:AD:78
Received Take

 */