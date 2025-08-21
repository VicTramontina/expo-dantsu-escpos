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
  deviceName: string | null;
  address: string;
  bonded: boolean;
  rssi: number | null;
  source: 'bonded' | 'scan' | 'both';
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

export type BluetoothScanOptions = {
  scanMillis?: number;
  nameRegex?: string;
  includeRssi?: boolean;
  includeBondedOnly?: boolean;
};

export type BluetoothConnectionOptions = {
  address: string;
  preferInsecureIfUnbonded?: boolean;
  timeoutMs?: number;
  allowSecureFallback?: boolean;
  nameHint?: string;
  printerDpi?: number;
  printerWidthMM?: number;
  printerNbrCharactersPerLine?: number;
};

export type BluetoothConnectionResult = {
  connectionMode: 'secure' | 'insecure' | 'existing';
  dpi: number;
  widthMM: number;
  charsPerLine: number;
};

export interface ExpoDantsuEscposModule {
  getBluetoothDevices(options?: BluetoothScanOptions): Promise<BluetoothDevice[]>;

  getUsbDevices(): Promise<UsbDevice[]>;

  getTcpDevices(): Promise<TcpDevice[]>;

  connectBluetooth(options: BluetoothConnectionOptions): Promise<BluetoothConnectionResult>;

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
