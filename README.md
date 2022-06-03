# Godot Android Bluetooth Plugin

This is the Android Studio project that implements all the logic to use Bluetooth LTE from Godot. Checkout the [Godot demo](https://github.com/pablojimenezmateo/GodotAndroidBluetoothDemo) to see a working example.

## Software versions

This versions have been tested:

* Android Studio: Bumblebee 2021.1.1 Patch 1
* Godot: 3.4.4
* Android: 11

## Initialize GodotBluetooth
To use the module functions on your scripts, start the module as follows: 

```GDScript

var bluetooth

func _ready():
	if Engine.has_singleton("GodotBluetooth344"):
		GodotBluetooth344 = Engine.get_singleton("GodotBluetooth344")
```

## API


### Signals

**_on_debug_message**

```GDScript
GodotBluetooth344.connect("_on_debug_message", self, "_on_debug_message")
```

This signal sends extra information from the Android Plugin.

Received arguments:
* string: Debug message
___

**_on_device_found**

```GDScript
GodotBluetooth344.connect("_on_device_found", self, "_on_device_found")
```

This signal is called every time a new device is found.

Received arguments:
* dictionary: Represents the information of the device, has the following keys:
	* address: MAC address of the device
	* name: Name of the device
	* rssi: Signal strength of the device in dBm
___

**_on_bluetooth_status_change**

```GDScript
GodotBluetooth344.connect("_on_bluetooth_status_change", self, "_on_bluetooth_status_change")
```

This signal is called every time the Bluetooth Status is changed (e.g. changes from On to Off)

Received arguments:
* string: Represents the current status of the Bluetooth Adapter, can have the following values:
	* on
	* turning_on
	* off
	* turning_off
___

**_on_location_status_change**

```GDScript
GodotBluetooth344.connect("_on_location_status_change", self, "_on_location_status_change")
```

This signal is called every time the Location Status is changed (e.g. changes from On to Off)

Received arguments:
* string: Represents the current status of the Location Adapter, can have the following values:
	* on
	* off
___

**_on_connection_status_change**

```GDScript
GodotBluetooth344.connect("_on_connection_status_change", self, "_on_connection_status_change")
```

This signal is called every time the Connection Status is changed (e.g. changes from Connected to Disconnected)

Received arguments:
* string: Represents the current status of the Connection Status, can have the following values:
	* connected
	* disconnected
	* Other value, this means an error, check https://developer.android.com/reference/android/bluetooth/BluetoothGatt.html#constants_2 for more information
___

**_on_characteristic_found**

```GDScript
GodotBluetooth344.connect("_on_characteristic_found", self, "_on_characteristic_found")
```

This signal is called every time the we discover a characteristic

Received arguments:
* dictionary: Represents the information of the characteristic, it has the following values:
	* service_uuid: The serice UUID
	* characteristic_uuid: The characteristic UUID
	* real_mask: The mask of the characteristic, for more information check: https://developer.android.com/reference/android/bluetooth/BluetoothGattCharacteristic.html#getProperties()
	* readable: If this characteristic is readable
	* writable: If this characteristic is writable
	* writable_no_response: If this characteristic is writable with no response
___

**_on_characteristic_finding**

```GDScript
GodotBluetooth344.connect("_on_characteristic_finding", self, "_on_characteristic_finding")
```

This signal is called every time we are querying for characteristics. Note that you cannot use any characteristic that has not been discovered before.

Received arguments:
* string: Represents the current status of the finding characteristics, it can have the following values:
	* processing
	* done
___

**_on_characteristic_read**

```GDScript
GodotBluetooth344.connect("_on_characteristic_read", self, "_on_characteristic_read")
```

This signal is called every time other device writes to this characteristic.

**Note:** You can use [PoolByteArray](https://docs.godotengine.org/en/stable/classes/class_poolbytearray.html) to transform the bytes to string:
```GDScript
var string = PoolByteArray(data.bytes).get_string_from_utf8()
```

Received arguments:
* dictionary: Represents the data written to the characteristic, it has the following values:
	* service_uuid: The serice UUID
	* characteristic_uuid: The characteristic UUID
	* bytes: They raw bytes of the payload
___

### Methods

**scan**

```GDScript
GodotBluetooth344.scan()
```

Starts a scan. The results will be delivered by the `_on_device_found` signal.

Arguments:

* No arguments

Returns:

* Nothing

___

**stopScan**

```GDScript
GodotBluetooth344.stopScan()
```

Stops a scan if there is one in progress.

Arguments:

* No arguments

Returns:

* Nothing
___

**hasLocationPermissions**

```GDScript
GodotBluetooth344.hasLocationPermissions()
```

Checks if we have location permissions.

Arguments:

* No arguments

Returns:

* boolean: If we have location permissions, values:
		* true: We have location permissions
		* false: We do not have location permissions

___

**locationStatus**

```GDScript
GodotBluetooth344.locationStatus()
```

Arguments:

* No arguments

Returns:

* boolean: If the location is On, values:
		* true: Location is On
		* false: Location is Off

___

**connect**

```GDScript
GodotBluetooth344.connect(address)
```

Connects to a given device. The result will be delivered by the `_on_connection_status_change` signal.

Arguments:

* address: A string representing the MAC address of the other device

Returns:

* Nothing
___

**disconnect**

```GDScript
GodotBluetooth344.disconnect()
```

Disconnects from current device. The result will be delivered by the `_on_connection_status_change` signal.

Arguments:

* No arguments

Returns:

* Nothing
___

**listServicesAndCharacteristics**

```GDScript
GodotBluetooth344.listServicesAndCharacteristics()
```

Lists all services and characteristics from the connected device. The result will be delivered by the `_on_characteristic_found` signal.

**Note:** You cna check if we are still reading characreistics using the `_on_characteristic_finding` signal.

Arguments:

* No arguments

Returns:

* Nothing
___

**subscribeToCharacteristic**

```GDScript
GodotBluetooth344.subscribeToCharacteristic(service_uuid, read_uuid)
```

Subscribes to a characteristic. From now on, any write to that characteristic will be delivered by the signal `_on_characteristic_read`.

Arguments:

* service_uuid: The serice UUID
* characteristic_uuid: The characteristic UUID

Returns:

* Nothing
___

**unsubscribeFromCharacteristic**

```GDScript
GodotBluetooth344.unsubscribeFromCharacteristic(service_uuid, read_uuid)
```

Unsubscribes from a characteristic.

Arguments:

* service_uuid: The serice UUID
* characteristic_uuid: The characteristic UUID

Returns:

* Nothing
___

**writeBytesToCharacteristic**

```GDScript
GodotBluetooth344.writeBytesToCharacteristic(bytes)
```

Writes bytes to a characteristic.

Arguments:

* bytes: Bytes to write. Use [PoolByteArray](https://docs.godotengine.org/en/stable/classes/class_poolbytearray.html)

Returns:

* Nothing
___

**writeStringToCharacteristic**

```GDScript
GodotBluetooth344.writeStringToCharacteristic(string)
```

Writes a UTF-8 string to a characteristic.


Arguments:

* string: UTF-8 string

Returns:

* Nothing
___

**readFromCharacteristic**

```GDScript
GodotBluetooth344.readFromCharacteristic(service_uuid, read_uuid)
```

Reads bytes from a characteristic. The result will be sent through the signal `_on_characteristic_read`.

Arguments:

* service_uuid: The serice UUID
* characteristic_uuid: The characteristic UUID

Returns:

* Nothing
___
 
## License
 
The MIT License (MIT)

Copyright (c) 2022 Pablo Jiménez Mateo

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
