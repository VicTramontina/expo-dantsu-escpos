export type OnLoadEventPayload = {
  url: string;
};

export type ExpoDantsuEscposModuleEvents = {
  onChange: (params: ChangeEventPayload) => void;
};

export type ChangeEventPayload = {
  value: string;
};

export type BluetoothDevice = {
  deviceName: string;
  address: string;
};

export type UsbDevice = {
  deviceName: string;
  vendorId: number;
  productId: number;
};

export interface TcpDevice {
  address: string;
  port: number;
  status: string;
  deviceName: string;
}

export type PrinterInfo = {
  dpi: number;
  widthMM: number;
  widthPx: number;
  charsPerLine: number;
};

export interface ExpoDantsuEscposModule {
  getBluetoothDevices(): Promise<BluetoothDevice[]>;

  getUsbDevices(): Promise<UsbDevice[]>;

  getTcpDevices(): Promise<TcpDevice[]>;

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
