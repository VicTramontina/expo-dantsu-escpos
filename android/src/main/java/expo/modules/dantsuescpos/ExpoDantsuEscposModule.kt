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
import com.dantsu.escposprinter.connection.DeviceConnection
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import android.Manifest
import android.net.wifi.WifiManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.IntentFilter
import java.io.IOException
import java.io.OutputStream
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.UUID
import java.util.regex.Pattern

class ExpoDantsuEscposModule : Module() {
    private var printer: EscPosPrinter? = null
    private var customDeviceConnection: InsecureBluetoothConnection? = null
    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 1
    private val ACTION_USB_PERMISSION = "com.github.expo.modules.dantsuescpos.USB_PERMISSION"
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    private var isConnecting = false

    inner class InsecureBluetoothConnection(private val device: BluetoothDevice) : DeviceConnection() {
        private var socket: BluetoothSocket? = null

        @SuppressLint("MissingPermission")
        override fun connect(): InsecureBluetoothConnection {
            if (isConnected()) {
                return this
            }

            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            bluetoothAdapter?.cancelDiscovery()

            try {
                socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                socket?.connect()
                outputStream = socket?.outputStream
                data = byteArrayOf()
            } catch (e: IOException) {
                android.util.Log.e("ExpoDantsuEscposModule", "Insecure connection failed: ${e.message}", e)
                disconnect()
                throw e
            }
            return this
        }

        override fun isConnected(): Boolean {
            return socket?.isConnected == true && super.isConnected()
        }

        override fun disconnect(): InsecureBluetoothConnection {
            data = byteArrayOf()
            outputStream?.let {
                try {
                    it.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                outputStream = null
            }
            socket?.let {
                try {
                    it.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                socket = null
            }
            return this
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun discoverBluetoothDevices(
        scanMillis: Int,
        nameRegex: String?,
        includeRssi: Boolean
    ): List<Map<String, Any?>> = suspendCancellableCoroutine { continuation ->
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            continuation.resume(emptyList())
            return@suspendCancellableCoroutine
        }

        val discoveredDevices = mutableMapOf<String, MutableMap<String, Any?>>()
        val namePattern = nameRegex?.let { Pattern.compile(it, Pattern.CASE_INSENSITIVE) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        val rssi = if (includeRssi) intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt() else null
                        
                        device?.let {
                            val deviceName = it.name
                            if (namePattern == null || deviceName?.let { name -> namePattern.matcher(name).find() } == true) {
                                val address = it.address
                                val existing = discoveredDevices[address]
                                if (existing == null) {
                                    discoveredDevices[address] = mutableMapOf(
                                        "deviceName" to deviceName,
                                        "address" to address,
                                        "bonded" to (it.bondState == BluetoothDevice.BOND_BONDED),
                                        "rssi" to rssi,
                                        "source" to "scan"
                                    )
                                } else {
                                    existing["source"] = "both"
                                    if (rssi != null && existing["rssi"] == null) {
                                        existing["rssi"] = rssi
                                    }
                                }
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        try {
                            appContext.reactContext?.unregisterReceiver(this)
                        } catch (e: Exception) {
                            android.util.Log.w("ExpoDantsuEscposModule", "Error unregistering receiver: ${e.message}")
                        }
                        continuation.resume(discoveredDevices.values.toList())
                    }
                }
            }
        }

        continuation.invokeOnCancellation {
            try {
                bluetoothAdapter.cancelDiscovery()
                appContext.reactContext?.unregisterReceiver(receiver)
            } catch (e: Exception) {
                android.util.Log.w("ExpoDantsuEscposModule", "Error cleaning up discovery: ${e.message}")
            }
        }

        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            appContext.reactContext?.registerReceiver(receiver, filter)
            
            bluetoothAdapter.cancelDiscovery()
            if (!bluetoothAdapter.startDiscovery()) {
                appContext.reactContext?.unregisterReceiver(receiver)
                continuation.resume(emptyList())
            }
        } catch (e: Exception) {
            android.util.Log.e("ExpoDantsuEscposModule", "Error starting discovery: ${e.message}", e)
            continuation.resume(emptyList())
        }
    }

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
            val accessFineLocation = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
            if (bluetoothAdmin != PackageManager.PERMISSION_GRANTED || accessFineLocation != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION),
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

        // List Bluetooth devices (bonded + discovered)
        AsyncFunction("getBluetoothDevices") { options: Map<String, Any>? ->
            android.util.Log.d("ExpoDantsuEscposModule", "BT/SCAN: Starting enhanced Bluetooth scan")
            
            if (!checkBluetoothPermissions()) {
                throw CodedException("E_BT_PERMISSION", RuntimeException("Bluetooth permissions not granted"))
            }

            val scanMillis = (options?.get("scanMillis") as? Number)?.toInt() ?: 5000
            val nameRegex = options?.get("nameRegex") as? String
            val includeRssi = (options?.get("includeRssi") as? Boolean) ?: true
            val includeBondedOnly = (options?.get("includeBondedOnly") as? Boolean) ?: false

            try {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                    throw CodedException("E_BT_DISABLED", RuntimeException("Bluetooth not available or disabled"))
                }

                val devices = mutableMapOf<String, MutableMap<String, Any?>>()
                val namePattern = nameRegex?.let { Pattern.compile(it, Pattern.CASE_INSENSITIVE) }

                // Get bonded devices first
                val bondedDevices = bluetoothAdapter.bondedDevices ?: emptySet()
                android.util.Log.d("ExpoDantsuEscposModule", "BT/SCAN: Found ${bondedDevices.size} bonded devices")

                for (device in bondedDevices) {
                    val deviceName = device.name
                    if (namePattern == null || deviceName?.let { name -> namePattern.matcher(name).find() } == true) {
                        devices[device.address] = mutableMapOf(
                            "deviceName" to deviceName,
                            "address" to device.address,
                            "bonded" to true,
                            "rssi" to null,
                            "source" to "bonded"
                        )
                    }
                }

                // Discover new devices if not bonded-only
                if (!includeBondedOnly) {
                    try {
                        android.util.Log.d("ExpoDantsuEscposModule", "BT/SCAN: Starting discovery for ${scanMillis}ms")
                        val discoveredDevices = kotlinx.coroutines.runBlocking {
                            discoverBluetoothDevices(scanMillis, nameRegex, includeRssi)
                        }
                        
                        android.util.Log.d("ExpoDantsuEscposModule", "BT/SCAN: Discovery found ${discoveredDevices.size} devices")
                        
                        // Merge discovered devices with bonded ones
                        for (discoveredDevice in discoveredDevices) {
                            val address = discoveredDevice["address"] as String
                            val existing = devices[address]
                            if (existing == null) {
                                devices[address] = discoveredDevice.toMutableMap()
                            } else {
                                existing["source"] = "both"
                                if (discoveredDevice["rssi"] != null && existing["rssi"] == null) {
                                    existing["rssi"] = discoveredDevice["rssi"]
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("ExpoDantsuEscposModule", "E_BT_SCAN_WARN: Discovery failed, continuing with bonded only: ${e.message}")
                    }
                }

                // Sort devices: bonded first, then by RSSI descending, then by name
                val sortedDevices = devices.values.sortedWith { a, b ->
                    val aBonded = a["bonded"] as Boolean
                    val bBonded = b["bonded"] as Boolean
                    
                    when {
                        aBonded && !bBonded -> -1
                        !aBonded && bBonded -> 1
                        else -> {
                            val aRssi = a["rssi"] as? Int ?: Int.MIN_VALUE
                            val bRssi = b["rssi"] as? Int ?: Int.MIN_VALUE
                            when {
                                aRssi != bRssi -> bRssi.compareTo(aRssi) // Descending RSSI
                                else -> {
                                    val aName = a["deviceName"] as? String ?: ""
                                    val bName = b["deviceName"] as? String ?: ""
                                    aName.compareTo(bName)
                                }
                            }
                        }
                    }
                }

                android.util.Log.d("ExpoDantsuEscposModule", "BT/SCAN: Returning ${sortedDevices.size} total devices")
                sortedDevices
            } catch (e: Exception) {
                android.util.Log.e("ExpoDantsuEscposModule", "BT/SCAN: Error during scan: ${e.message}", e)
                throw CodedException("E_BT_SCAN", e)
            }
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

        // Enhanced Bluetooth connection with insecure SPP support
        AsyncFunction("connectBluetooth") { options: Map<String, Any>? ->
            if (isConnecting) {
                throw CodedException("E_BT_CONNECTING", RuntimeException("Connection already in progress"))
            }
            
            try {
                isConnecting = true
                android.util.Log.d("ExpoDantsuEscposModule", "BT/CONNECT: Starting enhanced Bluetooth connection")
                
                if (!checkBluetoothPermissions()) {
                    android.util.Log.e("ExpoDantsuEscposModule", "Bluetooth permissions not granted")
                    throw CodedException("E_BT_PERMISSION", RuntimeException("Bluetooth permissions not granted"))
                }

                val address = options?.get("address") as? String
                val preferInsecureIfUnbonded = (options?.get("preferInsecureIfUnbonded") as? Boolean) ?: true
                val timeoutMs = (options?.get("timeoutMs") as? Number)?.toInt() ?: 15000
                val allowSecureFallback = (options?.get("allowSecureFallback") as? Boolean) ?: true
                val nameHint = options?.get("nameHint") as? String
                val printerDpi = (options?.get("printerDpi") as? Number)?.toInt() ?: 203
                val printerWidthMM = (options?.get("printerWidthMM") as? Number)?.toFloat() ?: 48f
                val printerNbrCharactersPerLine = (options?.get("printerNbrCharactersPerLine") as? Number)?.toInt() ?: 32

                if (address.isNullOrEmpty()) {
                    throw CodedException("E_NO_BT_PRINTER", RuntimeException("Address is required for enhanced Bluetooth connection"))
                }

                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                    throw CodedException("E_BT_DISABLED", RuntimeException("Bluetooth not available or disabled"))
                }

                // Cancel any ongoing discovery
                bluetoothAdapter.cancelDiscovery()

                // Get the target device
                val targetDevice = bluetoothAdapter.getRemoteDevice(address)
                    ?: throw CodedException("E_NO_BT_PRINTER", RuntimeException("Device '$address' not found"))

                val bondState = targetDevice.bondState
                val logPrefix = "BT/CONNECT: $address"
                nameHint?.let { android.util.Log.d("ExpoDantsuEscposModule", "$logPrefix (${it})") }
                android.util.Log.d("ExpoDantsuEscposModule", "$logPrefix bondState=${when(bondState) {
                    BluetoothDevice.BOND_BONDED -> "BONDED"
                    BluetoothDevice.BOND_BONDING -> "BONDING"
                    else -> "NONE"
                }}")

                // Check if we already have a connection to this device
                if (printer != null && customDeviceConnection?.isConnected() == true) {
                    android.util.Log.d("ExpoDantsuEscposModule", "$logPrefix Already connected, reusing connection")
                    return@AsyncFunction mapOf(
                        "connectionMode" to "existing",
                        "dpi" to printerDpi,
                        "widthMM" to printerWidthMM,
                        "charsPerLine" to printerNbrCharactersPerLine
                    )
                }

                // Disconnect any existing connection
                customDeviceConnection?.disconnect()
                customDeviceConnection = null
                printer?.disconnectPrinter()
                printer = null

                var connectionMode: String? = null
                var deviceConnection: DeviceConnection? = null
                var lastError: Exception? = null

                // Determine connection strategy based on bond state
                val tryInsecureFirst = preferInsecureIfUnbonded && bondState != BluetoothDevice.BOND_BONDED
                val trySecureFirst = !tryInsecureFirst

                android.util.Log.d("ExpoDantsuEscposModule", "$logPrefix Strategy: ${if (tryInsecureFirst) "insecure-first" else "secure-first"}")

                // Try insecure connection first if device is not bonded
                if (tryInsecureFirst) {
                    try {
                        android.util.Log.d("ExpoDantsuEscposModule", "$logPrefix Attempting insecure connection")
                        val startTime = System.currentTimeMillis()
                        
                        customDeviceConnection = InsecureBluetoothConnection(targetDevice)
                        customDeviceConnection!!.connect()
                        deviceConnection = customDeviceConnection
                        connectionMode = "insecure"
                        
                        val duration = System.currentTimeMillis() - startTime
                        android.util.Log.d("ExpoDantsuEscposModule", "$logPrefix Insecure connection successful (${duration}ms)")
                    } catch (e: Exception) {
                        android.util.Log.w("ExpoDantsuEscposModule", "$logPrefix Insecure connection failed: ${e.message}")
                        lastError = e
                        customDeviceConnection?.disconnect()
                        customDeviceConnection = null
                        
                        // Try secure fallback if allowed and device is bonded
                        if (allowSecureFallback && bondState == BluetoothDevice.BOND_BONDED) {
                            try {
                                android.util.Log.d("ExpoDantsuEscposModule", "$logPrefix Attempting secure fallback")
                                val startTime = System.currentTimeMillis()
                                
                                bluetoothAdapter.cancelDiscovery()
                                val secureConnection = BluetoothConnection(targetDevice)
                                secureConnection.connect()
                                deviceConnection = secureConnection
                                connectionMode = "secure"
                                
                                val duration = System.currentTimeMillis() - startTime
                                android.util.Log.d("ExpoDantsuEscposModule", "$logPrefix Secure fallback successful (${duration}ms)")
                            } catch (secureError: Exception) {
                                android.util.Log.e("ExpoDantsuEscposModule", "$logPrefix Secure fallback also failed: ${secureError.message}")
                                lastError = secureError
                            }
                        }
                    }
                }

                // Try secure connection first if device is bonded
                if (trySecureFirst && deviceConnection == null) {
                    try {
                        android.util.Log.d("ExpoDantsuEscposModule", "$logPrefix Attempting secure connection")
                        val startTime = System.currentTimeMillis()
                        
                        bluetoothAdapter.cancelDiscovery()
                        val secureConnection = BluetoothConnection(targetDevice)
                        secureConnection.connect()
                        deviceConnection = secureConnection
                        connectionMode = "secure"
                        
                        val duration = System.currentTimeMillis() - startTime
                        android.util.Log.d("ExpoDantsuEscposModule", "$logPrefix Secure connection successful (${duration}ms)")
                    } catch (e: Exception) {
                        android.util.Log.w("ExpoDantsuEscposModule", "$logPrefix Secure connection failed: ${e.message}")
                        lastError = e
                        
                        // Try insecure fallback if allowed
                        if (allowSecureFallback) {
                            try {
                                android.util.Log.d("ExpoDantsuEscposModule", "$logPrefix Attempting insecure fallback")
                                val startTime = System.currentTimeMillis()
                                
                                customDeviceConnection = InsecureBluetoothConnection(targetDevice)
                                customDeviceConnection!!.connect()
                                deviceConnection = customDeviceConnection
                                connectionMode = "insecure"
                                
                                val duration = System.currentTimeMillis() - startTime
                                android.util.Log.d("ExpoDantsuEscposModule", "$logPrefix Insecure fallback successful (${duration}ms)")
                            } catch (insecureError: Exception) {
                                android.util.Log.e("ExpoDantsuEscposModule", "$logPrefix Insecure fallback also failed: ${insecureError.message}")
                                lastError = insecureError
                            }
                        }
                    }
                }

                if (deviceConnection == null || connectionMode == null) {
                    throw CodedException("E_BT_CONNECT", lastError ?: RuntimeException("All connection attempts failed"))
                }

                // Initialize printer with the successful connection
                printer = EscPosPrinter(
                    deviceConnection,
                    printerDpi,
                    printerWidthMM,
                    printerNbrCharactersPerLine
                )

                android.util.Log.d("ExpoDantsuEscposModule", "$logPrefix Connection established successfully ($connectionMode)")
                
                mapOf(
                    "connectionMode" to connectionMode,
                    "dpi" to printerDpi,
                    "widthMM" to printerWidthMM,
                    "charsPerLine" to printerNbrCharactersPerLine
                )
            } catch (e: Exception) {
                android.util.Log.e("ExpoDantsuEscposModule", "BT/CONNECT: Error: ${e.message}", e)
                throw CodedException("E_BT_CONNECT", e)
            } finally {
                isConnecting = false
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

        // Enhanced disconnect function
        Function("disconnect") {
            try {
                android.util.Log.d("ExpoDantsuEscposModule", "Disconnecting printer")
                
                // Disconnect custom insecure Bluetooth connection if exists
                customDeviceConnection?.disconnect()
                customDeviceConnection = null
                
                // Disconnect standard printer connection (works for all types: USB, TCP, Bluetooth)
                printer?.disconnectPrinter()
                printer = null
                
                android.util.Log.d("ExpoDantsuEscposModule", "Disconnection complete")
            } catch (e: Exception) {
                android.util.Log.w("ExpoDantsuEscposModule", "Error during disconnect: ${e.message}")
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
