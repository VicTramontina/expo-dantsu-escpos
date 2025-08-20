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
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.util.UUID
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
    private var activeBluetoothSocket: BluetoothSocket? = null
    private val connectedDevices = mutableSetOf<String>()
    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 1
    private val ACTION_USB_PERMISSION = "com.github.expo.modules.dantsuescpos.USB_PERMISSION"
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private fun checkBluetoothPermissions(): Boolean {
        val activity = appContext.activityProvider?.currentActivity ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetoothConnect = ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT)
            val bluetoothScan = ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN)
            if (bluetoothConnect != PackageManager.PERMISSION_GRANTED || bluetoothScan != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
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

    @SuppressLint("MissingPermission")
    private fun createDirectBluetoothConnection(device: BluetoothDevice): BluetoothConnection {
        return object : BluetoothConnection(device) {
            private var bluetoothSocket: BluetoothSocket? = null
            private var isConnectedFlag = false
            
            override fun connect(): BluetoothConnection {
                if (isConnectedFlag) {
                    // Already connected, just return this
                    return this
                }
                
                try {
                    // Cancel discovery to improve connection performance
                    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    if (bluetoothAdapter?.isDiscovering == true) {
                        bluetoothAdapter.cancelDiscovery()
                    }
                    
                    // Create insecure RFCOMM socket
                    bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    
                    // Connect to the device
                    bluetoothSocket?.connect()
                    
                    // Store the active socket for cleanup
                    activeBluetoothSocket = bluetoothSocket
                    isConnectedFlag = true
                    
                    android.util.Log.d("ExpoDantsuEscposModule", "Successfully connected to ${device.address}")
                    
                    return this
                } catch (e: Exception) {
                    android.util.Log.e("ExpoDantsuEscposModule", "Direct connection failed to ${device.address}: ${e.message}")
                    throw EscPosConnectionException("Unable to connect to bluetooth device: ${e.message}")
                }
            }
            
            override fun disconnect(): BluetoothConnection {
                try {
                    bluetoothSocket?.close()
                    bluetoothSocket = null
                    activeBluetoothSocket = null
                    isConnectedFlag = false
                } catch (e: Exception) {
                    android.util.Log.w("ExpoDantsuEscposModule", "Disconnect warning: ${e.message}")
                }
                return this
            }
            
            override fun isConnected(): Boolean {
                return isConnectedFlag && bluetoothSocket?.isConnected == true
            }
            
            override fun write(bytes: ByteArray?) {
                bluetoothSocket?.outputStream?.write(bytes)
                bluetoothSocket?.outputStream?.flush()
            }
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

        // Scan for all Bluetooth devices (paired + nearby)
        AsyncFunction("getBluetoothDevices") {
            if (!checkBluetoothPermissions()) {
                throw CodedException("E_BT_PERMISSION", RuntimeException("Bluetooth permissions not granted"))
            }

            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                ?: throw CodedException("E_NO_BT_ADAPTER", RuntimeException("Bluetooth adapter not available"))

            if (!bluetoothAdapter.isEnabled) {
                throw CodedException("E_BT_DISABLED", RuntimeException("Bluetooth is disabled"))
            }

            val devices = mutableListOf<Map<String, Any>>()
            val discoveredDevices = mutableSetOf<String>()

            // First add paired devices
            val pairedDevices = bluetoothAdapter.bondedDevices
            pairedDevices?.forEach { device ->
                devices.add(mapOf(
                    "deviceName" to (device.name ?: "Unknown Device"),
                    "address" to device.address,
                    "type" to "paired",
                    "bondState" to device.bondState
                ))
                discoveredDevices.add(device.address)
            }

            // Start discovery for nearby devices
            try {
                if (bluetoothAdapter.isDiscovering) {
                    bluetoothAdapter.cancelDiscovery()
                }
                
                // Register broadcast receiver for device discovery
                val discoveryReceiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                        when (intent?.action) {
                            BluetoothDevice.ACTION_FOUND -> {
                                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                device?.let {
                                    if (!discoveredDevices.contains(it.address)) {
                                        devices.add(mapOf(
                                            "deviceName" to (it.name ?: "Unknown Device"),
                                            "address" to it.address,
                                            "type" to "nearby",
                                            "bondState" to it.bondState
                                        ))
                                        discoveredDevices.add(it.address)
                                    }
                                }
                            }
                        }
                    }
                }
                
                val activity = appContext.activityProvider?.currentActivity
                activity?.registerReceiver(discoveryReceiver, android.content.IntentFilter(BluetoothDevice.ACTION_FOUND))
                
                bluetoothAdapter.startDiscovery()
                
                // Wait for discovery to complete
                Thread.sleep(10000)
                
                bluetoothAdapter.cancelDiscovery()
                activity?.unregisterReceiver(discoveryReceiver)
            } catch (e: Exception) {
                android.util.Log.w("ExpoDantsuEscposModule", "Discovery failed: ${e.message}")
            }

            devices
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

        // Connect via Bluetooth with direct connection capability
        AsyncFunction("connectBluetooth") { address: String?, printerDpi: Int?, printerWidthMM: Float?, printerNbrCharactersPerLine: Int? ->
            try {
                if (!checkBluetoothPermissions()) {
                    android.util.Log.e("ExpoDantsuEscposModule", "Bluetooth permissions not granted")
                    throw CodedException("E_BT_PERMISSION", RuntimeException("Bluetooth permissions not granted"))
                }

                // Check if device is already connected
                address?.let { addr ->
                    if (connectedDevices.contains(addr)) {
                        throw CodedException("E_PRINTER_BUSY", RuntimeException("Printer at address $addr is already connected"))
                    }
                }

                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    ?: throw CodedException("E_NO_BT_ADAPTER", RuntimeException("Bluetooth adapter not available"))

                if (!bluetoothAdapter.isEnabled) {
                    throw CodedException("E_BT_DISABLED", RuntimeException("Bluetooth is disabled"))
                }

                // Always use direct insecure connection
                val connDevice: BluetoothConnection = if (address.isNullOrEmpty()) {
                    throw CodedException("E_NO_ADDRESS", RuntimeException("MAC address is required for connection"))
                } else {
                    // Connect directly by MAC address using insecure connection
                    val targetDevice = try {
                        bluetoothAdapter.getRemoteDevice(address)
                    } catch (e: IllegalArgumentException) {
                        throw CodedException("E_INVALID_ADDRESS", RuntimeException("Invalid MAC address: $address"))
                    }

                    // Always create direct insecure connection
                    val directConnection = createDirectBluetoothConnection(targetDevice)
                    directConnection
                }

                // Mark device as connected
                address?.let { connectedDevices.add(it) }

                printer = EscPosPrinter(
                    connDevice,
                    printerDpi ?: 203,
                    printerWidthMM ?: 48f,
                    printerNbrCharactersPerLine ?: 32
                )
            } catch (e: Exception) {
                android.util.Log.e("ExpoDantsuEscposModule", "Bluetooth connect error: ${e.message}", e)
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
            try {
                printer?.disconnectPrinter()
                activeBluetoothSocket?.close()
                activeBluetoothSocket = null
                connectedDevices.clear()
                printer = null
            } catch (e: Exception) {
                android.util.Log.w("ExpoDantsuEscposModule", "Disconnect warning: ${e.message}")
            }
            Unit
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
