# expo-dantsu-escpos

A React Native module that bridges [DantSu's](https://github.com/DantSu) [ESCPOS-ThermalPrinter-Android](https://github.com/DantSu/ESCPOS-ThermalPrinter-Android) library so you can send ESC/POS commands to thermal printers from an Expo application. The current implementation targets **Android** only and wraps DantSu's native API with asynchronous functions.

## Expo Dantsu ESCPOS Module

This module bridges the [ESCPOS-ThermalPrinter-Android](https://github.com/DantSu/ESCPOS-ThermalPrinter-Android) library to React Native using Expo. It provides methods to interact with Bluetooth, USB, and TCP printers.

### Installation

```bash
npm install expo-dantsu-escpos
expo prebuild
```

### Methods

#### `getBluetoothDevices()`
Lists paired Bluetooth printers.
- **Returns**: `Promise<BluetoothDevice[]>`

#### `getUsbDevices()`
Lists connected USB printers.
- **Returns**: `Promise<UsbDevice[]>`

#### `getTcpDevices()`
Lists connected TCP printers.
- **Returns**: `Promise<TcpDevice[]>`

#### `connectBluetooth(address?: string)`
Connects to a Bluetooth printer by address or the first paired printer.
- **Parameters**:
  - `address` (optional): The address of the Bluetooth printer.
- **Returns**: `Promise<void>`

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

#### `BluetoothDevice`
- `deviceName`: Name of the Bluetooth device.
- `address`: Address of the Bluetooth device.

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

#### Bluetooth
Add the following permissions to your `app.json`:
```json
{
  "expo": {
    "android": {
      "permissions": [
        "ACCESS_FINE_LOCATION",
        "BLUETOOTH"
      ]
    }
  }
}
```

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

Refer to the `example` directory for a working example. Below is a snippet:

```tsx
import ExpoEscposDantsuModule from 'expo-dantsu-escpos';

async function printExample() {
  await ExpoEscposDantsuModule.connectBluetooth();
  await ExpoEscposDantsuModule.printText('<C>Hello World</C>\n<BR>');
  await ExpoEscposDantsuModule.disconnect();
}
```

### Acknowledgements

This package builds upon the great work by **DantSu** and his [ESCPOS-ThermalPrinter-Android](https://github.com/DantSu/ESCPOS-ThermalPrinter-Android) project.

### License

MIT
