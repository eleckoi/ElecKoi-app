package com.eleckoi.android.feature.chat.data

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns the lifetime of model generations.
 *
 * Cancellation belongs to one immutable lease. Starting a later request can never reset the
 * cancelled state observed by an older blocked HTTP connection.
 */
internal class GenerationLeaseRegistry {
    private val active = AtomicReference<Lease?>(null)
    private val mutationLock = Any()

    fun begin(sessionId: String, runId: String): Lease {
        require(runId.isNotBlank()) { "Generation run id cannot be blank" }
        return synchronized(mutationLock) {
            val next = Lease(
                id = runId,
                sessionId = sessionId,
            )
            active.getAndSet(next)?.cancel()
            next
        }
    }

    fun cancel(runId: String): Boolean {
        return synchronized(mutationLock) {
            val current = active.get()?.takeIf { it.id == runId } ?: return@synchronized false
            current.cancel()
            true
        }
    }

    /** Runs recovery without racing a new lease for the same conversation. */
    fun runIfSessionInactive(sessionId: String, action: () -> Unit): Boolean {
        return synchronized(mutationLock) {
            val current = active.get()
            if (current?.sessionId == sessionId && !current.cancelled.get()) {
                false
            } else {
                action()
                true
            }
        }
    }

    fun isCurrent(lease: Lease): Boolean = active.get() === lease

    fun isCancelled(lease: Lease): Boolean = lease.cancelled.get() || !isCurrent(lease)

    fun finish(lease: Lease) {
        synchronized(mutationLock) {
            lease.clearCancellationAction()
            active.compareAndSet(lease, null)
        }
    }

    /** Runs a persistent commit atomically against cancellation and the start of a newer lease. */
    fun commitIfActive(lease: Lease, action: () -> Unit): Boolean {
        return synchronized(mutationLock) {
            if (active.get() !== lease || lease.cancelled.get()) return@synchronized false
            action()
            true
        }
    }

    /** A cancelled lease may persist its partial result only until a newer lease replaces it. */
    fun commitIfOwned(lease: Lease, action: () -> Unit): Boolean {
        return synchronized(mutationLock) {
            if (active.get() !== lease) return@synchronized false
            action()
            true
        }
    }

    internal class Lease internal constructor(
        val id: String,
        val sessionId: String,
    ) {
        internal val cancelled = AtomicBoolean(false)
        private val cancellationAction = AtomicReference<(() -> Unit)?>(null)

        internal fun cancel() {
            cancelled.set(true)
            cancellationAction.getAndSet(null)?.let { action -> runCatching(action) }
        }

        fun invokeOnCancel(action: () -> Unit) {
            if (cancelled.get()) {
                runCatching(action)
                return
            }
            cancellationAction.set(action)
            if (cancelled.get() && cancellationAction.compareAndSet(action, null)) runCatching(action)
        }

        internal fun clearCancellationAction() {
            cancellationAction.set(null)
        }
    }
}
