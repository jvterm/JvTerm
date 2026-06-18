/*
 * Copyright 2026 Gagik Sargsyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.jvterm.ui.swing.viewport

import kotlin.math.floor

/**
 * EDT-confined scrollback viewport model for Swing wheel input.
 *
 * The terminal render reader remains line-addressed, but the Swing viewport is
 * pixel-addressed. Prompt dividers and fractional component heights can create
 * visual overflow without adding terminal scrollback rows, so this model keeps
 * one pixel scroll position and derives the whole-row render request from it.
 */
internal class SwingScrollModel {
    private var visualOffsetPixels: Double = 0.0
    private var visualOverflowPixels: Int = 0
    private var cellHeight: Int = 1
    private var historySize: Int = 0
    private var preciseOffset: Double = 0.0
    private var committedOffset: Int = 0
    private var renderOffset: Int = 0
    private var fraction: Double = 0.0

    /**
     * Current committed scrollback offset in whole terminal rows.
     */
    val offset: Int
        get() = committedOffset

    /**
     * Precise logical scrollback offset in terminal rows.
     *
     * `0.0` is the live viewport. Larger values move farther back into
     * scrollback history.
     */
    val preciseScrollbackOffset: Double
        get() = preciseOffset

    /**
     * Scrollback offset that should be requested from the render reader.
     */
    val requestedOffset: Int
        get() = renderOffset

    /**
     * Current visual pixel offset from the live bottom.
     */
    val visualScrollOffsetPixels: Double
        get() = visualOffsetPixels

    /**
     * Maximum visual pixel offset from the live bottom.
     */
    val visualScrollRangePixels: Int
        get() = visualOverflowPixels + historySize * cellHeight

    /**
     * Local live-viewport visual overflow consumed before terminal history rows.
     */
    val liveVisualOverflowPixels: Int
        get() = visualOverflowPixels

    /**
     * Whether the current viewport needs one row of overscan.
     */
    val needsOverscan: Boolean
        get() = fraction > 0.0 && renderOffset > committedOffset

    /**
     * Clears scrollback state back to the live viewport.
     */
    fun reset() {
        visualOffsetPixels = 0.0
        visualOverflowPixels = 0
        cellHeight = 1
        historySize = 0
        preciseOffset = 0.0
        committedOffset = 0
        renderOffset = 0
        fraction = 0.0
    }

    /**
     * Clamps the current offset after history size changes.
     *
     * @return true when the requested render offset changed.
     */
    fun clamp(historySize: Int): Boolean {
        require(historySize >= 0) { "historySize must be >= 0, was $historySize" }
        this.historySize = historySize
        return clampVisualOffset()
    }

    /**
     * Updates pixel metrics used to derive row-based render requests.
     *
     * @return true when the requested render offset changed.
     */
    fun updateVisualMetrics(
        historySize: Int,
        cellHeight: Int,
        visualOverflowPixels: Int,
    ): Boolean {
        require(historySize >= 0) { "historySize must be >= 0, was $historySize" }
        require(cellHeight > 0) { "cellHeight must be > 0, was $cellHeight" }
        require(visualOverflowPixels >= 0) {
            "visualOverflowPixels must be >= 0, was $visualOverflowPixels"
        }
        this.historySize = historySize
        this.cellHeight = cellHeight
        this.visualOverflowPixels = visualOverflowPixels
        return clampVisualOffset()
    }

    /**
     * Moves to [offsetLines], clamped to the available scrollback history.
     *
     * @return true when the precise offset changed.
     */
    fun scrollTo(
        offsetLines: Double,
        historySize: Int,
    ): Boolean {
        require(!offsetLines.isNaN()) { "offsetLines must not be NaN" }
        require(historySize >= 0) { "historySize must be >= 0, was $historySize" }

        this.historySize = historySize
        val nextVisual =
            if (offsetLines <= 0.0) {
                0.0
            } else {
                visualOverflowPixels.toDouble() + offsetLines.coerceAtMost(historySize.toDouble()) * cellHeight
            }
        return scrollToVisualOffset(nextVisual)
    }

    /**
     * Moves to an absolute visual pixel offset from the live bottom.
     *
     * @return true when the visual offset changed.
     */
    fun scrollToVisualOffset(offsetPixels: Double): Boolean {
        require(!offsetPixels.isNaN()) { "offsetPixels must not be NaN" }
        val nextVisual = offsetPixels.coerceIn(0.0, visualScrollRangePixels.toDouble())
        if (nextVisual == visualOffsetPixels) return false
        visualOffsetPixels = nextVisual
        recomputeDerivedOffsets()
        return true
    }

    /**
     * Applies a fractional line delta.
     *
     * @return true when the precise offset changed.
     */
    fun scrollBy(
        deltaLines: Double,
        historySize: Int,
    ): Boolean {
        require(!deltaLines.isNaN()) { "deltaLines must not be NaN" }
        if (deltaLines == 0.0) return false

        this.historySize = historySize
        return scrollToVisualOffset(visualOffsetPixels + deltaLines * cellHeight)
    }

    /**
     * Returns the vertical content translation for the current fractional
     * scroll position.
     */
    fun contentYOffset(cellHeight: Int): Double {
        require(cellHeight > 0) {
            "cellHeight must be > 0, was $cellHeight"
        }
        if (!needsOverscan) return 0.0
        return -(1.0 - fraction) * cellHeight
    }

    /**
     * Returns the render-cache row count needed for the current viewport.
     */
    fun requestedRows(renderRows: Int): Int {
        require(renderRows > 0) { "renderRows must be > 0, was $renderRows" }
        return if (needsOverscan) renderRows + 1 else renderRows
    }

    private fun committed(
        offset: Double,
        historySize: Int,
    ): Int = floor(offset).toInt().coerceIn(0, historySize)

    private fun renderOffset(
        offset: Double,
        historySize: Int,
    ): Int {
        val committed = committed(offset, historySize)
        val offsetFraction = fractionalPart(offset)
        if (offsetFraction == 0.0) return committed
        return (committed + 1).coerceIn(0, historySize)
    }

    private fun clampVisualOffset(): Boolean {
        val oldRenderOffset = renderOffset
        visualOffsetPixels = visualOffsetPixels.coerceIn(0.0, visualScrollRangePixels.toDouble())
        recomputeDerivedOffsets()
        return oldRenderOffset != renderOffset
    }

    private fun recomputeDerivedOffsets() {
        val historyPixels = maxOf(0.0, visualOffsetPixels - visualOverflowPixels.toDouble())
        val nextPrecise = (historyPixels / cellHeight).coerceIn(0.0, historySize.toDouble())
        preciseOffset = nextPrecise
        committedOffset = committed(nextPrecise, historySize)
        renderOffset = renderOffset(nextPrecise, historySize)
        fraction = fractionalPart(nextPrecise)
    }

    private fun fractionalPart(offset: Double): Double = offset - floor(offset)
}
