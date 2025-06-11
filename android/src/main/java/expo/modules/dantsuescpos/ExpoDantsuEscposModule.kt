package expo.modules.dantsuescpos

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.BitmapFactory
import android.util.Base64
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.exception.CodedException
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.tcp.TcpConnection
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections
import com.dantsu.escposprinter.connection.usb.UsbConnection
import com.dantsu.escposprinter.textparser.PrinterTextParserImg

class ExpoDantsuEscposModule : Module() {
    private var printer: EscPosPrinter? = null

    @SuppressLint("MissingPermission")
    override fun definition() = ModuleDefinition {
        Name("ExpoDantsuEscposModule")

        // List paired Bluetooth printers
        AsyncFunction("getBluetoothDevices") {
            val connections = BluetoothPrintersConnections()
            val list = connections.getList()
            list?.map {
                mapOf(
                    "name" to it.device.name,
                    "address" to it.device.address
                )
            } ?: emptyList<Map<String, String>>()
        }

        // List connected USB printers
        AsyncFunction("getUsbDevices") {
            val activity: Activity = appContext.activityProvider?.currentActivity
                ?: throw CodedException("E_NO_ACTIVITY", RuntimeException("Activity unavailable"))
            val connections = UsbPrintersConnections(activity)
            val list = connections.getList()
            list?.map { usb ->
                mapOf(
                    "vendorId" to usb.device.vendorId,
                    "productId" to usb.device.productId,
                    "deviceName" to usb.device.deviceName
                )
            } ?: emptyList<Map<String, Any>>()
        }

        // Connect via Bluetooth (address optional)
        Function("connectBluetooth") { address: String? ->
            try {
                val connDevice: BluetoothConnection = if (address.isNullOrEmpty()) {
                    BluetoothPrintersConnections.selectFirstPaired()
                        ?: throw CodedException("E_NO_BT_PRINTER", RuntimeException("No paired Bluetooth printer"))
                } else {
                    val connections = BluetoothPrintersConnections()
                    connections.getList()
                        ?.firstOrNull { it.device.address == address }
                        ?: throw CodedException("E_NO_BT_PRINTER", RuntimeException("Printer '$address' not found"))
                }
                printer = EscPosPrinter(connDevice, 203, 48f, 32)
            } catch (e: Exception) {
                throw CodedException("E_BT_CONNECT", e)
            }
        }

        // Connect via USB
        Function("connectUsb") { vendorId: Int?, productId: Int? ->
            try {
                val activity: Activity = appContext.activityProvider?.currentActivity
                    ?: throw CodedException("E_NO_ACTIVITY", RuntimeException("Activity unavailable"))
                val connDevice: UsbConnection = if (vendorId != null && productId != null) {
                    val connections = UsbPrintersConnections(activity)
                    connections.getList()
                        ?.firstOrNull { it.device.vendorId == vendorId && it.device.productId == productId }
                        ?: throw CodedException(
                            "E_NO_USB_PRINTER",
                            RuntimeException("Printer $vendorId:$productId not found")
                        )
                } else {
                    UsbPrintersConnections.selectFirstConnected(activity)
                        ?: throw CodedException("E_NO_USB_PRINTER", RuntimeException("No USB printer connected"))
                }
                printer = EscPosPrinter(connDevice, 203, 48f, 32)
            } catch (e: Exception) {
                throw CodedException("E_USB_CONNECT", e)
            }
        }

        // Connect via TCP
        Function("connectTcp") { address: String, port: Int, timeout: Int? ->
            try {
                val conn = timeout?.let { TcpConnection(address, port, it) } ?: TcpConnection(address, port)
                printer = EscPosPrinter(conn, 203, 48f, 32)
            } catch (e: Exception) {
                throw CodedException("E_TCP_CONNECT", e)
            }
        }

        // Tear down
        Function("disconnect") {
            printer?.disconnectPrinter()
            printer = null
        }

        // Print formatted ESC/POS text
        Function("printText") { text: String ->
            printer?.printFormattedText(text)
                ?: throw CodedException("E_NO_PRINTER", RuntimeException("Printer not connected"))
        }

        // Print a Base64 image
        Function("printImage") { base64: String, gradient: Boolean? ->
            val p = printer ?: throw CodedException("E_NO_PRINTER", RuntimeException("Printer not connected"))
            try {
                val raw = Base64.decode(base64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                    ?: throw CodedException("E_DECODE_IMAGE", RuntimeException("Invalid image data"))
                val hex = PrinterTextParserImg.bitmapToHexadecimalString(p, bmp, gradient ?: false)
                p.printFormattedText("[C]<img>$hex</img>\n")
            } catch (e: Exception) {
                throw CodedException("E_PRINT_IMAGE", e)
            }
        }

        // Print barcode
        Function("printBarcode") { data: String, type: String?, width: Int?, height: Int?, textPosition: String?, align: String? ->
            val p = printer ?: throw CodedException("E_NO_PRINTER", RuntimeException("Printer not connected"))
            try {
                val al = when (align?.uppercase()) {
                    "C" -> "[C]"; "R" -> "[R]"; else -> "[L]"
                }
                val tag = buildString {
                    append(al).append("<barcode")
                    type?.let { append(" type='$it'") }
                    width?.let { append(" width='$it'") }
                    height?.let { append(" height='$it'") }
                    textPosition?.let { append(" text='$it'") }
                    append(">$data</barcode>\n")
                }
                p.printFormattedText(tag)
            } catch (e: Exception) {
                throw CodedException("E_PRINT_BARCODE", e)
            }
        }

        // Print QR code
        Function("printQRCode") { data: String, size: Int?, align: String? ->
            val p = printer ?: throw CodedException("E_NO_PRINTER", RuntimeException("Printer not connected"))
            try {
                val al = when (align?.uppercase()) {
                    "C" -> "[C]"; "R" -> "[R]"; else -> "[L]"
                }
                val sz = size ?: 20
                p.printFormattedText("$al<qrcode size='$sz'>$data</qrcode>\n")
            } catch (e: Exception) {
                throw CodedException("E_PRINT_QRCODE", e)
            }
        }

        // Feed paper
        Function("feedPaper") { mm: Float ->
            printer?.printFormattedText("", mm)
                ?: throw CodedException("E_NO_PRINTER", RuntimeException("Printer not connected"))
        }

        // Cut paper
        Function("cutPaper") {
            printer?.printFormattedTextAndCut("")
                ?: throw CodedException("E_NO_PRINTER", RuntimeException("Printer not connected"))
        }

        // Open cash drawer
        Function("openCashDrawer") {
            printer?.printFormattedTextAndOpenCashBox("", 0)
                ?: throw CodedException("E_NO_PRINTER", RuntimeException("Printer not connected"))
        }

        // ESC Asterisk command
        Function("useEscAsteriskCommand") { enable: Boolean ->
            printer?.useEscAsteriskCommand(enable)
                ?: throw CodedException("E_NO_PRINTER", RuntimeException("Printer not connected"))
        }

        // Printer info
        Function("getPrinterInfo") {
            val p = printer ?: throw CodedException("E_NO_PRINTER", RuntimeException("Printer not connected"))
            mapOf(
                "dpi" to p.printerDpi,
                "widthMM" to p.printerWidthMM,
                "widthPx" to p.printerWidthPx,
                "charsPerLine" to p.getPrinterNbrCharactersPerLine()
            )
        }

        // Millimeters to pixels
        Function("mmToPx") { mm: Float ->
            printer?.mmToPx(mm)
                ?: throw CodedException("E_NO_PRINTER", RuntimeException("Printer not connected"))
        }
    }
}
