import { NativeModule, requireNativeModule } from "expo";

import type {
  ExpoDantsuEscposModuleEvents,
  BluetoothDevice,
  UsbDevice,
  PrinterInfo,
} from "./ExpoDantsuEscpos.types";

declare class ExpoDantsuEscposModule extends NativeModule<ExpoDantsuEscposModuleEvents> {
  /** List paired Bluetooth printers */
  getBluetoothDevices(): Promise<BluetoothDevice[]>;

  /** List connected USB printers */
  getUsbDevices(): Promise<UsbDevice[]>;

  /** Connect to the first paired Bluetooth printer or by address */
  connectBluetooth(address?: string): Promise<void>;

  /** Connect to the first connected USB printer or by vendor/product id */
  connectUsb(vendorId?: number, productId?: number): Promise<void>;

  /** Connect to a TCP printer */
  connectTcp(address: string, port: number, timeout?: number): Promise<void>;

  /** Disconnect from the printer */
  disconnect(): Promise<void>;

  /** Print ESC/POS formatted text */
  printText(text: string): Promise<void>;

  /** Print a base64 encoded image */
  printImage(base64: string, gradient?: boolean): Promise<void>;

  /** Print a barcode */
  printBarcode(
    data: string,
    type?: string,
    width?: number,
    height?: number,
    textPosition?: string,
    align?: string,
  ): Promise<void>;

  /** Print a QR code */
  printQRCode(data: string, size?: number, align?: string): Promise<void>;

  /** Feed paper in millimeters */
  feedPaper(mm: number): Promise<void>;

  /** Cut the paper */
  cutPaper(): Promise<void>;

  /** Open the cash drawer */
  openCashDrawer(): Promise<void>;

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
