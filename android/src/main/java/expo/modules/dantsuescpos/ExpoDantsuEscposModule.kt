package expo.modules.dantsuescpos

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Base64
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import android.Manifest
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class ExpoDantsuEscposModule : Module() {
    private var printer: EscPosPrinter? = null
    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 1
    private val ACTION_USB_PERMISSION = "com.github.expo.modules.dantsuescpos.USB_PERMISSION"

    private fun checkBluetoothPermissions(): Boolean {
        val activity = appContext.activityProvider?.currentActivity ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetoothConnect = ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
            if (bluetoothConnect != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    BLUETOOTH_PERMISSION_REQUEST_CODE
                )
                return false
            }
        } else {
            val bluetoothAdmin = ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_ADMIN)
            if (bluetoothAdmin != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.BLUETOOTH_ADMIN),
                    BLUETOOTH_PERMISSION_REQUEST_CODE
                )
                return false
            }
        }
        return true
    }

    private fun checkUsbPermissions(usbConnection: UsbConnection): Boolean {
        val activity = appContext.activityProvider?.currentActivity ?: return false
        val usbManager = activity.getSystemService(Context.USB_SERVICE) as UsbManager

        if (!usbManager.hasPermission(usbConnection.device)) {
            val permissionIntent = PendingIntent.getBroadcast(
                activity,
                0,
                Intent(ACTION_USB_PERMISSION),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
            usbManager.requestPermission(usbConnection.device, permissionIntent)
            return false
        }
        return true
    }

    private fun checkNetworkPermissions(): Boolean {
        val activity = appContext.activityProvider?.currentActivity ?: return false

        // Check for internet permission
        val internetPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.INTERNET)
        if (internetPermission != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        // Check for network state permission
        val networkStatePermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_NETWORK_STATE)
        if (networkStatePermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_NETWORK_STATE),
                2 // Using a different request code than Bluetooth
            )
            return false
        }

        return true
    }

    // Common printer ports
    private val COMMON_PRINTER_PORTS = listOf(9100, 515, 631)
    private val CONNECTION_TIMEOUT_MS = 300 // Short timeout for quick scanning

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getSubnetPrefix(ipAddress: String): String? {
        val lastDotIndex = ipAddress.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            ipAddress.substring(0, lastDotIndex + 1)
        } else {
            null
        }
    }

    private suspend fun scanForPrinters(): List<Map<String, Any>> = coroutineScope {
        val localIp = getLocalIpAddress() ?: return@coroutineScope emptyList()
        val subnetPrefix = getSubnetPrefix(localIp) ?: return@coroutineScope emptyList()

        val discoveredPrinters = mutableListOf<Map<String, Any>>()

        // Create tasks for all IP addresses in subnet (1-254) for common printer ports
        val scanTasks = mutableListOf<kotlinx.coroutines.Deferred<Map<String, Any>?>>()

        for (i in 1..254) {
            val ip = "$subnetPrefix$i"

            // Skip local IP (no need to scan ourselves)
            if (ip == localIp) continue

            for (port in COMMON_PRINTER_PORTS) {
                scanTasks.add(async(Dispatchers.IO) {
                    try {
                        val socket = Socket()
                        socket.connect(java.net.InetSocketAddress(ip, port), CONNECTION_TIMEOUT_MS)
                        socket.close()
                        // If connection succeeded, this could be a printer
                        mapOf(
                            "address" to ip,
                            "port" to port,
                            "deviceName" to "Printer at $ip:$port",
                            "status" to "available"
                        )
                    } catch (e: Exception) {
                        null // Connection failed, not a printer or not accessible
                    }
                })
            }
        }

        // Wait for all scan tasks to complete and filter out null results
        scanTasks.awaitAll().filterNotNull().forEach {
            discoveredPrinters.add(it)
        }

        discoveredPrinters
    }

    @SuppressLint("MissingPermission")
    override fun definition() = ModuleDefinition {
        Name("ExpoDantsuEscposModule")

        // List TCP printers on the network
        AsyncFunction("getTcpDevices") {
            try {
                val activity: Activity = appContext.activityProvider?.currentActivity
                    ?: throw CodedException("E_NO_ACTIVITY", RuntimeException("Activity unavailable"))

                if (!checkNetworkPermissions()) {
                    throw CodedException("E_NETWORK_PERMISSION", RuntimeException("Network permissions not granted"))
                }

                kotlinx.coroutines.runBlocking {
                    scanForPrinters()
                }
            } catch (e: Exception) {
                android.util.Log.e("ExpoDantsuEscposModule", "TCP scan error: ${e.message}", e)
                throw CodedException("E_TCP_SCAN", e)
            }
        }

        // List paired Bluetooth printers
        AsyncFunction("getBluetoothDevices") {
            if (!checkBluetoothPermissions()) {
                throw CodedException("E_BT_PERMISSION", RuntimeException("Bluetooth permissions not granted"))
            }

            val connections = BluetoothPrintersConnections()
            val list = connections.getList()
            list?.map {
                mapOf(
                    "deviceName" to it.device.name,
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
        AsyncFunction("connectBluetooth") { address: String?, printerDpi: Int?, printerWidthMM: Float?, printerNbrCharactersPerLine: Int? ->
            try {
                if (!checkBluetoothPermissions()) {
                    throw CodedException("E_BT_PERMISSION", RuntimeException("Bluetooth permissions not granted"))
                }

                val connDevice: BluetoothConnection = if (address.isNullOrEmpty()) {
                    BluetoothPrintersConnections.selectFirstPaired()
                        ?: throw CodedException("E_NO_BT_PRINTER", RuntimeException("No paired Bluetooth printer"))
                } else {
                    val connections = BluetoothPrintersConnections()
                    connections.getList()
                        ?.firstOrNull { it.device.address == address }
                        ?: throw CodedException("E_NO_BT_PRINTER", RuntimeException("Printer '$address' not found"))
                }

                printer = EscPosPrinter(
                    connDevice,
                    printerDpi ?: 203,
                    printerWidthMM ?: 48f,
                    printerNbrCharactersPerLine ?: 32
                )
            } catch (e: Exception) {
                throw CodedException("E_BT_CONNECT", e)
            }
        }

        // Connect via USB
        AsyncFunction("connectUsb") { vendorId: Int?, productId: Int?, printerDpi: Int?, printerWidthMM: Float?, printerNbrCharactersPerLine: Int? ->
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

                if (!checkUsbPermissions(connDevice)) {
                    throw CodedException("E_USB_PERMISSION", RuntimeException("USB permissions not granted"))
                }

                printer = EscPosPrinter(
                    connDevice,
                    printerDpi ?: 203,
                    printerWidthMM ?: 48f,
                    printerNbrCharactersPerLine ?: 32
                )
            } catch (e: Exception) {
                throw CodedException("E_USB_CONNECT", e)
            }
        }

        // Connect via TCP
        Function("connectTcp") { address: String, port: Int, timeout: Int?, printerDpi: Int?, printerWidthMM: Float?, printerNbrCharactersPerLine: Int? ->
            try {
                val conn = timeout?.let { TcpConnection(address, port, it) } ?: TcpConnection(address, port)
                printer = EscPosPrinter(
                    conn,
                    printerDpi ?: 203,
                    printerWidthMM ?: 48f,
                    printerNbrCharactersPerLine ?: 32
                )
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
