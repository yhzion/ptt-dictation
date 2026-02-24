package com.ptt.dictation.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ptt.dictation.ble.ConnectionState
import org.junit.Rule
import org.junit.Test

class PttScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun render(state: PttUiState = PttUiState()) {
        composeTestRule.setContent {
            PttScreen(
                state = state,
                onPttPress = {},
                onPttRelease = {},
                onConnect = {},
                onDisconnect = {},
            )
        }
    }

    @Test
    fun showsDisconnectedStatus() {
        render()
        composeTestRule.onNodeWithTag("connection-status")
            .assertTextEquals("DISCONNECTED")
    }

    @Test
    fun showsConnectedStatus() {
        render(PttUiState(connectionState = ConnectionState.CONNECTED))
        composeTestRule.onNodeWithTag("connection-status")
            .assertTextEquals("CONNECTED")
    }

    @Test
    fun showsScanButtonWhenDisconnected() {
        render()
        composeTestRule.onNodeWithTag("connect-button").assertExists()
    }

    @Test
    fun showsPttButtonWhenConnected() {
        render(PttUiState(connectionState = ConnectionState.CONNECTED))
        composeTestRule.onNodeWithTag("ptt-button").assertExists()
    }

    @Test
    fun showsPartialTextWhenListening() {
        render(
            PttUiState(
                connectionState = ConnectionState.CONNECTED,
                isPttPressed = true,
                partialText = "테스트 텍스트",
            ),
        )
        composeTestRule.onNodeWithTag("partial-text")
            .assertTextEquals("테스트 텍스트")
    }

    @Test
    fun showsPttHintWhenConnectedIdle() {
        render(PttUiState(connectionState = ConnectionState.CONNECTED))
        composeTestRule.onNodeWithTag("ptt-label")
            .assertTextEquals("누르고 말씀하세요")
    }
}
