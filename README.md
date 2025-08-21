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

#### `printFormattedText(content: string, feedPaperMM?: number, cutPaper?: boolean, openCashDrawer?: boolean)`
**New Unified Function**: Prints ESC/POS formatted text with optional actions in a single call, following the DantSu library pattern.

- **Parameters**:
  - `content`: ESC/POS formatted text with embedded tags (images, barcodes, QR codes, formatting)
  - `feedPaperMM` (optional): Feed paper in millimeters after printing
  - `cutPaper` (optional): Cut paper after printing
  - `openCashDrawer` (optional): Open cash drawer after printing
- **Returns**: `Promise<void>`

**ESC/POS Formatting Syntax**:
- **Alignment**: `[L]` (left), `[C]` (center), `[R]` (right)
- **Text Formatting**: `<b>bold</b>`, `<u>underline</u>`, `<font size='big'>large</font>`
- **Images**: `<img>hexdata</img>` (use `convertImageToEscPos()` helper)
- **Barcodes**: `<barcode type='EAN13' width='2' height='50'>123456789012</barcode>`
- **QR Codes**: `<qrcode size='25'>https://example.com</qrcode>`
- **Line Breaks**: `\n` or `<BR>`

#### `convertImageToEscPos(base64: string, align?: string, gradient?: boolean)`
Converts a base64 image to ESC/POS format string for embedding in `printFormattedText()`.
- **Parameters**:
  - `base64`: Base64 string of the image
  - `align` (optional): Alignment ('L', 'C', 'R')
  - `gradient` (optional): Whether to apply gradient
- **Returns**: `Promise<string>` - ESC/POS formatted image string

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

#### `EscPosTextBuilder`
Fluent interface for building ESC/POS formatted text:
- `text(content: string)`: Add plain text
- `center(content: string)`: Add centered text
- `left(content: string)`: Add left-aligned text  
- `right(content: string)`: Add right-aligned text
- `bold(content: string)`: Add bold text
- `underline(content: string)`: Add underlined text
- `fontBig(content: string)`: Add large font text
- `fontTall(content: string)`: Add tall font text
- `image(base64: string, align?, gradient?)`: Add image
- `barcode(data: string, options?)`: Add barcode
- `qrcode(data: string, options?)`: Add QR code
- `newLine()`: Add line break
- `build()`: Build final ESC/POS string

#### `PrintOptions`
- `feedPaperMM?`: Feed paper in millimeters after printing
- `cutPaper?`: Cut paper after printing  
- `openCashDrawer?`: Open cash drawer after printing

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

Refer to the `example` directory for a comprehensive working example. Below are snippets demonstrating the new unified printing approach:

#### New Unified Printing Approach
```tsx
import ExpoEscposDantsuModule, { createEscPosBuilder, EscPosUtils } from 'expo-dantsu-escpos';

async function enhancedPrintingWorkflow() {
  try {
    // 1. Connect to printer (same as before)
    const devices = await ExpoEscposDantsuModule.getBluetoothDevices({
      scanMillis: 6000,
      includeRssi: true,
      nameRegex: 'printer'
    });
    
    const targetDevice = devices[0];
    await ExpoEscposDantsuModule.connectBluetooth({
      address: targetDevice.address,
      preferInsecureIfUnbonded: true,
      allowSecureFallback: true
    });
    
    // 2. Build content using the fluent builder (recommended)
    const receipt = createEscPosBuilder()
      .center('MY STORE')
      .newLine()
      .center('123 Main St, City')
      .newLine()
      .center('========================')
      .newLine()
      .left('Coffee').right('$4.50')
      .newLine()
      .left('Sandwich').right('$8.95')
      .newLine()
      .center('------------------------')
      .newLine()
      .bold('Total: $13.45')
      .newLine()
      .center('Thank you!')
      .newLine()
      .qrcode('https://mystore.com/receipt/123', { size: 25, align: 'C' })
      .newLine()
      .barcode('1234567890123', { 
        type: 'EAN13', 
        height: 50, 
        align: 'C' 
      })
      .build();
    
    // 3. Print everything in one call with actions
    await ExpoEscposDantsuModule.printFormattedText(
      receipt,
      5,     // Feed 5mm
      true,  // Cut paper
      true   // Open cash drawer
    );
    
    // 4. Disconnect
    await ExpoEscposDantsuModule.disconnect();
    
  } catch (error) {
    console.error('Printing failed:', error);
  }
}
```

#### Direct ESC/POS Syntax (Alternative)
```tsx
async function directEscPosPrint() {
  // You can also write ESC/POS directly
  const content = `
[C]<b><font size='big'>RECEIPT</font></b>
[C]========================
[L]Item 1[R]$10.00
[L]Item 2[R]$15.50
[C]------------------------
[C]<b>Total: $25.50</b>
[C]<qrcode size='20'>https://expo.dev</qrcode>
[C]<barcode type='EAN13'>123456789012</barcode>
  `;
  
  await ExpoEscposDantsuModule.printFormattedText(content, 5, true);
}
```

#### Adding Images
```tsx
async function printWithImage() {
  // Convert image first
  const imageEscPos = await ExpoEscposDantsuModule.convertImageToEscPos(
    base64ImageData, 
    'C', 
    false
  );
  
  // Build content with image
  const content = createEscPosBuilder()
    .center('STORE LOGO')
    .newLine()
    .text(imageEscPos)  // Insert converted image
    .newLine()
    .center('Welcome!')
    .build();
  
  await ExpoEscposDantsuModule.printFormattedText(content);
}
```

#### Utility Functions
```tsx
import { EscPosUtils } from 'expo-dantsu-escpos';

// Quick receipt building
const receipt = 
  EscPosUtils.receiptHeader('My Store', '123 Main St') +
  EscPosUtils.lineItem('Coffee', '$4.50', 2) +
  EscPosUtils.lineItem('Sandwich', '$8.95') +
  EscPosUtils.separator() +
  EscPosUtils.lineItem('Total', '$17.95') +
  EscPosUtils.receiptFooter('Thank you for your business!');

await ExpoEscposDantsuModule.printFormattedText(receipt, 5, true);
```

### Builder Pattern API

The new `createEscPosBuilder()` provides a fluent interface for building receipts:

```tsx
import { createEscPosBuilder } from 'expo-dantsu-escpos';

const content = createEscPosBuilder()
  .center('CENTERED TEXT')        // [C]CENTERED TEXT
  .left('LEFT TEXT')              // [L]LEFT TEXT  
  .right('RIGHT TEXT')            // [R]RIGHT TEXT
  .bold('BOLD TEXT')              // <b>BOLD TEXT</b>
  .underline('UNDERLINED')        // <u>UNDERLINED</u>
  .fontBig('BIG FONT')           // <font size='big'>BIG FONT</font>
  .fontTall('TALL FONT')         // <font size='tall'>TALL FONT</font>
  .qrcode('QR Data', {           // <qrcode size='25'>QR Data</qrcode>
    size: 25,
    align: 'C'
  })
  .barcode('123456789012', {     // [C]<barcode type='EAN13'>123456789012</barcode>
    type: 'EAN13',
    align: 'C'
  })
  .newLine()                     // \n
  .build();                      // Returns final string
```

### Key Improvements

✅ **Single Function Call**: No more multiple requests to the printer  
✅ **Atomic Operations**: Print, feed, cut, and open drawer in one transaction  
✅ **Builder Pattern**: Easy-to-use fluent interface for complex receipts  
✅ **Direct ESC/POS**: Full control with direct syntax  
✅ **Utility Functions**: Pre-built patterns for common receipt elements  
✅ **Backward Compatible**: All connection methods remain the same  

### Migration from Multiple Functions

**Before (Multiple Calls)**:
```tsx
// OLD - Multiple separate calls
await ExpoEscposDantsuModule.printText('[C]My Store\n');
await ExpoEscposDantsuModule.printQRCode('https://expo.dev', 20, 'C');
await ExpoEscposDantsuModule.printBarcode('123456789012');
await ExpoEscposDantsuModule.feedPaper(5);
await ExpoEscposDantsuModule.cutPaper();
```

**After (Single Call)**:
```tsx
// NEW - Single call with everything
const content = createEscPosBuilder()
  .center('My Store')
  .newLine()
  .qrcode('https://expo.dev', { size: 20, align: 'C' })
  .newLine()
  .barcode('123456789012', { align: 'C' })
  .build();

await ExpoEscposDantsuModule.printFormattedText(content, 5, true);
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
