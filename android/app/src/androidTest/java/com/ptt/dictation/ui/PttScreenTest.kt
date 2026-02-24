package com.ptt.dictation.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ptt.dictation.ws.ConnectionState
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
        composeTestRule.onNodeWithTag("connection-status").assertTextEquals("연결 안 됨")
    }

    @Test
    fun showsConnectedStatus() {
        render(PttUiState(connectionState = ConnectionState.CONNECTED))
        composeTestRule.onNodeWithTag("connection-status").assertTextEquals("연결됨")
    }

    @Test
    fun showsPttButton() {
        render()
        composeTestRule.onNodeWithTag("ptt-button").assertExists()
    }

    @Test
    fun showsPartialTextWhenPresent() {
        render(PttUiState(partialText = "테스트 텍스트"))
        composeTestRule.onNodeWithTag("partial-text").assertTextEquals("테스트 텍스트")
    }
}
