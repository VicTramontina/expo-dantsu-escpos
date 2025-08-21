# expo-dantsu-escpos

A React Native module that bridges [DantSu's](https://github.com/DantSu) [ESCPOS-ThermalPrinter-Android](https://github.com/DantSu/ESCPOS-ThermalPrinter-Android) library so you can send ESC/POS commands to thermal printers from an Expo application. The current implementation targets **Android** only and wraps DantSu's native API with asynchronous functions.

**✨ Enhanced Bluetooth Support**: This version includes advanced Bluetooth connectivity features including insecure SPP connections for corporate ROMs, device discovery scanning, and comprehensive device listing with bond state and signal strength information.

## Expo Dantsu ESCPOS Module

This module bridges the [ESCPOS-ThermalPrinter-Android](https://github.com/DantSu/ESCPOS-ThermalPrinter-Android) library to React Native using Expo. It provides methods to interact with Bluetooth, USB, and TCP printers.

### Installation

```bash
npm install expo-dantsu-escpos
expo prebuild
```

### Methods

#### `getBluetoothDevices(options?: BluetoothScanOptions)`
**Enhanced**: Lists both bonded (paired) and discovered Bluetooth devices with comprehensive scanning capabilities.

- **Parameters**:
  - `options` (optional): Scan configuration object
    - `scanMillis` (number, default: 5000): Duration of device discovery in milliseconds
    - `nameRegex` (string): Filter devices by name using regex pattern
    - `includeRssi` (boolean, default: true): Include signal strength (RSSI) information
    - `includeBondedOnly` (boolean, default: false): If true, only return bonded devices (skip discovery)
- **Returns**: `Promise<BluetoothDevice[]>` - Array of devices with enhanced metadata

**Example**:
```typescript
// Get all devices with 6-second discovery
const devices = await ExpoEscposDantsuModule.getBluetoothDevices({
  scanMillis: 6000,
  includeRssi: true,
  nameRegex: 'printer.*' // Only devices with names containing "printer"
});

// Get only bonded devices (fast)
const bondedDevices = await ExpoEscposDantsuModule.getBluetoothDevices({
  includeBondedOnly: true
});
```

#### `getUsbDevices()`
Lists connected USB printers.
- **Returns**: `Promise<UsbDevice[]>`

#### `getTcpDevices()`
Lists connected TCP printers.
- **Returns**: `Promise<TcpDevice[]>`

#### `connectBluetooth(options: BluetoothConnectionOptions)`
**Enhanced**: Connects to a Bluetooth printer with advanced connection strategies including insecure SPP support for corporate ROMs.

- **Parameters**:
  - `options`: Connection configuration object
    - `address` (string, **required**): MAC address of the target Bluetooth device
    - `preferInsecureIfUnbonded` (boolean, default: true): Use insecure RFCOMM for unbonded devices
    - `allowSecureFallback` (boolean, default: true): Allow fallback to secure connection if insecure fails
    - `timeoutMs` (number, default: 15000): Connection timeout in milliseconds
    - `nameHint` (string): Device name for logging purposes
    - `printerDpi` (number, default: 203): Printer DPI setting
    - `printerWidthMM` (number, default: 48): Printer width in millimeters
    - `printerNbrCharactersPerLine` (number, default: 32): Characters per line
- **Returns**: `Promise<BluetoothConnectionResult>` - Connection result with mode and printer info

**Example**:
```typescript
// Connect to a specific device with insecure SPP (ideal for corporate ROMs)
const result = await ExpoEscposDantsuModule.connectBluetooth({
  address: "00:11:22:33:44:55",
  preferInsecureIfUnbonded: true,
  allowSecureFallback: true,
  timeoutMs: 15000,
  nameHint: "My Thermal Printer"
});

console.log(`Connected using ${result.connectionMode} mode`);
// result.connectionMode can be: "secure", "insecure", or "existing"
```

#### `connectUsb(vendorId?: number, productId?: number)`
Connects to a USB printer by vendor/product ID or the first connected printer.
- **Parameters**:
  - `vendorId` (optional): Vendor ID of the USB printer.
  - `productId` (optional): Product ID of the USB printer.
- **Returns**: `Promise<void>`

#### `connectTcp(address: string, port: number, timeout?: number)`
Connects to a TCP printer.
- **Parameters**:
  - `address`: IP address of the printer.
  - `port`: Port number.
  - `timeout` (optional): Connection timeout in milliseconds.
- **Returns**: `Promise<void>`

#### `disconnect()`
Disconnects from the printer.
- **Returns**: `Promise<void>`

#### `printText(text: string)`
Prints ESC/POS formatted text.
- **Parameters**:
  - `text`: ESC/POS formatted text (e.g., `<C>Hello</C>`).
- **Returns**: `Promise<void>`

#### `printImage(base64: string, gradient?: boolean)`
Prints a base64-encoded image.
- **Parameters**:
  - `base64`: Base64 string of the image.
  - `gradient` (optional): Whether to apply a gradient.
- **Returns**: `Promise<void>`

#### `printBarcode(data: string, type?: string, width?: number, height?: number, textPosition?: string, align?: string)`
Prints a barcode.
- **Parameters**:
  - `data`: Barcode data.
  - `type` (optional): Barcode type.
  - `width` (optional): Width of the barcode.
  - `height` (optional): Height of the barcode.
  - `textPosition` (optional): Position of the text.
  - `align` (optional): Alignment.
- **Returns**: `Promise<void>`

#### `printQRCode(data: string, size?: number, align?: string)`
Prints a QR code.
- **Parameters**:
  - `data`: QR code data.
  - `size` (optional): Size of the QR code.
  - `align` (optional): Alignment.
- **Returns**: `Promise<void>`

### Type Definitions

#### `BluetoothDevice` (Enhanced)
- `deviceName`: Name of the Bluetooth device (can be null for unnamed devices).
- `address`: MAC address of the Bluetooth device.
- `bonded`: Whether the device is bonded (paired) with this Android device.
- `rssi`: Signal strength in dBm (null if not available during scan).
- `source`: How the device was discovered - `"bonded"`, `"scan"`, or `"both"`.

#### `BluetoothScanOptions`
- `scanMillis?`: Duration of device discovery in milliseconds (default: 5000).
- `nameRegex?`: Filter devices by name using regex pattern.
- `includeRssi?`: Include signal strength information (default: true).
- `includeBondedOnly?`: If true, only return bonded devices (default: false).

#### `BluetoothConnectionOptions`
- `address`: MAC address of the target Bluetooth device (**required**).
- `preferInsecureIfUnbonded?`: Use insecure RFCOMM for unbonded devices (default: true).
- `allowSecureFallback?`: Allow fallback to secure connection (default: true).
- `timeoutMs?`: Connection timeout in milliseconds (default: 15000).
- `nameHint?`: Device name for logging purposes.
- `printerDpi?`: Printer DPI setting (default: 203).
- `printerWidthMM?`: Printer width in millimeters (default: 48).
- `printerNbrCharactersPerLine?`: Characters per line (default: 32).

#### `BluetoothConnectionResult`
- `connectionMode`: The connection mode used - `"secure"`, `"insecure"`, or `"existing"`.
- `dpi`: Printer DPI setting.
- `widthMM`: Printer width in millimeters.
- `charsPerLine`: Characters per line.

#### `UsbDevice`
- `deviceName`: Name of the USB device.
- `vendorId`: Vendor ID.
- `productId`: Product ID.

#### `TcpDevice`
- `address`: IP address.
- `port`: Port number.
- `status`: Connection status.
- `deviceName`: Name of the TCP device.

#### `PrinterInfo`
- `dpi`: Printer DPI.
- `widthMM`: Width in millimeters.
- `widthPx`: Width in pixels.
- `charsPerLine`: Characters per line.

### Permissions

#### Bluetooth (Enhanced)
The enhanced Bluetooth functionality requires different permissions based on Android version:

**For Android 12+ (API 31+):**
```json
{
  "expo": {
    "android": {
      "permissions": [
        "BLUETOOTH_SCAN",
        "BLUETOOTH_CONNECT"
      ]
    }
  }
}
```

**For Android < 12:**
```json
{
  "expo": {
    "android": {
      "permissions": [
        "ACCESS_FINE_LOCATION",
        "BLUETOOTH",
        "BLUETOOTH_ADMIN"
      ]
    }
  }
}
```

**Note**: Location permission (`ACCESS_FINE_LOCATION`) is required on older Android versions for Bluetooth device discovery.

#### USB
Add the following permissions to your `app.json`:
```json
{
  "expo": {
    "android": {
      "permissions": [
        "USB_HOST"
      ]
    }
  }
}
```

#### TCP
Ensure network access is enabled in your app.

### Example Usage

Refer to the `example` directory for a comprehensive working example. Below are snippets demonstrating the enhanced Bluetooth functionality:

#### Enhanced Bluetooth Workflow
```tsx
import ExpoEscposDantsuModule from 'expo-dantsu-escpos';

async function enhancedBluetoothPrint() {
  try {
    // 1. Discover all available devices
    const devices = await ExpoEscposDantsuModule.getBluetoothDevices({
      scanMillis: 6000,
      includeRssi: true,
      nameRegex: 'printer' // Optional: filter for printers
    });
    
    console.log(`Found ${devices.length} devices`);
    devices.forEach(device => {
      console.log(`${device.deviceName} (${device.address}) - ${device.bonded ? 'Bonded' : 'Discovered'} - ${device.rssi}dBm`);
    });
    
    // 2. Select and connect to a device
    const targetDevice = devices[0]; // Select the first device
    const connectionResult = await ExpoEscposDantsuModule.connectBluetooth({
      address: targetDevice.address,
      nameHint: targetDevice.deviceName,
      preferInsecureIfUnbonded: true, // Key for corporate ROMs
      allowSecureFallback: true,
      timeoutMs: 15000
    });
    
    console.log(`Connected using ${connectionResult.connectionMode} mode`);
    
    // 3. Print content
    await ExpoEscposDantsuModule.printText('<C>Enhanced Bluetooth Print</C>\n<BR>');
    await ExpoEscposDantsuModule.printQRCode('https://example.com', 20, 'C');
    
    // 4. Disconnect
    await ExpoEscposDantsuModule.disconnect();
    
  } catch (error) {
    console.error('Printing failed:', error);
  }
}
```

#### Legacy Bluetooth (Backward Compatible)
```tsx
import ExpoEscposDantsuModule from 'expo-dantsu-escpos';

async function legacyBluetoothPrint() {
  // Note: Legacy method still works but requires bonded devices
  const devices = await ExpoEscposDantsuModule.getBluetoothDevices({ includeBondedOnly: true });
  
  if (devices.length > 0) {
    await ExpoEscposDantsuModule.connectBluetooth({ address: devices[0].address });
    await ExpoEscposDantsuModule.printText('<C>Hello World</C>\n<BR>');
    await ExpoEscposDantsuModule.disconnect();
  }
}
```

### Debugging and Logging

The enhanced Bluetooth functionality includes comprehensive logging to help debug connection issues, especially useful for corporate ROMs:

#### Android Logcat Logs

Use `adb logcat` or Android Studio's Logcat to monitor detailed connection logs:

```bash
# Filter for module-specific logs
adb logcat | grep "ExpoDantsuEscposModule"

# Common log patterns:
# BT/SCAN: Device discovery progress
# BT/CONNECT: Connection attempts and results  
# BT/DISCONNECT: Disconnection status
```

**Key Log Messages:**
- `Strategy: insecure-first` or `secure-first` - Shows connection approach
- `Insecure connection successful` - Insecure SPP worked
- `Secure connection successful` - Standard Bluetooth connection worked
- `E_BT_SCAN_WARN: Discovery failed` - Discovery issues (permissions/hardware)

#### Common Issues & Solutions

**Corporate ROM Connection Issues:**
- Set `preferInsecureIfUnbonded: true` (default)
- Ensure device is not bonded if using insecure connection
- Check that Bluetooth permissions are properly granted

**Discovery Not Finding Devices:**
- Verify location permissions on Android < 12
- Ensure Bluetooth is enabled and discoverable on target device
- Try increasing `scanMillis` parameter

**Connection Timeouts:**
- Increase `timeoutMs` parameter
- Ensure target device is within range
- Check for interference from other Bluetooth devices

### Corporate ROM Compatibility

This module specifically addresses connection issues common in corporate Android ROMs:

- **Insecure SPP Support**: Bypasses pairing requirements often restricted in corporate environments
- **Dual Strategy**: Falls back to secure connections when appropriate
- **Enhanced Discovery**: Finds devices without requiring prior pairing
- **Comprehensive Logging**: Detailed connection diagnostics for troubleshooting

### Acknowledgements

This package builds upon the great work by **DantSu** and his [ESCPOS-ThermalPrinter-Android](https://github.com/DantSu/ESCPOS-ThermalPrinter-Android) project.

### License

MIT
