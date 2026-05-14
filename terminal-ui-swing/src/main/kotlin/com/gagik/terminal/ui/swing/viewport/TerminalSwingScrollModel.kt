package com.gagik.terminal.ui.swing.viewport

import kotlin.math.floor

/**
 * EDT-confined scrollback viewport model for Swing wheel input.
 *
 * Wheel devices can report fractional line deltas. The render/session contract
 * is still line-addressed, so this model preserves fractional accumulation and
 * exposes the committed integer line offset whenever a row boundary is crossed.
 */
internal class TerminalSwingScrollModel {
    private var preciseOffset: Double = 0.0
    private var committedOffset: Int = 0

    /**
     * Current committed scrollback offset in whole terminal rows.
     */
    val offset: Int
        get() = committedOffset

    /**
     * Clears scrollback state back to the live viewport.
     */
    fun reset() {
        preciseOffset = 0.0
        committedOffset = 0
    }

    /**
     * Clamps the current offset after history size changes.
     *
     * @return true when the committed offset changed.
     */
    fun clamp(historySize: Int): Boolean {
        val nextPrecise = preciseOffset.coerceIn(0.0, historySize.toDouble())
        val nextCommitted = commit(nextPrecise, historySize)
        val changed = nextCommitted != committedOffset
        preciseOffset = nextPrecise
        committedOffset = nextCommitted
        return changed
    }

    /**
     * Applies a fractional line delta.
     *
     * @return true when the precise offset changed.
     */
    fun scrollBy(deltaLines: Double, historySize: Int): Boolean {
        if (deltaLines == 0.0) return false

        val nextPrecise = (preciseOffset + deltaLines).coerceIn(0.0, historySize.toDouble())
        if (nextPrecise == preciseOffset) return false

        val nextCommitted = commit(nextPrecise, historySize)
        preciseOffset = nextPrecise
        committedOffset = nextCommitted
        return true
    }

    private fun commit(offset: Double, historySize: Int): Int {
        return floor(offset).toInt().coerceIn(0, historySize)
    }
}
