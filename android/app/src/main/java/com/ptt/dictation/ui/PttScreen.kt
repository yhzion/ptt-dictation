package com.ptt.dictation.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ptt.dictation.ble.ConnectionState

@Suppress("ktlint:standard:function-naming")
@Composable
fun PttScreen(
    state: PttUiState,
    onPttPress: () -> Unit,
    onPttRelease: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Connection status
        Text(
            text =
                when (state.connectionState) {
                    ConnectionState.CONNECTED -> "연결됨"
                    ConnectionState.CONNECTING -> "연결 중..."
                    ConnectionState.DISCONNECTED -> "연결 안 됨"
                },
            modifier = Modifier.testTag("connection-status"),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Connect/Disconnect button
        Button(
            onClick = {
                if (state.connectionState == ConnectionState.CONNECTED) {
                    onDisconnect()
                } else {
                    onConnect()
                }
            },
            modifier = Modifier.testTag("connect-button"),
        ) {
            Text(
                if (state.connectionState == ConnectionState.CONNECTED) "연결 해제" else "스캔",
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Partial text display
        if (state.partialText.isNotEmpty()) {
            Text(
                text = state.partialText,
                modifier = Modifier.testTag("partial-text"),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // PTT Button
        Box(
            modifier =
                Modifier
                    .size(120.dp)
                    .testTag("ptt-button")
                    .pointerInput(state.canPtt) {
                        if (state.canPtt) {
                            detectTapGestures(
                                onPress = {
                                    onPttPress()
                                    tryAwaitRelease()
                                    onPttRelease()
                                },
                            )
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color =
                    when {
                        state.isPttPressed -> MaterialTheme.colorScheme.primary
                        state.canPtt -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.isPttPressed) "듣는 중..." else "PTT",
                        modifier = Modifier.testTag("ptt-label"),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
