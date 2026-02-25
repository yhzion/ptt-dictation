package com.ptt.dictation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.ptt.dictation.ble.BleCentralClient
import com.ptt.dictation.service.PttForegroundService
import com.ptt.dictation.stt.SpeechRecognizerSTTEngine
import com.ptt.dictation.ui.PttScreen
import com.ptt.dictation.ui.PttViewModel

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: PttViewModel

    private val requiredPermissions: Array<String>
        get() =
            buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }.toTypedArray()

    private val requestPermissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) {
                setupUi()
            } else {
                Toast.makeText(this, "Microphone and Bluetooth permissions are required", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 연결 여부와 상관없이 화면 꺼짐 방지
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val missingPermissions =
            requiredPermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

        if (missingPermissions.isEmpty()) {
            setupUi()
        } else {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun setupUi() {
        startForegroundService(Intent(this, PttForegroundService::class.java))

        val transport = BleCentralClient(context = this, clientId = "android-${Build.MODEL}", deviceModel = Build.MODEL)
        val sttEngine = SpeechRecognizerSTTEngine(this)
        val factory = PttViewModel.Factory(transport, sttEngine)
        viewModel = ViewModelProvider(this, factory)[PttViewModel::class.java]

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val state by viewModel.state.collectAsState()
                PttScreen(
                    state = state,
                    onPttPress = viewModel::onPttPress,
                    onPttRelease = viewModel::onPttRelease,
                    onConnect = viewModel::onConnect,
                    onDisconnect = viewModel::onDisconnect,
                )
            }
        }
    }
}
