# expo-dantsu-escpos

A React Native module that bridges [DantSu's](https://github.com/DantSu) [ESCPOS-ThermalPrinter-Android](https://github.com/DantSu/ESCPOS-ThermalPrinter-Android) library so you can send ESC/POS commands to thermal printers from an Expo application. The current implementation targets **Android** only and wraps DantSu's native API with asynchronous functions.

## Installation

```sh
npx expo install expo-dantsu-escpos
```

## Usage

```ts
import ExpoDantsuEscpos from 'expo-dantsu-escpos';

async function printExample() {
  const devices = await ExpoDantsuEscpos.getBluetoothDevices();
  if (devices.length > 0) {
    await ExpoDantsuEscpos.connectBluetooth(devices[0].address);
    await ExpoDantsuEscpos.printText('<C>Hello World</C>\n');
  }
}
```

## Types

```ts
export type BluetoothDevice = {
  deviceName: string;
  address: string;
};

export type UsbDevice = {
  deviceName: string;
  vendorId: number;
  productId: number;
};

export type PrinterInfo = {
  dpi: number;
  widthMM: number;
  widthPx: number;
  charsPerLine: number;
};

export type ChangeEventPayload = {
  value: string;
};

export type ExpoDantsuEscposModuleEvents = {
  onChange: (params: ChangeEventPayload) => void;
};
```

## API

```ts
interface ExpoDantsuEscposModule {
  getBluetoothDevices(): Promise<BluetoothDevice[]>;
  getUsbDevices(): Promise<UsbDevice[]>;
  connectBluetooth(address?: string): Promise<void>;
  connectUsb(vendorId?: number, productId?: number): Promise<void>;
  connectTcp(address: string, port: number, timeout?: number): Promise<void>;
  disconnect(): Promise<void>;
  printText(text: string): Promise<void>;
  printImage(base64: string, gradient?: boolean): Promise<void>;
  printBarcode(
    data: string,
    type?: string,
    width?: number,
    height?: number,
    textPosition?: string,
    align?: string,
  ): Promise<void>;
  printQRCode(data: string, size?: number, align?: string): Promise<void>;
  feedPaper(mm: number): Promise<void>;
  cutPaper(): Promise<void>;
  openCashDrawer(): Promise<void>;
  useEscAsteriskCommand(enable: boolean): Promise<void>;
  getPrinterInfo(): Promise<PrinterInfo>;
  mmToPx(mm: number): Promise<number>;
}
```

All methods are asynchronous and return promises. For a working example check the contents of the `example` directory in this repository.

## Acknowledgements

This package builds upon the great work by **DantSu** and his [ESCPOS-ThermalPrinter-Android](https://github.com/DantSu/ESCPOS-ThermalPrinter-Android) project.

## License

MIT
