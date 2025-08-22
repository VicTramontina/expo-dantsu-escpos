export interface ExpoDantsuEscposModule {
  getBluetoothDevices(): Promise<{ name: string; address: string }[]>;
  connectBluetooth(
    address: string,
    dpi: number,
    widthMM: number,
    nbrCharactersPerLine: number,
  ): Promise<void>;
  disconnectPrinter(): Promise<void>;
  useEscAsteriskCommand(enable: boolean): Promise<void>;
  printFormattedText(text: string, mmFeedPaper?: number): Promise<void>;
  printFormattedTextAndCut(text: string, mmFeedPaper?: number): Promise<void>;
  printFormattedTextAndOpenCashBox(text: string, mmFeedPaper?: number): Promise<void>;
  getUSBDevices(): Promise<{ name: string; vendorId: number; productId: number }[]>;
  connectUSB(
    vendorId: number,
    productId: number,
    dpi: number,
    widthMM: number,
    nbrCharactersPerLine: number,
  ): Promise<void>;
  connectTCP(
    address: string,
    port: number,
    dpi: number,
    widthMM: number,
    nbrCharactersPerLine: number,
  ): Promise<void>;
}
