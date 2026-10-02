package com.eleckoi.android.sdk.author.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthorBridgeRequestGateTest {
    @Test
    fun `rejects oversized utf8 requests`() {
        val gate = AuthorBridgeRequestGate(maxRequestBytes = 4)

        assertEquals(
            AuthorBridgeRequestRejection.RequestTooLarge,
            gate.tryAcquire("你好"),
        )
    }

    @Test
    fun `bounds concurrent requests and allows another after release`() {
        val gate = AuthorBridgeRequestGate(maxInFlight = 1)

        assertNull(gate.tryAcquire("{}"))
        assertEquals(AuthorBridgeRequestRejection.TooManyInFlight, gate.tryAcquire("{}"))
        gate.release()
        assertNull(gate.tryAcquire("{}"))
    }

    @Test
    fun `fast sequential local requests are not rate limited`() {
        val gate = AuthorBridgeRequestGate()
        repeat(1000) {
            assertNull(gate.tryAcquire("{}"))
            gate.release()
        }
    }
}
