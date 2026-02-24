package com.ptt.dictation.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.ptt.dictation.model.PttMessage
import com.ptt.dictation.ws.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("MissingPermission")
class BleCentralClient(
    private val context: Context,
    private val clientId: String,
    private val deviceModel: String,
) : PttTransport {
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var listener: PttTransportListener? = null

    private var controlChar: BluetoothGattCharacteristic? = null
    private var partialTextChar: BluetoothGattCharacteristic? = null
    private var finalTextChar: BluetoothGattCharacteristic? = null
    private var deviceInfoChar: BluetoothGattCharacteristic? = null

    override fun setListener(listener: PttTransportListener) {
        this.listener = listener
    }

    override fun startScanning() {
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
        val filter =
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BLEConstants.SERVICE_UUID))
                .build()
        val settings =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    override fun connect(deviceId: String) {
        _connectionState.value = ConnectionState.CONNECTING
        val device = bluetoothAdapter.getRemoteDevice(deviceId)
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    override fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override fun send(message: PttMessage) {
        val json = BleMessageEncoder.encode(message)
        val data = json.toByteArray(Charsets.UTF_8)

        val characteristic =
            when (message.type) {
                "PTT_START" -> controlChar
                "PARTIAL" -> partialTextChar
                "FINAL" -> finalTextChar
                "HELLO" -> deviceInfoChar
                else -> null
            } ?: return

        val writeType =
            if (message.type == "PARTIAL") {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

        val gatt = bluetoothGatt ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, data, writeType)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = data
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun sendHello() {
        send(PttMessage.hello(clientId, deviceModel, "Google"))
    }

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
                bluetoothAdapter.bluetoothLeScanner?.stopScan(this)
                connect(result.device.address)
            }
        }

    private val gattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        _connectionState.value = ConnectionState.DISCONNECTED
                        listener?.onDisconnected()
                    }
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) return

                val service = gatt.getService(BLEConstants.SERVICE_UUID) ?: return
                controlChar = service.getCharacteristic(BLEConstants.CONTROL_CHAR_UUID)
                partialTextChar = service.getCharacteristic(BLEConstants.PARTIAL_TEXT_CHAR_UUID)
                finalTextChar = service.getCharacteristic(BLEConstants.FINAL_TEXT_CHAR_UUID)
                deviceInfoChar = service.getCharacteristic(BLEConstants.DEVICE_INFO_CHAR_UUID)

                _connectionState.value = ConnectionState.CONNECTED
                listener?.onConnected()
                sendHello()
            }
        }
}
