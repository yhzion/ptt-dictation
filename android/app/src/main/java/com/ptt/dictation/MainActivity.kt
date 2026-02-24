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

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                setupUi()
            } else {
                Toast.makeText(this, "마이크 권한이 필요합니다", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            setupUi()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun setupUi() {
        startForegroundService(Intent(this, PttForegroundService::class.java))

        val transport = BleCentralClient(context = this, clientId = "android-${Build.MODEL}", deviceModel = Build.MODEL)
        val sttEngine = SpeechRecognizerSTTEngine(this)
        val factory = PttViewModel.Factory(transport, sttEngine)
        viewModel = ViewModelProvider(this, factory)[PttViewModel::class.java]

        setContent {
            MaterialTheme {
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
