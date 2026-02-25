package com.ptt.dictation.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.ptt.dictation.model.PttMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

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

    private data class PendingWrite(
        val messageType: String,
        val characteristicUuid: UUID,
        val data: ByteArray,
        val writeType: Int,
        val attempts: Int = 0,
    )

    private val writeQueue = ArrayDeque<PendingWrite>()
    private var writeInFlight = false
    private var inflightWrite: PendingWrite? = null
    private var negotiatedMtu = DEFAULT_MTU
    private var waitingForMtu = false

    private val handler = Handler(Looper.getMainLooper())
    private val scanTimeoutRunnable: Runnable =
        Runnable {
            bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
            if (_connectionState.value != ConnectionState.CONNECTED) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    private val drainQueueRunnable = Runnable { drainWriteQueue() }

    override fun setListener(listener: PttTransportListener) {
        this.listener = listener
    }

    override fun startScanning() {
        // Clean up stale GATT/write state from previous session.
        clearWriteState()
        bluetoothGatt?.close()
        bluetoothGatt = null
        negotiatedMtu = DEFAULT_MTU
        waitingForMtu = false

        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
        val filter =
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BLEConstants.SERVICE_UUID))
                .build()
        val settings =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
        handler.removeCallbacks(scanTimeoutRunnable)
        scanner.startScan(listOf(filter), settings, scanCallback)
        handler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS)
    }

    override fun connect(deviceId: String) {
        _connectionState.value = ConnectionState.CONNECTING
        val device = bluetoothAdapter.getRemoteDevice(deviceId)
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    override fun disconnect() {
        handler.removeCallbacks(scanTimeoutRunnable)
        handler.removeCallbacks(drainQueueRunnable)
        clearWriteState()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        negotiatedMtu = DEFAULT_MTU
        waitingForMtu = false
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override fun send(message: PttMessage) {
        if (_connectionState.value != ConnectionState.CONNECTED) return

        val json = BleMessageEncoder.encode(message)
        val data = json.toByteArray(Charsets.UTF_8)

        val characteristicUuid =
            when (message.type) {
                "PTT_START", "PTT_END" -> BLEConstants.CONTROL_CHAR_UUID
                "PARTIAL" -> BLEConstants.PARTIAL_TEXT_CHAR_UUID
                "FINAL" -> BLEConstants.FINAL_TEXT_CHAR_UUID
                "HELLO" -> BLEConstants.DEVICE_INFO_CHAR_UUID
                else -> return
            }

        val writeType =
            if (message.type == "PARTIAL") {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

        enqueueWrite(
            PendingWrite(
                messageType = message.type,
                characteristicUuid = characteristicUuid,
                data = data,
                writeType = writeType,
            ),
        )
    }

    private fun sendHello() {
        send(PttMessage.hello(clientId, deviceModel, "Google"))
    }

    private fun maxPayloadBytes(): Int = (negotiatedMtu - ATT_HEADER_BYTES).coerceAtLeast(20)

    @Synchronized
    private fun enqueueWrite(write: PendingWrite) {
        if (write.messageType == "PARTIAL") {
            // Keep only the latest partial to avoid starving FINAL/control writes.
            val removed = writeQueue.removeAll { it.messageType == "PARTIAL" }
            if (removed) {
                Log.d(TAG, "Dropped stale PARTIAL writes while queueing latest partial")
            }
        }
        writeQueue.addLast(write)
        drainWriteQueue()
    }

    @Synchronized
    private fun drainWriteQueue() {
        if (writeInFlight || _connectionState.value != ConnectionState.CONNECTED) return

        val gatt = bluetoothGatt ?: return
        val pending = writeQueue.removeFirstOrNull() ?: return
        val characteristic = resolveCharacteristic(pending.characteristicUuid)
        if (characteristic == null) {
            Log.w(TAG, "Characteristic missing for ${pending.messageType}, dropping")
            handler.post(drainQueueRunnable)
            return
        }

        val maxBytes = maxPayloadBytes()
        if (pending.data.size > maxBytes) {
            val msg =
                "Payload too large (${pending.data.size} > $maxBytes) for ${pending.messageType}; dropping"
            Log.w(TAG, msg)
            listener?.onError(msg)
            handler.post(drainQueueRunnable)
            return
        }

        val writeAccepted = writeCharacteristic(gatt, characteristic, pending)
        if (!writeAccepted) {
            retryOrDrop(
                pending,
                reason = "Gatt busy/failed write dispatch",
            )
            return
        }

        if (pending.writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            handler.postDelayed(drainQueueRunnable, NO_RESPONSE_WRITE_INTERVAL_MS)
        } else {
            writeInFlight = true
            inflightWrite = pending
        }
    }

    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        pending: PendingWrite,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val resultCode =
                gatt.writeCharacteristic(
                    characteristic,
                    pending.data,
                    pending.writeType,
                )
            resultCode == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = pending.data
            characteristic.writeType = pending.writeType
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun resolveCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
        return when (uuid) {
            BLEConstants.CONTROL_CHAR_UUID -> controlChar
            BLEConstants.PARTIAL_TEXT_CHAR_UUID -> partialTextChar
            BLEConstants.FINAL_TEXT_CHAR_UUID -> finalTextChar
            BLEConstants.DEVICE_INFO_CHAR_UUID -> deviceInfoChar
            else -> null
        }
    }

    @Synchronized
    private fun retryOrDrop(
        pending: PendingWrite,
        reason: String,
    ) {
        if (pending.attempts >= MAX_WRITE_RETRY) {
            val msg = "Dropping ${pending.messageType} after retries: $reason"
            Log.w(TAG, msg)
            if (pending.messageType != "PARTIAL") {
                listener?.onError(msg)
            }
            handler.post(drainQueueRunnable)
            return
        }
        writeQueue.addFirst(pending.copy(attempts = pending.attempts + 1))
        handler.postDelayed(drainQueueRunnable, WRITE_RETRY_DELAY_MS)
    }

    @Synchronized
    private fun clearWriteState() {
        writeQueue.clear()
        writeInFlight = false
        inflightWrite = null
    }

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
                handler.removeCallbacks(scanTimeoutRunnable)
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
                        clearWriteState()
                        negotiatedMtu = DEFAULT_MTU
                        waitingForMtu = false
                        @Suppress("DEPRECATION")
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            waitingForMtu = gatt.requestMtu(TARGET_MTU)
                        }
                        if (!waitingForMtu) {
                            gatt.discoverServices()
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        clearWriteState()
                        bluetoothGatt?.close()
                        bluetoothGatt = null
                        controlChar = null
                        partialTextChar = null
                        finalTextChar = null
                        deviceInfoChar = null
                        negotiatedMtu = DEFAULT_MTU
                        waitingForMtu = false
                        _connectionState.value = ConnectionState.DISCONNECTED
                        listener?.onDisconnected()
                    }
                }
            }

            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    negotiatedMtu = mtu
                    Log.d(TAG, "MTU changed to $mtu")
                } else {
                    negotiatedMtu = DEFAULT_MTU
                    Log.w(TAG, "MTU change failed with status=$status, using default")
                }
                waitingForMtu = false
                gatt.discoverServices()
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

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                val completed = inflightWrite
                writeInFlight = false
                inflightWrite = null

                if (completed != null && status != BluetoothGatt.GATT_SUCCESS) {
                    retryOrDrop(
                        completed,
                        reason = "onCharacteristicWrite status=$status (${completed.messageType})",
                    )
                    return
                }
                drainWriteQueue()
            }
        }

    companion object {
        private const val TAG = "BleCentralClient"
        private const val SCAN_TIMEOUT_MS = 10_000L
        private const val TARGET_MTU = 512
        private const val DEFAULT_MTU = 23
        private const val ATT_HEADER_BYTES = 3
        private const val MAX_WRITE_RETRY = 3
        private const val WRITE_RETRY_DELAY_MS = 60L
        private const val NO_RESPONSE_WRITE_INTERVAL_MS = 20L
    }
}
