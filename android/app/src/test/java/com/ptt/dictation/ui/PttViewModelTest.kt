package com.ptt.dictation.ui

import com.ptt.dictation.ws.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PttViewModelTest {
    @Test
    fun `initial state is idle`() {
        val state = PttUiState()
        assertEquals(PttMode.IDLE, state.mode)
        assertFalse(state.isPttPressed)
        assertEquals(ConnectionState.DISCONNECTED, state.connectionState)
    }

    @Test
    fun `pressing PTT changes mode to LISTENING`() {
        val state =
            PttUiState().copy(
                connectionState = ConnectionState.CONNECTED,
                isPttPressed = true,
                mode = PttMode.LISTENING,
            )
        assertTrue(state.isPttPressed)
        assertEquals(PttMode.LISTENING, state.mode)
    }

    @Test
    fun `can only PTT when connected`() {
        val state = PttUiState(connectionState = ConnectionState.DISCONNECTED)
        assertFalse(state.canPtt)
    }

    @Test
    fun `can PTT when connected`() {
        val state = PttUiState(connectionState = ConnectionState.CONNECTED)
        assertTrue(state.canPtt)
    }

    @Test
    fun `server address format`() {
        val state = PttUiState(serverHost = "192.168.1.10", serverPort = 9876)
        assertEquals("ws://192.168.1.10:9876", state.wsUrl)
    }
}
