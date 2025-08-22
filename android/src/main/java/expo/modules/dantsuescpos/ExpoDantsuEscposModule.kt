package expo.modules.dantsuescpos

import android.annotation.SuppressLint
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnections
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.tcp.TcpConnection
import com.dantsu.escposprinter.connection.usb.UsbConnections
import com.dantsu.escposprinter.connection.usb.UsbConnection
import com.dantsu.escposprinter.connection.DeviceConnection
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import kotlin.math.max

class ExpoDantsuEscposModule : Module() {
    private var printer: EscPosPrinter? = null

    @SuppressLint("MissingPermission")
    override fun definition() = ModuleDefinition {
        Name("ExpoDantsuEscposModule")

        // Expose a JS event that fires when the underlying connection reports an unexpected disconnect
        Events("printerDisconnected")

        AsyncFunction("getBluetoothDevices") {
            val list = mutableListOf<Map<String, String>>()
            BluetoothConnections().getList()?.forEach { conn ->
                conn.device?.let { device ->
                    list.add(mapOf("name" to (device.name ?: ""), "address" to device.address))
                }
            }
            list
        }

        AsyncFunction("connectBluetooth") { address: String, dpi: Int, widthMM: Double, nbrCharactersPerLine: Int ->
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            val device = adapter.getRemoteDevice(address)
            val connection = BluetoothConnection(device).connect()
            printer = EscPosPrinter(connection, dpi, widthMM.toFloat(), nbrCharactersPerLine)
            Unit
        }

        AsyncFunction("disconnectPrinter") {
            printer?.disconnectPrinter()
            printer = null
            Unit
        }

        AsyncFunction("useEscAsteriskCommand") { enable: Boolean ->
            printer?.useEscAsteriskCommand(enable)
            Unit
        }

        AsyncFunction("printFormattedText") { text: String, mmFeedPaper: Double? ->
            val feed = mmFeedPaper?.toFloat() ?: 0f
            printer?.printFormattedText(text, feed)
            Unit
        }

        AsyncFunction("printFormattedTextAndCut") { text: String, mmFeedPaper: Double? ->
            //log text to logcat for debugging
            android.util.Log.d("ExpoDantsuEscpos", "printFormattedTextAndCut: $text")
            printer?.printFormattedTextAndCut(text)
            Unit
        }

        AsyncFunction("printFormattedTextAndOpenCashBox") { text: String, mmFeedPaper: Double? ->
            val feed = mmFeedPaper?.toFloat() ?: 0f
            printer?.printFormattedTextAndOpenCashBox(text, feed)
            Unit
        }

        AsyncFunction("getUSBDevices") {
            val list = mutableListOf<Map<String, Any>>()
            val context = appContext.reactContext ?: throw Exception("No React context available")
            UsbConnections(context).getList()?.forEach { conn ->
                val device = conn.getDevice()
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

        AsyncFunction("connectUSB") { vendorId: Int, productId: Int, dpi: Int, widthMM: Double, nbrCharactersPerLine: Int ->
            val context = appContext.reactContext ?: throw Exception("No React context available")
            val connection = UsbConnections(context).getList()
                ?.firstOrNull { it.getDevice().vendorId == vendorId && it.getDevice().productId == productId }
                ?.connect() ?: throw Exception("USB device not found")
            printer = EscPosPrinter(connection, dpi, widthMM.toFloat(), nbrCharactersPerLine)
            Unit
        }

        AsyncFunction("connectTCP") { address: String, port: Int, dpi: Int, widthMM: Double, nbrCharactersPerLine: Int ->
            val connection = TcpConnection(address, port).connect()
            printer = EscPosPrinter(connection, dpi, widthMM.toFloat(), nbrCharactersPerLine)
            Unit
        }

    }
}

