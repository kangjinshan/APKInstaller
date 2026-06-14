package com.apkinstaller.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SocketTransferManagerTest {

    @Test
    fun supportedTransferPortsPreferCurrentThenLegacyPort() {
        assertEquals(
            listOf(9999, 8888),
            SocketTransferManager.SUPPORTED_TRANSFER_PORTS
        )
    }
}
