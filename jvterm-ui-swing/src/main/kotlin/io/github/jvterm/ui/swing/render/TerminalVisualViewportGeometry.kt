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
package io.github.jvterm.ui.swing.render

import io.github.jvterm.ui.swing.settings.SwingMetrics
import io.github.jvterm.ui.swing.settings.SwingSettings
import java.awt.Rectangle
import kotlin.math.ceil
import kotlin.math.floor

/**
 * EDT-owned pixel geometry for the current Swing render-cache viewport.
 *
 * Terminal row coordinates remain terminal-native. This model adds UI-only
 * visual bands such as prompt dividers, stores the content origin used for the
 * current paint, and exposes all row-to-pixel conversions so painting, repaint
 * planning, hit testing, mouse reporting, and command navigation consume the
 * same coordinate system.
 */
internal class TerminalVisualViewportGeometry {
    private var rowTops = IntArray(0)
    private var dividerBeforeRows = BooleanArray(0)

    var rowCount: Int = 0
        private set
    var cellHeight: Int = 0
        private set
    var dividerBandHeight: Int = 0
        private set
    var visualHeight: Int = 0
        private set
    var viewportPixelHeight: Int = 0
        private set
    var liveVisualOverflowPixels: Int = 0
        private set
    var contentOriginY: Double = 0.0
        private set

    /**
     * Rebuilds row positions and divider bands for the current render cache.
     *
     * @return true when visible row positions or divider flags changed.
     */
    fun updateLayout(
        settings: SwingSettings,
        metrics: SwingMetrics,
        decorations: TerminalShellIntegrationViewportDecorations?,
        rows: Int,
        terminalRows: Int,
        viewportPixelHeight: Int,
    ): Boolean {
        require(rows >= 0) { "rows must be >= 0, was $rows" }
        require(terminalRows > 0) { "terminalRows must be > 0, was $terminalRows" }
        require(viewportPixelHeight >= 0) { "viewportPixelHeight must be >= 0, was $viewportPixelHeight" }
        ensureCapacity(rows)

        val previousRowCount = rowCount
        val previousCellHeight = cellHeight
        val previousDividerBandHeight = dividerBandHeight
        val previousVisualHeight = visualHeight
        val previousViewportPixelHeight = this.viewportPixelHeight
        val previousLiveVisualOverflowPixels = liveVisualOverflowPixels
        val bandHeight =
            if (settings.shellIntegrationPromptDividersVisible) {
                settings.shellIntegrationPromptDividerGap
            } else {
                0
            }

        rowCount = rows
        cellHeight = metrics.cellHeight
        dividerBandHeight = bandHeight
        this.viewportPixelHeight = viewportPixelHeight

        var changed =
            previousRowCount != rows ||
                previousCellHeight != metrics.cellHeight ||
                previousDividerBandHeight != bandHeight ||
                previousViewportPixelHeight != viewportPixelHeight
        var y = 0
        var row = 0
        while (row < rows) {
            val hasDivider = bandHeight > 0 && decorations != null && decorations.hasPromptDividerAt(row)
            if (hasDivider) y += bandHeight
            if (rowTops[row] != y) {
                changed = true
                rowTops[row] = y
            }
            if (dividerBeforeRows[row] != hasDivider) {
                changed = true
                dividerBeforeRows[row] = hasDivider
            }
            y += metrics.cellHeight
            row++
        }

        visualHeight = y
        if (previousVisualHeight != visualHeight) changed = true
        liveVisualOverflowPixels = maxOf(0, visualHeightForRows(minOf(rows, terminalRows)) - viewportPixelHeight)
        if (previousLiveVisualOverflowPixels != liveVisualOverflowPixels) changed = true
        clearUnusedRows(rows, previousRowCount)
        return changed
    }

    /**
     * Updates the current content origin.
     *
     * @return true when the origin changed.
     */
    fun updateContentOrigin(contentOriginY: Double): Boolean {
        require(!contentOriginY.isNaN()) { "contentOriginY must not be NaN" }
        if (this.contentOriginY == contentOriginY) return false
        this.contentOriginY = contentOriginY
        return true
    }

    /**
     * Clears retained row geometry.
     */
    fun reset() {
        rowCount = 0
        cellHeight = 0
        dividerBandHeight = 0
        visualHeight = 0
        viewportPixelHeight = 0
        liveVisualOverflowPixels = 0
        contentOriginY = 0.0
    }

    /**
     * Returns true when [row] has a prompt divider band immediately before it.
     */
    fun hasDividerBefore(row: Int): Boolean = row in 0 until rowCount && dividerBeforeRows[row]

    /**
     * Returns the visual top of terminal [row], excluding [contentOriginY].
     */
    fun rowTop(row: Int): Int {
        if (row !in 0 until rowCount) return row * cellHeight
        return rowTops[row]
    }

    /**
     * Returns the visual bottom of terminal [row], excluding [contentOriginY].
     */
    fun rowBottom(row: Int): Int = rowTop(row) + cellHeight

    /**
     * Returns the visual height occupied by the first [rows] terminal rows.
     */
    fun visualHeightForRows(rows: Int): Int {
        val safeRows = rows.coerceIn(0, rowCount)
        if (safeRows == 0) return 0
        return rowBottom(safeRows - 1)
    }

    /**
     * Returns the graphics translation needed before painting [row].
     */
    fun translationForRow(row: Int): Int = rowTop(row) - row * cellHeight

    /**
     * Returns the visual y coordinate for the divider before [row].
     */
    fun dividerY(
        row: Int,
        thickness: Int,
    ): Int {
        val centeredInset = maxOf(0, dividerBandHeight - thickness) / 2
        return rowTop(row) - dividerBandHeight + centeredInset
    }

    /**
     * Maps visual local y, excluding [contentOriginY], to a terminal row.
     */
    fun rowAt(visualY: Int): Int {
        if (rowCount <= 0) return 0
        if (visualY <= 0) return 0
        if (visualY >= visualHeight) return rowCount - 1

        var low = 0
        var high = rowCount - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (visualY < rowBottom(mid)) {
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return low.coerceIn(0, rowCount - 1)
    }

    /**
     * Maps a component-local y coordinate to a terminal row.
     */
    fun rowAtComponentY(
        y: Int,
        paddingTop: Int,
    ): Int = rowAt(floor(y.toDouble() - paddingTop.toDouble() - contentOriginY).toInt())

    /**
     * Converts visual local y into terminal-native pixel y.
     */
    fun terminalPixelY(
        visualY: Int,
        row: Int,
    ): Int {
        val safeRow = row.coerceIn(0, maxOf(0, rowCount - 1))
        val yInCell = (visualY - rowTop(safeRow)).coerceIn(0, maxOf(0, cellHeight - 1))
        return safeRow * cellHeight + yInCell
    }

    /**
     * Converts a component-local y coordinate into terminal-native pixel y.
     */
    fun terminalPixelYAtComponentY(
        y: Int,
        paddingTop: Int,
    ): Int {
        val localY = floor(y.toDouble() - paddingTop.toDouble() - contentOriginY).toInt()
        val row = rowAt(localY)
        return terminalPixelY(localY, row)
    }

    /**
     * Returns the first row that should be considered for painting [clip].
     */
    fun firstPaintRow(
        clip: Rectangle?,
        paddingTop: Int,
    ): Int {
        if (clip == null) return 0
        val localY = floor(clip.y.toDouble() - paddingTop.toDouble() - contentOriginY).toInt()
        return rowAt(localY)
    }

    /**
     * Returns one past the last row that should be considered for painting.
     */
    fun lastPaintRowExclusive(
        clip: Rectangle?,
        componentHeight: Int,
        paddingTop: Int,
        paddingBottom: Int,
    ): Int {
        val availableHeight = componentHeight - paddingTop - paddingBottom
        val visibleRows = minOf(rowCount, rowAt(ceil(availableHeight.toDouble() - contentOriginY).toInt()) + 2)
        if (clip == null || clip.height <= 0) return visibleRows

        val clipBottom = clip.y + clip.height
        if (clipBottom <= 0) return 0
        val localBottom = ceil(clipBottom.toDouble() - paddingTop.toDouble() - contentOriginY).toInt()
        return (rowAt(localBottom) + 1).coerceIn(0, visibleRows)
    }

    /**
     * Returns one past the last row visible in the component.
     */
    fun visibleRowsExclusive(
        componentHeight: Int,
        paddingTop: Int,
        paddingBottom: Int,
    ): Int {
        val availableHeight = componentHeight - paddingTop - paddingBottom
        return minOf(rowCount, rowAt(ceil(availableHeight.toDouble() - contentOriginY).toInt()) + 2)
    }

    /**
     * Returns the first fully visible row, useful for command navigation.
     */
    fun firstFullyVisibleRow(): Int {
        if (rowCount <= 0) return 0
        var row = rowAt(ceil(-contentOriginY).toInt())
        while (row < rowCount && rowTop(row).toDouble() + contentOriginY < 0.0) {
            row++
        }
        return row.coerceIn(0, rowCount - 1)
    }

    private fun ensureCapacity(rows: Int) {
        if (rowTops.size >= rows) return
        rowTops = rowTops.copyOf(rows)
        dividerBeforeRows = dividerBeforeRows.copyOf(rows)
    }

    private fun clearUnusedRows(
        rows: Int,
        previousRows: Int,
    ) {
        var row = rows
        while (row < previousRows && row < dividerBeforeRows.size) {
            rowTops[row] = 0
            dividerBeforeRows[row] = false
            row++
        }
    }
}
