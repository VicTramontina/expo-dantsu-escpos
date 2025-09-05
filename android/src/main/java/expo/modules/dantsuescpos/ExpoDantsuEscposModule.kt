package expo.modules.dantsuescpos

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.DeviceConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnections
import com.dantsu.escposprinter.connection.tcp.TcpConnection
import com.dantsu.escposprinter.connection.usb.UsbConnections
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ExpoDantsuEscposModule : Module() {
    private var printer: EscPosPrinter? = null

    companion object {
        private const val TAG = "ExpoDantsuEscpos"
        private const val REQ_PERMISSIONS = 0xD001
        private const val REQ_ENABLE_BT = 0xD002
    }

    override fun definition() = ModuleDefinition {
        Name("ExpoDantsuEscposModule")

        // You can emit later if you wire into dantsu's disconnect callbacks
        Events("printerDisconnected")

        /**
         * ========== BLUETOOTH LIST (PAIRED) ==========
         * Will request BLUETOOTH_CONNECT (Android 12+) and to enable BT if powered off.
         * If it had to request, it throws a coded error; call this function again afterwards.
         */
        @SuppressLint("MissingPermission")
        AsyncFunction("getBluetoothDevices") {
            val activity = currentActivityOrThrow()
            if (!ensureBluetoothEnabled(activity)) {
                throw CodedException(
                    "BLUETOOTH_ENABLE_REQUESTED",
                    "Requested user to enable Bluetooth. Call again after user accepts.",
                    null
                )
            }
            if (!ensurePermissionsRequested(activity, requiredPermsForPairedList())) {
                throw CodedException(
                    "PERMISSION_REQUESTED",
                    "Requested Bluetooth permission(s). Call again after user grants.",
                    null
                )
            }

            val list = mutableListOf<Map<String, String>>()
            BluetoothConnections().getList()?.forEach { conn ->
                conn.device?.let { device ->
                    list.add(mapOf("name" to (device.name ?: ""), "address" to device.address))
                }
            }
            list
        }

        /**
         * ========== BLUETOOTH LIST (UNPAIRED / DISCOVERY) ==========
         * Will request BLUETOOTH_SCAN (+ CONNECT) on Android 12+, or FINE_LOCATION on older.
         * Also asks user to enable BT if off.
         */
        @SuppressLint("MissingPermission")
        AsyncFunction("getUnpairedBluetoothDevices") {
            val activity = currentActivityOrThrow()
            if (!ensureBluetoothEnabled(activity)) {
                throw CodedException(
                    "BLUETOOTH_ENABLE_REQUESTED",
                    "Requested user to enable Bluetooth. Call again after user accepts.",
                    null
                )
            }
            if (!ensurePermissionsRequested(activity, requiredPermsForDiscovery())) {
                throw CodedException(
                    "PERMISSION_REQUESTED",
                    "Requested Bluetooth/Location permission(s). Call again after user grants.",
                    null
                )
            }

            val list = mutableListOf<Map<String, String>>()
            val context = appContext.reactContext ?: activity.applicationContext
            BluetoothConnections().getUnpairedList(context)?.forEach { conn ->
                conn.device?.let { device ->
                    list.add(mapOf("name" to (device.name ?: ""), "address" to device.address))
                }
            }
            list
        }

        /**
         * ========== BLUETOOTH CONNECT ==========
         * Requests enable and permissions if needed before connecting.
         */
        @SuppressLint("MissingPermission")
        AsyncFunction("connectBluetooth") { address: String, dpi: Int, widthMM: Double, nbrCharactersPerLine: Int ->
            val activity = currentActivityOrThrow()
            if (!ensureBluetoothEnabled(activity)) {
                throw CodedException(
                    "BLUETOOTH_ENABLE_REQUESTED",
                    "Requested user to enable Bluetooth. Call again after user accepts.",
                    null
                )
            }
            if (!ensurePermissionsRequested(activity, requiredPermsForConnect())) {
                throw CodedException(
                    "PERMISSION_REQUESTED",
                    "Requested Bluetooth permission(s). Call again after user grants.",
                    null
                )
            }

            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: throw CodedException("BLUETOOTH_UNAVAILABLE", "Bluetooth adapter not available.", null)

            val device = try {
                adapter.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                throw CodedException("INVALID_BLUETOOTH_ADDRESS", "Invalid bluetooth address: $address", e)
            }

            val connection = try {
                BluetoothConnection(device).connect()
            } catch (e: EscPosConnectionException) {
                throw CodedException("BLUETOOTH_CONNECTION_FAILED", "Failed to connect via Bluetooth: ${e.message}", e)
            }

            printer = EscPosPrinter(connection, dpi, widthMM.toFloat(), nbrCharactersPerLine)
            Unit
        }

        /**
         * ========== DISCONNECT ==========
         */
        AsyncFunction("disconnectPrinter") {
            try {
                printer?.disconnectPrinter()
            } catch (e: Exception) {
                Log.w(TAG, "disconnectPrinter error: ${e.message}")
            } finally {
                printer = null
            }
            Unit
        }

        /**
         * ========== PRINTING ==========
         */
        AsyncFunction("useEscAsteriskCommand") { enable: Boolean ->
            printerOrThrow().useEscAsteriskCommand(enable)
            Unit
        }

        AsyncFunction("printFormattedText") { text: String, mmFeedPaper: Double? ->
            val feed = mmFeedPaper?.toFloat() ?: 0f
            printerOrThrow().printFormattedText(text, feed)
            Unit
        }

        AsyncFunction("printFormattedTextAndCut") { text: String, mmFeedPaper: Double? ->
            Log.d(TAG, "printFormattedTextAndCut: $text")
            printerOrThrow().printFormattedTextAndCut(text)
            Unit
        }

        AsyncFunction("printFormattedTextAndOpenCashBox") { text: String, mmFeedPaper: Double? ->
            val feed = mmFeedPaper?.toFloat() ?: 0f
            printerOrThrow().printFormattedTextAndOpenCashBox(text, feed)
            Unit
        }

        /**
         * ========== USB LIST ==========
         * Just enumerates. No permission needed for listing.
         */
        AsyncFunction("getUSBDevices") {
            val activity = currentActivityOrThrow()
            val context = appContext.reactContext ?: activity.applicationContext
            val list = mutableListOf<Map<String, Any>>()
            UsbConnections(context).list?.forEach { conn ->
                val device = conn.device
                list.add(
                    mapOf(
                        "name" to (device.productName ?: ""),
                        "vendorId" to device.vendorId,
                        "productId" to device.productId
                    )
                )
            }
            list
        }

        /**
         * ========== USB CONNECT ==========
         * Will request (and trigger system dialog for) USB permission if needed.
         * If permission is requested now, throws USB_PERMISSION_REQUESTED; call again after user responds.
         */
        AsyncFunction("connectUSB") { vendorId: Int, productId: Int, dpi: Int, widthMM: Double, nbrCharactersPerLine: Int ->
            val activity = currentActivityOrThrow()
            val context = appContext.reactContext ?: activity.applicationContext

            val usbConn = UsbConnections(context).list
                ?.firstOrNull { it.device.vendorId == vendorId && it.device.productId == productId }
                ?: throw CodedException(
                    "USB_NOT_FOUND",
                    "USB device not found (vendorId=$vendorId, productId=$productId).",
                    null
                )

            val device = usbConn.device
            val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager

            if (!manager.hasPermission(device)) {
                // Trigger permission dialog and exit; user will act, then your JS can call this again.
                requestUsbPermissionOnce(activity, manager, device)
                throw CodedException(
                    "USB_PERMISSION_REQUESTED",
                    "Requested USB permission for device. Call connectUSB again after user grants.",
                    null
                )
            }

            val connection = try {
                usbConn.connect()
            } catch (e: EscPosConnectionException) {
                throw CodedException("USB_CONNECTION_FAILED", "Failed to connect via USB: ${e.message}", e)
            }

            printer = EscPosPrinter(connection, dpi, widthMM.toFloat(), nbrCharactersPerLine)
            Unit
        }

        /**
         * ========== TCP CONNECT ==========
         */
        AsyncFunction("connectTCP") { address: String, port: Int, dpi: Int, widthMM: Double, nbrCharactersPerLine: Int ->
            val connection: DeviceConnection = try {
                TcpConnection(address, port).connect()
            } catch (e: EscPosConnectionException) {
                throw CodedException("TCP_CONNECTION_FAILED", "Failed to connect via TCP: ${e.message}", e)
            }
            printer = EscPosPrinter(connection, dpi, widthMM.toFloat(), nbrCharactersPerLine)
            Unit
        }
    }

    // --------------------- Helpers ---------------------

    private fun currentActivityOrThrow(): Activity {
        return appContext.activityProvider?.currentActivity
            ?: throw CodedException("NO_ACTIVITY", "No current Activity available to request permissions.", null)
    }

    private fun printerOrThrow(): EscPosPrinter {
        return printer ?: throw CodedException("NO_PRINTER", "No active printer connection.", null)
    }

    private fun requiredPermsForPairedList(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }
    }

    private fun requiredPermsForConnect(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }
    }

    private fun requiredPermsForDiscovery(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /**
     * If any permission is missing, request it and return false.
     * If all are already granted, return true.
     */
    private fun ensurePermissionsRequested(activity: Activity, permissions: Array<String>): Boolean {
        if (permissions.isEmpty()) return true
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return true

        ActivityCompat.requestPermissions(activity, missing.toTypedArray(), REQ_PERMISSIONS)
        Log.i(TAG, "Requested permissions: $missing")
        return false
    }

    /**
     * If BT is disabled, triggers system dialog and returns false.
     * If enabled, returns true.
     */
    private fun ensureBluetoothEnabled(activity: Activity): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        if (adapter.isEnabled) return true

        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        activity.startActivityForResult(intent, REQ_ENABLE_BT)
        Log.i(TAG, "Requested user to enable Bluetooth")
        return false
    }

    /**
     * Fire a one-shot USB permission request for this device.
     * We don't wait here; we simply show the dialog and return.
     * Next call to connectUSB should see permission granted.
     */
    private fun requestUsbPermissionOnce(
        activity: Activity,
        manager: UsbManager,
        device: UsbDevice
    ) {
        val action = "${activity.packageName}.USB_PERMISSION.${device.deviceId}"
        val pendingIntent = PendingIntent.getBroadcast(
            activity,
            device.deviceId,
            Intent(action),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        )

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // One-shot: unregister immediately
                try {
                    context.unregisterReceiver(this)
                } catch (_: Exception) {
                }
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                Log.i(TAG, "USB permission result for device ${device.deviceId}: granted=$granted")
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= 33) {
                activity.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
            } else {
                activity.registerReceiver(receiver, IntentFilter(action))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register USB permission receiver: ${e.message}")
        }

        try {
            manager.requestPermission(device, pendingIntent)
            Log.i(TAG, "Requested USB permission for device ${device.deviceId}")
        } catch (e: SecurityException) {
            Log.e(TAG, "USB permission request failed: ${e.message}")
            // Best effort cleanup
            runCatching { activity.unregisterReceiver(receiver) }
        }
    }
}
