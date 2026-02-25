@file:Suppress("ktlint:standard:function-naming")

package com.ptt.dictation.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ptt.dictation.ble.ConnectionState

/** OLED-optimized grayscale palette matching wireframe spec */
private object OledColors {
    val background = Color.Black
    val divider = Color(0xFF1A1A1A)
    val surfaceBorder = Color(0xFF222222)
    val surfaceBorderActive = Color(0xFF777777)
    val textHint = Color(0xFF444444)
    val textMuted = Color(0xFF555555)
    val textSecondary = Color(0xFF666666)
    val textPrimary = Color(0xFF888888)
    val textBright = Color(0xFFAAAAAA)
    val textHighlight = Color(0xFFCCCCCC)
}

private const val PIXEL_SHIFT_CYCLE_MS = 720_000 // 12 minutes
private const val PIXEL_SHIFT_RANGE_DP = 13f

@Composable
fun PttScreen(
    state: PttUiState,
    onPttPress: () -> Unit,
    onPttRelease: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    var isUpsideDown by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

        val sensorEventListener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // y-axis gravity value
                    val y = event.values[1]
                    isUpsideDown = y < -4.0f
                }

                override fun onAccuracyChanged(
                    sensor: Sensor?,
                    accuracy: Int,
                ) {}
            }
        sensorManager.registerListener(
            sensorEventListener,
            gravitySensor,
            SensorManager.SENSOR_DELAY_UI,
        )
        onDispose {
            sensorManager.unregisterListener(sensorEventListener)
        }
    }

    val rotationAngle by animateFloatAsState(targetValue = if (isUpsideDown) 180f else 0f, label = "rotation")

    val infiniteTransition = rememberInfiniteTransition(label = "pixelShift")
    val shiftX by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(PIXEL_SHIFT_CYCLE_MS / 2, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "shiftX",
    )
    val shiftY by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = -1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(PIXEL_SHIFT_CYCLE_MS / 3, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "shiftY",
    )

    // Outer box: black background always fills screen (no gap during pixel shift)
    Box(
        modifier = Modifier.fillMaxSize().background(OledColors.background).rotate(rotationAngle),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .offset(
                        x = (shiftX * PIXEL_SHIFT_RANGE_DP / 2).dp,
                        y = (shiftY * PIXEL_SHIFT_RANGE_DP / 2).dp,
                    ),
        ) {
            // Header
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clickable {
                            if (state.connectionState == ConnectionState.CONNECTED) {
                                onDisconnect()
                            } else {
                                onConnect()
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        when (state.connectionState) {
                            ConnectionState.CONNECTED -> "CONNECTED"
                            ConnectionState.CONNECTING -> "CONNECTING..."
                            ConnectionState.DISCONNECTED -> "DISCONNECTED"
                        },
                    color =
                        when (state.connectionState) {
                            ConnectionState.CONNECTED -> OledColors.textBright
                            else -> OledColors.textSecondary
                        },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp,
                    modifier = Modifier.testTag("connection-status"),
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(OledColors.divider),
            )

            when (state.connectionState) {
                ConnectionState.CONNECTED -> {
                    ConnectedContent(
                        state = state,
                        onPttPress = onPttPress,
                        onPttRelease = onPttRelease,
                    )
                }
                else -> {
                    DisconnectedContent(
                        connectionState = state.connectionState,
                    )
                }
            }
        }
    }
}

@Composable
private fun DisconnectedContent(connectionState: ConnectionState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text =
                if (connectionState == ConnectionState.CONNECTING) {
                    "Searching nearby desktop..."
                } else {
                    "Auto reconnect enabled"
                },
            color = OledColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 0.5.sp,
            modifier = Modifier.testTag("auto-reconnect-hint"),
        )
    }
}

@Composable
private fun ConnectedContent(
    state: PttUiState,
    onPttPress: () -> Unit,
    onPttRelease: () -> Unit,
) {
    val view = LocalView.current
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.dp,
                        color =
                            if (state.isPttPressed) {
                                OledColors.surfaceBorderActive
                            } else {
                                OledColors.surfaceBorder
                            },
                        shape = RoundedCornerShape(24.dp),
                    )
                    .testTag("ptt-button")
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.pressed && !change.previousPressed) {
                                    change.consume()
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    onPttPress()
                                } else if (!change.pressed && change.previousPressed) {
                                    change.consume()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.VIRTUAL_KEY_RELEASE,
                                        )
                                    }
                                    onPttRelease()
                                }
                            }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            // Recording indicator pill
            if (state.isPttPressed) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 24.dp)
                            .border(1.dp, OledColors.textMuted, RoundedCornerShape(100.dp))
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "LISTENING",
                        color = OledColors.textBright,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 2.sp,
                        modifier = Modifier.testTag("ptt-label"),
                    )
                }
            }

            // Dictation text
            if (state.partialText.isNotEmpty()) {
                Text(
                    text = state.partialText,
                    color = OledColors.textHighlight,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 36.sp,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .padding(24.dp)
                            .testTag("partial-text"),
                )
            }

            // Idle hint
            if (!state.isPttPressed) {
                Text(
                    text = "Hold to speak",
                    color = OledColors.textHint,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                            .testTag("ptt-label"),
                )
            }
        }
    }
}
