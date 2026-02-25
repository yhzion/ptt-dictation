package com.ptt.dictation.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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
    private var statusChar: BluetoothGattCharacteristic? = null

    private var preferredDeviceAddress: String? = null
    private var scanInProgress = false
    private var connectAttemptInProgress = false
    private var manualDisconnectRequested = false

    private var negotiatedMtu = DEFAULT_MTU
    private var waitingForMtu = false

    private var keepaliveConsecutiveFailures = 0
    private var keepaliveAwaitingResponse = false

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

    private val handler = Handler(Looper.getMainLooper())

    private val scanTimeoutRunnable =
        Runnable {
            stopScanIfNeeded()
            if (_connectionState.value != ConnectionState.CONNECTED) {
                setDisconnected(notifyListener = false)
            }
        }

    private val connectTimeoutRunnable =
        Runnable {
            if (!connectAttemptInProgress || _connectionState.value == ConnectionState.CONNECTED) {
                return@Runnable
            }
            Log.w(TAG, "Connect timeout; falling back to scan")
            connectAttemptInProgress = false
            bluetoothGatt?.disconnect()
            clearGattState(closeGatt = true)
            startScanInternal()
        }

    private val keepaliveRunnable = Runnable { performKeepaliveCheck() }
    private val drainQueueRunnable = Runnable { drainWriteQueue() }

    override fun setListener(listener: PttTransportListener) {
        this.listener = listener
    }

    override fun startScanning() {
        manualDisconnectRequested = false
        stopKeepalive()
        clearWriteState()
        stopScanIfNeeded()

        if (attemptDirectReconnect()) {
            return
        }
        startScanInternal()
    }

    override fun connect(deviceId: String) {
        manualDisconnectRequested = false
        if (!connectToDevice(deviceId)) {
            setDisconnected(notifyListener = false)
            listener?.onError("Failed to connect target device: $deviceId")
        }
    }

    override fun disconnect() {
        manualDisconnectRequested = true
        handler.removeCallbacks(connectTimeoutRunnable)
        stopScanIfNeeded()
        stopKeepalive()
        clearWriteState()
        bluetoothGatt?.disconnect()
        clearGattState(closeGatt = true)
        connectAttemptInProgress = false
        setDisconnected(notifyListener = false)
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

    private fun attemptDirectReconnect(): Boolean {
        val address = preferredDeviceAddress ?: return false
        return connectToDevice(address)
    }

    private fun connectToDevice(deviceId: String): Boolean {
        val device =
            try {
                bluetoothAdapter.getRemoteDevice(deviceId)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid bluetooth address: $deviceId", e)
                null
            }

        if (device == null) {
            return false
        }

        handler.removeCallbacks(scanTimeoutRunnable)
        handler.removeCallbacks(connectTimeoutRunnable)
        stopScanIfNeeded()
        clearGattState(closeGatt = true)

        _connectionState.value = ConnectionState.CONNECTING
        connectAttemptInProgress = true
        preferredDeviceAddress = deviceId
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        handler.postDelayed(connectTimeoutRunnable, CONNECT_TIMEOUT_MS)
        return true
    }

    private fun startScanInternal() {
        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            setDisconnected(notifyListener = false)
            listener?.onError("Bluetooth LE scanner unavailable")
            Log.w(TAG, "Cannot start scan: Bluetooth LE scanner unavailable")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        val filter =
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BLEConstants.SERVICE_UUID))
                .build()
        val settings =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

        handler.removeCallbacks(scanTimeoutRunnable)
        try {
            scanner.startScan(listOf(filter), settings, scanCallback)
            scanInProgress = true
        } catch (e: SecurityException) {
            setDisconnected(notifyListener = false)
            listener?.onError("BLE scan permission denied")
            Log.w(TAG, "Cannot start scan: missing permission", e)
            return
        } catch (e: IllegalStateException) {
            setDisconnected(notifyListener = false)
            listener?.onError("Bluetooth adapter is not ready")
            Log.w(TAG, "Cannot start scan: adapter state invalid", e)
            return
        }

        handler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS)
    }

    private fun stopScanIfNeeded() {
        handler.removeCallbacks(scanTimeoutRunnable)
        if (!scanInProgress) return
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
        scanInProgress = false
    }

    private fun sendHello() {
        send(PttMessage.hello(clientId, deviceModel, "Google"))
    }

    private fun enableStatusNotifications(gatt: BluetoothGatt) {
        val status = statusChar ?: return

        val notificationEnabled = gatt.setCharacteristicNotification(status, true)
        if (!notificationEnabled) {
            Log.w(TAG, "Failed to enable local notification state for status characteristic")
            return
        }

        val descriptor = status.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
        if (descriptor == null) {
            Log.w(TAG, "CCCD descriptor missing on status characteristic")
            return
        }

        val writeAccepted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }

        if (!writeAccepted) {
            Log.w(TAG, "Failed to write CCCD for status notifications")
        }
    }

    private fun startKeepalive() {
        keepaliveConsecutiveFailures = 0
        keepaliveAwaitingResponse = false
        handler.removeCallbacks(keepaliveRunnable)
        handler.postDelayed(keepaliveRunnable, KEEPALIVE_INTERVAL_MS)
    }

    private fun stopKeepalive() {
        handler.removeCallbacks(keepaliveRunnable)
        keepaliveConsecutiveFailures = 0
        keepaliveAwaitingResponse = false
    }

    private fun performKeepaliveCheck() {
        if (_connectionState.value != ConnectionState.CONNECTED) return

        val gatt = bluetoothGatt
        if (gatt == null) {
            registerKeepaliveFailure("gatt missing")
            scheduleNextKeepaliveIfConnected()
            return
        }

        if (keepaliveAwaitingResponse) {
            registerKeepaliveFailure("previous keepalive timed out")
            if (_connectionState.value != ConnectionState.CONNECTED) return
        }

        val started =
            try {
                gatt.readRemoteRssi()
            } catch (e: Exception) {
                Log.w(TAG, "keepalive readRemoteRssi error", e)
                false
            }

        if (started) {
            keepaliveAwaitingResponse = true
        } else {
            registerKeepaliveFailure("readRemoteRssi dispatch failed")
            if (_connectionState.value != ConnectionState.CONNECTED) return
        }

        scheduleNextKeepaliveIfConnected()
    }

    private fun scheduleNextKeepaliveIfConnected() {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        handler.postDelayed(keepaliveRunnable, KEEPALIVE_INTERVAL_MS)
    }

    private fun registerKeepaliveFailure(reason: String) {
        keepaliveAwaitingResponse = false
        keepaliveConsecutiveFailures++
        Log.w(
            TAG,
            "Keepalive failure $keepaliveConsecutiveFailures/$KEEPALIVE_FAILURE_THRESHOLD: $reason",
        )

        if (keepaliveConsecutiveFailures >= KEEPALIVE_FAILURE_THRESHOLD) {
            forceDisconnectDueToHealth(reason)
        }
    }

    private fun forceDisconnectDueToHealth(reason: String) {
        Log.w(TAG, "Force disconnect due to keepalive health check: $reason")
        manualDisconnectRequested = false
        handler.removeCallbacks(connectTimeoutRunnable)
        stopScanIfNeeded()
        stopKeepalive()
        clearWriteState()
        bluetoothGatt?.disconnect()
        clearGattState(closeGatt = true)
        connectAttemptInProgress = false
        setDisconnected(notifyListener = true)
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

    private fun clearGattState(closeGatt: Boolean) {
        if (closeGatt) {
            bluetoothGatt?.close()
        }
        bluetoothGatt = null
        controlChar = null
        partialTextChar = null
        finalTextChar = null
        deviceInfoChar = null
        statusChar = null
        negotiatedMtu = DEFAULT_MTU
        waitingForMtu = false
    }

    private fun setDisconnected(notifyListener: Boolean) {
        val wasDisconnected = _connectionState.value == ConnectionState.DISCONNECTED
        _connectionState.value = ConnectionState.DISCONNECTED
        if (notifyListener && !wasDisconnected) {
            listener?.onDisconnected()
        }
    }

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
                stopScanIfNeeded()
                connectToDevice(result.device.address)
            }

            override fun onScanFailed(errorCode: Int) {
                scanInProgress = false
                setDisconnected(notifyListener = false)
                listener?.onError("BLE scan failed: $errorCode")
                Log.w(TAG, "BLE scan failed with code=$errorCode")
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
                        connectAttemptInProgress = false
                        manualDisconnectRequested = false
                        handler.removeCallbacks(connectTimeoutRunnable)
                        stopScanIfNeeded()

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
                        val wasManual = manualDisconnectRequested
                        val hadConnectAttempt = connectAttemptInProgress

                        connectAttemptInProgress = false
                        manualDisconnectRequested = false
                        handler.removeCallbacks(connectTimeoutRunnable)

                        stopKeepalive()
                        clearWriteState()
                        clearGattState(closeGatt = true)

                        if (hadConnectAttempt && !wasManual) {
                            startScanInternal()
                            return
                        }

                        setDisconnected(notifyListener = !wasManual)
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
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    forceDisconnectDueToHealth("service discovery failed status=$status")
                    return
                }

                val service = gatt.getService(BLEConstants.SERVICE_UUID)
                if (service == null) {
                    forceDisconnectDueToHealth("service missing")
                    return
                }

                controlChar = service.getCharacteristic(BLEConstants.CONTROL_CHAR_UUID)
                partialTextChar = service.getCharacteristic(BLEConstants.PARTIAL_TEXT_CHAR_UUID)
                finalTextChar = service.getCharacteristic(BLEConstants.FINAL_TEXT_CHAR_UUID)
                deviceInfoChar = service.getCharacteristic(BLEConstants.DEVICE_INFO_CHAR_UUID)
                statusChar = service.getCharacteristic(BLEConstants.STATUS_CHAR_UUID)

                enableStatusNotifications(gatt)

                _connectionState.value = ConnectionState.CONNECTED
                listener?.onConnected()
                sendHello()
                startKeepalive()
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                if (descriptor.characteristic.uuid == BLEConstants.STATUS_CHAR_UUID &&
                    status != BluetoothGatt.GATT_SUCCESS
                ) {
                    Log.w(TAG, "Status notification descriptor write failed: status=$status")
                }
            }

            override fun onReadRemoteRssi(
                gatt: BluetoothGatt,
                rssi: Int,
                status: Int,
            ) {
                keepaliveAwaitingResponse = false
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    keepaliveConsecutiveFailures = 0
                } else {
                    registerKeepaliveFailure("readRemoteRssi status=$status")
                }
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
        private const val CONNECT_TIMEOUT_MS = 8_000L

        private const val KEEPALIVE_INTERVAL_MS = 12_000L
        private const val KEEPALIVE_FAILURE_THRESHOLD = 3

        private const val TARGET_MTU = 512
        private const val DEFAULT_MTU = 23
        private const val ATT_HEADER_BYTES = 3

        private const val MAX_WRITE_RETRY = 3
        private const val WRITE_RETRY_DELAY_MS = 30L
        private const val NO_RESPONSE_WRITE_INTERVAL_MS = 10L

        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
