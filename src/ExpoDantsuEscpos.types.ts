export type OnLoadEventPayload = {
  url: string;
};

export type ExpoDantsuEscposModuleEvents = {
  onChange: (params: ChangeEventPayload) => void;
};

export type ChangeEventPayload = {
  value: string;
};

export type EscPosTextBuilder = {
  text(content: string): EscPosTextBuilder;
  center(content: string): EscPosTextBuilder;
  left(content: string): EscPosTextBuilder;
  right(content: string): EscPosTextBuilder;
  bold(content: string): EscPosTextBuilder;
  underline(content: string): EscPosTextBuilder;
  fontBig(content: string): EscPosTextBuilder;
  fontTall(content: string): EscPosTextBuilder;
  image(base64: string, align?: 'L' | 'C' | 'R', gradient?: boolean): EscPosTextBuilder;
  barcode(data: string, options?: {
    type?: string;
    width?: number;
    height?: number;
    textPosition?: string;
    align?: 'L' | 'C' | 'R';
  }): EscPosTextBuilder;
  qrcode(data: string, options?: {
    size?: number;
    align?: 'L' | 'C' | 'R';
  }): EscPosTextBuilder;
  newLine(): EscPosTextBuilder;
  build(): string;
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

export type PrintOptions = {
  feedPaperMM?: number;
  cutPaper?: boolean;
  openCashDrawer?: boolean;
};

export type BluetoothConnectionResult = {
  connectionMode: 'secure' | 'insecure' | 'existing';
  dpi: number;
  widthMM: number;
  charsPerLine: number;
};

export type PrintFormattedTextOptions = {
  feedPaperMM?: number;
  cutPaper?: boolean;
  openCashDrawer?: boolean;
};

export interface ExpoDantsuEscposModule {
  getBluetoothDevices(options?: BluetoothScanOptions): Promise<BluetoothDevice[]>;

  getUsbDevices(): Promise<UsbDevice[]>;

  getTcpDevices(): Promise<TcpDevice[]>;

  connectBluetooth(options: BluetoothConnectionOptions): Promise<BluetoothConnectionResult>;

  connectUsb(vendorId?: number, productId?: number): Promise<void>;

  connectTcp(address: string, port: number, timeout?: number): Promise<void>;

  disconnect(): Promise<void>;

  printFormattedText(
    content: string,
    feedPaperMM?: number,
    cutPaper?: boolean,
    openCashDrawer?: boolean
  ): Promise<void>;

  convertImageToEscPos(base64: string, align?: string, gradient?: boolean): Promise<string>;

  useEscAsteriskCommand(enable: boolean): Promise<void>;

  getPrinterInfo(): Promise<PrinterInfo>;

  mmToPx(mm: number): Promise<number>;
}
