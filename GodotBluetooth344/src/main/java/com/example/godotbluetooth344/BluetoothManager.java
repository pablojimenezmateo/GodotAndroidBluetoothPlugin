package com.example.godotbluetooth344;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
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
import java.util.UUID;

public class BluetoothManager extends GodotPlugin {

    // General
    private Context context;
    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
    private LocationManager locationManager;
    private Handler handler = new Handler();

    private int ScanPeriod = 10000;
    private BluetoothGatt bluetoothGatt; // This is a reference to the connected device

    // Specific
    private boolean scanning = false;
    private boolean connected = false;
    private Map<String, ScanResult> devices = new HashMap<String, ScanResult>(); // Key is the address


    private boolean reportDuplicates = true;


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
        this.locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);

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

    public void setReportDuplicates(boolean report)
    {
        reportDuplicates = report;
    }

    public boolean getReportDuplicates()
    {
        return reportDuplicates;
    }

    public void setScanPeriod(int scanPeriod)
    {
        ScanPeriod = scanPeriod;
    }

    public int getScanPeriod() { return ScanPeriod; }


    public boolean hasGetScanPeriod() { return true; }

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
                "disconnect",
                "listServicesAndCharacteristics",
                "subscribeToCharacteristic",
                "unsubscribeFromCharacteristic",
                "writeBytesToCharacteristic",
                "writeStringToCharacteristic",
                "readFromCharacteristic",
                "setScanPeriod",
                "getScanPeriod",
                "hasGetScanPeriod",
                "setReportDuplicates",
                "getReportDuplicates");
    }

    public void sendDebugSignal(String s) {

        emitSignal("_on_debug_message", s);
    }

    public void sendNewDevice(ScanResult newDevice) {

        org.godotengine.godot.Dictionary deviceData = new org.godotengine.godot.Dictionary();

        deviceData.put("name", newDevice.getScanRecord().getDeviceName());
        deviceData.put("address", newDevice.getDevice().getAddress());
        deviceData.put("rssi", newDevice.getRssi());
        deviceData.put("manufacturerData", newDevice.getScanRecord().getBytes());

        emitSignal("_on_device_found", deviceData);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new ArraySet<>();

        signals.add(new SignalInfo("_on_debug_message", String.class));
        signals.add(new SignalInfo("_on_device_found", org.godotengine.godot.Dictionary.class));
        signals.add(new SignalInfo("_on_bluetooth_status_change", String.class));
        signals.add(new SignalInfo("_on_location_status_change", String.class));
        signals.add(new SignalInfo("_on_connection_status_change", String.class));
        signals.add(new SignalInfo("_on_characteristic_finding", String.class));
        signals.add(new SignalInfo("_on_characteristic_found", org.godotengine.godot.Dictionary.class));
        signals.add(new SignalInfo("_on_characteristic_read", org.godotengine.godot.Dictionary.class));
        signals.add(new SignalInfo("_on_scan_stopped", String.class));
        return signals;
    }

    public void scan() {
        if (hasLocationPermissions()) {
            if (!scanning) {
                // Stops scanning after a predefined scan period.
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;

                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {

                            sendDebugSignal("Cannot stop a scan because you do not have Manifest.permission.BLUETOOTH_SCAN");
                            return;
                        }
                        bluetoothLeScanner.stopScan(leScanCallback);
                        emitSignal("_on_scan_stopped", "scanTimedOut");

                    }
                }, ScanPeriod);

                scanning = true;
                //ScanSettings settings = new ScanSettings.Builder();


                bluetoothLeScanner.startScan(leScanCallback);
            }
        } else {
            sendDebugSignal("Cannot start a scan because you do not have location permissions");
        }
    }

    public void stopScan() {

        if (scanning) {
            scanning = false;
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {

                sendDebugSignal("Cannot stop a scan because you do not have Manifest.permission.BLUETOOTH_SCAN");
                return;
            }
            //emitSignal("_on_scan_stopped", "stopScan");
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
                        } else {
                             if (reportDuplicates) {
                                sendNewDevice(result);
                            }
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

    public void listServicesAndCharacteristics() {

        if (connected) {
            // Discover services and characteristics for this device
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                sendDebugSignal("Cannot list services because you do not have Manifest.permission.BLUETOOTH_CONNECT");

                return;
            }

            bluetoothGatt.discoverServices();
        }
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

                if (isGpsEnabled) {

                    emitSignal("_on_location_status_change", "on");
                } else if (isNetworkEnabled) {

                    emitSignal("_on_location_status_change", "on");
                } else {

                    emitSignal("_on_location_status_change", "off");
                }
            }
        }
    };

    // Device connect call back
    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {

        @Override
        // Called when a devices connects or disconnects
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {

            if (status == BluetoothGatt.GATT_SUCCESS) {

                switch (newState) {
                    case BluetoothProfile.STATE_DISCONNECTED:

                        emitSignal("_on_connection_status_change", "disconnected");
                        connected = false;

                        break;
                    case BluetoothProfile.STATE_CONNECTED:
                        connected = true;
                        // Read services and characteristics
                        listServicesAndCharacteristics();

                        emitSignal("_on_connection_status_change", "connected");

                        break;
                }
            } else { // There was an issue connecting

                sendDebugSignal(Integer.toString(status));
            }
        }

        @Override
        // Called after a BluetoothGatt.discoverServices() call
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {

            sendServicesAndCharacteristics(bluetoothGatt.getServices());
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
            } else {

                sendDebugSignal("onCharacteristicRead: " + Integer.toString(status));
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

            org.godotengine.godot.Dictionary data = new org.godotengine.godot.Dictionary();

            String characteristic_uuid = characteristic.getUuid().toString();
            String service_uuid = characteristic.getService().getUuid().toString();
            byte[] bytes = characteristic.getValue();

            sendDebugSignal("onCharacteristicChanged " + characteristic_uuid);

            data.put("service_uuid", service_uuid);
            data.put("characteristic_uuid", characteristic_uuid);
            data.put("bytes", bytes);

            emitSignal("_on_characteristic_read", data);
        }
    };

    public void connect(String address) {

        if (!connected) {
            sendDebugSignal("Connecting to device with address " + address);
            stopScan();
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                sendDebugSignal("Cannot connect because you do not have Manifest.permission.BLUETOOTH_CONNECT");

                return;
            }
            bluetoothGatt = devices.get(address).getDevice().connectGatt(context, false, btleGattCallback);
        }
    }

    public void disconnect() {

        if (connected) {
            sendDebugSignal("Disconnecting device");
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                sendDebugSignal("Cannot disconnect because you do not have Manifest.permission.BLUETOOTH_CONNECT");

                return;
            }
            bluetoothGatt.disconnect();
        }
    }

    private void sendServicesAndCharacteristics(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;

        emitSignal("_on_characteristic_finding", "processing");

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {

            final String serviceUuid = gattService.getUuid().toString();

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

                emitSignal("_on_characteristic_found", characteristicData);
            }
        }

        emitSignal("_on_characteristic_finding", "done");
    }

    // Read from characteristic
    private void readFromCharacteristic(String serviceUUID, String characteristicUUID) {

        if (connected) {

            UUID service = UUID.fromString(serviceUUID);
            UUID characteristic = UUID.fromString(characteristicUUID);

            BluetoothGattCharacteristic c = bluetoothGatt.getService(service).getCharacteristic(characteristic);

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                sendDebugSignal("Cannot read characteristics because you do not have Manifest.permission.BLUETOOTH_CONNECT");

                return;
            }

            bluetoothGatt.readCharacteristic(c);
        }
    }

    // Write bytes to characteristic, automatically detects the write type
    private void writeBytesToCharacteristic(String serviceUUID, String characteristicUUID, byte[] data) {

        if (connected) {

            UUID service = UUID.fromString(serviceUUID);
            UUID characteristic = UUID.fromString(characteristicUUID);

            BluetoothGattCharacteristic c = bluetoothGatt.getService(service).getCharacteristic(characteristic);
            c.setValue(data);

            if (c.getWriteType() == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {

                c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

            } else if (c.getWriteType() == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {

                c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            }

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                sendDebugSignal("Cannot write characteristic because you do not have Manifest.permission.BLUETOOTH_CONNECT");

                return;
            }
            bluetoothGatt.writeCharacteristic(c);
        }
    }

    // Write bytes to characteristic, automatically detects the write type
    private void writeStringToCharacteristic(String serviceUUID, String characteristicUUID, String data) {

        if (connected) {

            UUID service = UUID.fromString(serviceUUID);
            UUID characteristic = UUID.fromString(characteristicUUID);

            BluetoothGattCharacteristic c = bluetoothGatt.getService(service).getCharacteristic(characteristic);
            c.setValue(data);

            if (c.getWriteType() == BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {

                c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

            } else if (c.getWriteType() == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {

                c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            }

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                sendDebugSignal("Cannot write characteristic because you do not have Manifest.permission.BLUETOOTH_CONNECT");

                return;
            }
            bluetoothGatt.writeCharacteristic(c);
        }
    }

    // Subscribe to characteristic
    private void subscribeToCharacteristic(String serviceUUID, String characteristicUUID) {

        if (connected) {

            UUID service = UUID.fromString(serviceUUID);
            UUID characteristic = UUID.fromString(characteristicUUID);

            BluetoothGattCharacteristic c = bluetoothGatt.getService(service).getCharacteristic(characteristic);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                sendDebugSignal("Cannot subscribe to characteristic because you do not have Manifest.permission.BLUETOOTH_CONNECT");

                return;
            }
            bluetoothGatt.setCharacteristicNotification(c, true);

            // Set the Client Characteristic Config Descriptor to allow server initiated updates
            UUID CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
            BluetoothGattDescriptor desc = c.getDescriptor(CONFIG_DESCRIPTOR);
            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(desc);

        }
    }

    private void unsubscribeFromCharacteristic(String serviceUUID, String characteristicUUID) {

        if (connected) {

            UUID service = UUID.fromString(serviceUUID);
            UUID characteristic = UUID.fromString(characteristicUUID);

            // Set the Client Characteristic Config Descriptor to disable server initiated updates
            UUID CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
            BluetoothGattCharacteristic c = bluetoothGatt.getService(service).getCharacteristic(characteristic);

            BluetoothGattDescriptor desc = c.getDescriptor(CONFIG_DESCRIPTOR);
            desc.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                sendDebugSignal("Cannot unsubscribe from characteristic because you do not have Manifest.permission.BLUETOOTH_CONNECT");

                return;
            }

            bluetoothGatt.writeDescriptor(desc);
        }
    }
}