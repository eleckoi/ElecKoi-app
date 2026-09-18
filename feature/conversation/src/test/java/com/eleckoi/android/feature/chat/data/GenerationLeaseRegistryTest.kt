package com.eleckoi.android.feature.chat.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationLeaseRegistryTest {
    @Test
    fun cancelledLeaseCannotBeRevivedByStartingAnotherGeneration() {
        val registry = GenerationLeaseRegistry()
        val first = registry.begin("session", "run-1")

        registry.cancel("run-1")
        val second = registry.begin("session", "run-2")

        assertTrue(registry.isCancelled(first))
        assertFalse(registry.isCurrent(first))
        assertFalse(registry.isCancelled(second))
        assertTrue(registry.isCurrent(second))
    }

    @Test
    fun finishingStaleLeaseCannotClearNewGeneration() {
        val registry = GenerationLeaseRegistry()
        val first = registry.begin("session", "run-1")
        val second = registry.begin("session", "run-2")

        registry.finish(first)

        assertTrue(registry.isCurrent(second))
        assertFalse(registry.isCancelled(second))
    }

    @Test
    fun staleLeaseCannotCommitAfterNewGenerationStarts() {
        val registry = GenerationLeaseRegistry()
        val first = registry.begin("session", "run-1")
        registry.begin("session", "run-2")
        var committed = false

        val accepted = registry.commitIfOwned(first) { committed = true }

        assertFalse(accepted)
        assertFalse(committed)
    }

    @Test
    fun cancelledLeaseCanOnlyCommitItsStoppedSnapshotWhileStillOwned() {
        val registry = GenerationLeaseRegistry()
        val lease = registry.begin("session", "run-1")
        registry.cancel("run-1")

        assertFalse(registry.commitIfActive(lease) {})
        assertTrue(registry.commitIfOwned(lease) {})
    }

    @Test
    fun cancellingLeaseImmediatelyClosesItsRegisteredTransport() {
        val registry = GenerationLeaseRegistry()
        val lease = registry.begin("session", "run-1")
        var disconnected = false
        lease.invokeOnCancel { disconnected = true }

        registry.cancel("run-1")

        assertTrue(disconnected)
    }

    @Test
    fun orphanRecoveryCannotRunWhileTheSessionHasALiveLease() {
        val registry = GenerationLeaseRegistry()
        registry.begin("session", "run-1")
        var recovered = false

        assertFalse(registry.runIfSessionInactive("session") { recovered = true })
        assertFalse(recovered)

        registry.cancel("run-1")

        assertTrue(registry.runIfSessionInactive("session") { recovered = true })
        assertTrue(recovered)
    }

    @Test
    fun lateStopForOldRunCannotCancelReplacementRun() {
        val registry = GenerationLeaseRegistry()
        val first = registry.begin("session", "run-1")
        val second = registry.begin("session", "run-2")

        assertFalse(registry.cancel("run-1"))
        assertTrue(registry.isCancelled(first))
        assertFalse(registry.isCancelled(second))
        assertTrue(registry.isCurrent(second))
    }
}
