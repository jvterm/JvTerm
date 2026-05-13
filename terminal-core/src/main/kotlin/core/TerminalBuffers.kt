package com.gagik.core

import com.gagik.core.api.TerminalBufferApi
import com.gagik.core.buffer.TerminalBuffer

/**
 * Factory for creating terminal buffer instances behind the public core API.
 */
object TerminalBuffers {
    /**
     * Creates a terminal buffer with the requested visible dimensions and
     * scrollback capacity.
     */
    fun create(width: Int, height: Int, maxHistory: Int = 1000): TerminalBufferApi {
        return TerminalBuffer(width, height, maxHistory)
    }
}