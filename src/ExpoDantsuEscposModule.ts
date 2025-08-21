import { NativeModule, requireNativeModule } from "expo";

import type {
  ExpoDantsuEscposModuleEvents,
  BluetoothDevice,
  UsbDevice,
  PrinterInfo,
  TcpDevice,
  BluetoothScanOptions,
  BluetoothConnectionOptions,
  BluetoothConnectionResult,
} from "./ExpoDantsuEscpos.types";

declare class ExpoDantsuEscposModule extends NativeModule<ExpoDantsuEscposModuleEvents> {
  /** List Bluetooth devices (bonded + discovered) with enhanced scanning */
  getBluetoothDevices(options?: BluetoothScanOptions): Promise<BluetoothDevice[]>;

  /** List connected USB printers */
  getUsbDevices(): Promise<UsbDevice[]>;

  /** List connected TCP printers */
  getTcpDevices(): Promise<TcpDevice[]>;

  /** Enhanced Bluetooth connection with insecure SPP support */
  connectBluetooth(options: BluetoothConnectionOptions): Promise<BluetoothConnectionResult>;

  /** Connect to the first connected USB printer or by vendor/product id */
  connectUsb(vendorId?: number, productId?: number): Promise<void>;

  /** Connect to a TCP printer */
  connectTcp(address: string, port: number, timeout?: number): Promise<void>;

  /** Disconnect from the printer */
  disconnect(): Promise<void>;

  /** Print ESC/POS formatted text with optional paper feed, cut, and cash drawer actions */
  printFormattedText(
    content: string,
    feedPaperMM?: number,
    cutPaper?: boolean,
    openCashDrawer?: boolean
  ): Promise<void>;

  /** Convert base64 image to ESC/POS format string */
  convertImageToEscPos(base64: string, align?: string, gradient?: boolean): Promise<string>;

  /** Use ESC asterisk command */
  useEscAsteriskCommand(enable: boolean): Promise<void>;

  /** Get information about the connected printer */
  getPrinterInfo(): Promise<PrinterInfo>;

  /** Convert millimeters to pixels */
  mmToPx(mm: number): Promise<number>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoDantsuEscposModule>(
  "ExpoDantsuEscposModule",
);
