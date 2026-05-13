package com.gagik.terminal.render.cache

import com.gagik.terminal.render.api.TerminalRenderFrameReader
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Triple-buffered render cache publisher.
 *
 * One buffer is writer-owned (back).
 * One buffer is UI-readable (front).
 * One buffer is spare (recycled after front is replaced).
 *
 * Writer and UI never touch the same buffer simultaneously when UI consumers
 * access the front buffer through [readCurrent].
 */
class TerminalRenderPublisher(
    columns: Int,
    rows: Int,
) {
    private val buffers = Array(3) { TerminalRenderCache(columns, rows) }
    private val readerCounts = IntArray(BUFFER_COUNT)

    // Indices and reader counts are only mutated under publishLock.
    private var frontIndex = NO_FRONT
    private var nextWriteIndex = 0
    private val publishLock = ReentrantLock()
    private val bufferAvailable = publishLock.newCondition()

    // AtomicReference for lock-free front reads.
    private val frontRef = AtomicReference<TerminalRenderCache?>(null)

    /**
     * Called from render worker thread only.
     * Reads from [reader], updates back buffer, publishes as new front.
     */
    fun updateAndPublish(reader: TerminalRenderFrameReader) {
        updateAndPublish(reader, scrollbackOffset = 0)
    }

    /**
     * Called from render worker thread only.
     *
     * [scrollbackOffset] is caller-owned viewport state in lines above the live
     * bottom viewport. The source reader clamps it before rows are copied.
     */
    fun updateAndPublish(reader: TerminalRenderFrameReader, scrollbackOffset: Int) {
        val writeIndex = acquireWritableIndex()
        val back = buffers[writeIndex]

        // The selected back buffer is render-worker-exclusive here because the
        // session coalesces dirty notifications to at most one queued render
        // task, and session resize shares the same terminal mutation lock used
        // by the render frame reader.
        back.updateFrom(reader, scrollbackOffset)

        publishLock.withLock {
            frontIndex = writeIndex
            frontRef.set(buffers[frontIndex])
            bufferAvailable.signalAll()
        }
    }

    /**
     * Called from EDT only.
     * Returns the latest published snapshot. Never null after first publish.
     * The returned cache must not be retained across paint calls.
     *
     * Prefer [readCurrent] for paint code that needs to keep the returned cache
     * stable for the duration of a read.
     */
    fun current(): TerminalRenderCache? = frontRef.get()

    /**
     * Reads the latest published front buffer while preventing it from being
     * recycled as a writer-owned back buffer.
     *
     * The callback should only copy or paint from the cache and must not call
     * back into this publisher. Returning `null` means no frame has been
     * published yet.
     *
     * @param block reader invoked with the current front buffer.
     * @return [block]'s result, or `null` when no frame is available.
     */
    fun <T> readCurrent(block: (TerminalRenderCache) -> T): T? {
        val lease = acquireFrontLease() ?: return null
        try {
            return block(lease.cache)
        } finally {
            releaseFrontLease(lease.index)
        }
    }

    /**
     * Resizes all buffers atomically.
     *
     * Callers must ensure that neither the render worker nor the UI thread are
     * actively using a specific buffer during this call, or that they can
     * handle the dimension change. Typically called from the EDT or a control
     * thread.
     */
    fun resize(columns: Int, rows: Int) {
        publishLock.withLock {
            buffers.forEach {
                it.resize(columns, rows)
                it.resetOwnership()
            }
        }
    }

    private fun acquireWritableIndex(): Int {
        publishLock.withLock {
            while (true) {
                var offset = 0
                while (offset < BUFFER_COUNT) {
                    val index = (nextWriteIndex + offset) % BUFFER_COUNT
                    if (index != frontIndex && readerCounts[index] == 0) {
                        nextWriteIndex = (index + 1) % BUFFER_COUNT
                        return index
                    }
                    offset++
                }
                bufferAvailable.await()
            }
        }
    }

    private fun acquireFrontLease(): FrontLease? {
        publishLock.withLock {
            val index = frontIndex
            if (index == NO_FRONT) return null

            readerCounts[index]++
            return FrontLease(index, buffers[index])
        }
    }

    private fun releaseFrontLease(index: Int) {
        publishLock.withLock {
            readerCounts[index]--
            check(readerCounts[index] >= 0) {
                "TerminalRenderPublisher reader count underflow for buffer $index"
            }
            bufferAvailable.signalAll()
        }
    }

    private data class FrontLease(
        val index: Int,
        val cache: TerminalRenderCache,
    )

    private companion object {
        private const val BUFFER_COUNT = 3
        private const val NO_FRONT = -1
    }
}
