package com.gagik.terminal.ui.swing.viewport

import com.gagik.terminal.render.api.TerminalRenderCursor
import com.gagik.terminal.render.cache.TerminalRenderCache
import com.gagik.terminal.ui.swing.settings.TerminalSwingMetrics
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Computes bounded Swing repaint regions from render-cache change metadata.
 *
 * The planner is component-owned and EDT-confined. It keeps the previously
 * painted cursor so cursor-only updates can repaint both the old and new cell
 * without repainting the full terminal surface.
 */
internal class TerminalSwingRepaintPlanner {
    private var lastCursor: TerminalRenderCursor? = null

    /**
     * Clears remembered cursor state when the component unbinds or resets.
     */
    fun reset() {
        lastCursor = null
    }

    /**
     * Requests the smallest repaint regions needed for the latest published
     * [cache] update. [contentYOffset] must match the vertical translation used
     * by painting the same cache.
     */
    fun requestFrameRepaint(
        cache: TerminalRenderCache,
        metrics: TerminalSwingMetrics,
        componentWidth: Int,
        componentHeight: Int,
        contentYOffset: Double,
        repaintAll: () -> Unit,
        repaintRegion: (x: Int, y: Int, width: Int, height: Int) -> Unit,
    ) {
        if (cache.resizedOnLastUpdate) {
            lastCursor = cache.cursor
            repaintAll()
            return
        }

        val visibleRows = visibleRows(cache, metrics, componentHeight)
        repaintDirtyRows(
            cache = cache,
            metrics = metrics,
            componentWidth = componentWidth,
            componentHeight = componentHeight,
            visibleRows = visibleRows,
            contentYOffset = contentYOffset,
            repaintRegion = repaintRegion,
        )

        if (cache.cursorChangedOnLastUpdate) {
            repaintCursorIfNeeded(
                cursor = lastCursor,
                cache = cache,
                metrics = metrics,
                componentWidth = componentWidth,
                componentHeight = componentHeight,
                visibleRows = visibleRows,
                contentYOffset = contentYOffset,
                skipDirtyRows = true,
                repaintRegion = repaintRegion,
            )
            repaintCursorIfNeeded(
                cursor = cache.cursor,
                cache = cache,
                metrics = metrics,
                componentWidth = componentWidth,
                componentHeight = componentHeight,
                visibleRows = visibleRows,
                contentYOffset = contentYOffset,
                skipDirtyRows = true,
                repaintRegion = repaintRegion,
            )
        }

        lastCursor = cache.cursor
    }

    /**
     * Requests a repaint for the current blinking cursor cell only.
     */
    fun requestCursorBlinkRepaint(
        cache: TerminalRenderCache,
        metrics: TerminalSwingMetrics,
        componentWidth: Int,
        componentHeight: Int,
        contentYOffset: Double,
        repaintRegion: (x: Int, y: Int, width: Int, height: Int) -> Unit,
    ) {
        val cursor = cache.cursor ?: return
        if (!cursor.visible || !cursor.blinking) return

        repaintCursorIfNeeded(
            cursor = cursor,
            cache = cache,
            metrics = metrics,
            componentWidth = componentWidth,
            componentHeight = componentHeight,
            visibleRows = visibleRows(cache, metrics, componentHeight),
            contentYOffset = contentYOffset,
            skipDirtyRows = false,
            repaintRegion = repaintRegion,
        )
    }

    private fun repaintDirtyRows(
        cache: TerminalRenderCache,
        metrics: TerminalSwingMetrics,
        componentWidth: Int,
        componentHeight: Int,
        visibleRows: Int,
        contentYOffset: Double,
        repaintRegion: (x: Int, y: Int, width: Int, height: Int) -> Unit,
    ) {
        var row = 0
        while (row < visibleRows) {
            if (!cache.dirtyRows[row]) {
                row++
                continue
            }

            val startRow = row
            row++
            while (row < visibleRows && cache.dirtyRows[row]) {
                row++
            }

            repaintRowRun(
                startRow = startRow,
                endRow = row,
                metrics = metrics,
                componentWidth = componentWidth,
                componentHeight = componentHeight,
                contentYOffset = contentYOffset,
                repaintRegion = repaintRegion,
            )
        }
    }

    private fun repaintCursorIfNeeded(
        cursor: TerminalRenderCursor?,
        cache: TerminalRenderCache,
        metrics: TerminalSwingMetrics,
        componentWidth: Int,
        componentHeight: Int,
        visibleRows: Int,
        contentYOffset: Double,
        skipDirtyRows: Boolean,
        repaintRegion: (x: Int, y: Int, width: Int, height: Int) -> Unit,
    ): Boolean {
        if (cursor == null || !cursor.visible) return false
        if (cursor.column !in 0 until cache.columns || cursor.row !in 0 until visibleRows) return false
        if (skipDirtyRows && cache.dirtyRows[cursor.row]) return false

        val x = cursor.column * metrics.cellWidth
        if (x >= componentWidth) return false
        val regionWidth = minOf(metrics.cellWidth, componentWidth - x)
        if (regionWidth <= 0) return false

        val y = rowTop(cursor.row, metrics.cellHeight, contentYOffset)
        val bottom = rowBottom(cursor.row + 1, metrics.cellHeight, contentYOffset)
        if (bottom <= 0 || y >= componentHeight) return false
        val clippedY = maxOf(0, y)
        val clippedBottom = minOf(componentHeight, bottom)
        val regionHeight = clippedBottom - clippedY
        if (regionHeight <= 0) return false

        repaintRegion(
            x,
            clippedY,
            regionWidth,
            regionHeight,
        )
        return true
    }

    private fun repaintRowRun(
        startRow: Int,
        endRow: Int,
        metrics: TerminalSwingMetrics,
        componentWidth: Int,
        componentHeight: Int,
        contentYOffset: Double,
        repaintRegion: (x: Int, y: Int, width: Int, height: Int) -> Unit,
    ) {
        if (componentWidth <= 0 || componentHeight <= 0) return

        val y = rowTop(startRow, metrics.cellHeight, contentYOffset)
        val bottom = rowBottom(endRow, metrics.cellHeight, contentYOffset)
        if (bottom <= 0 || y >= componentHeight) return

        val clippedY = maxOf(0, y)
        val clippedBottom = minOf(componentHeight, bottom)
        val regionHeight = clippedBottom - clippedY
        if (regionHeight <= 0) return

        repaintRegion(0, clippedY, componentWidth, regionHeight)
    }

    private fun rowTop(
        row: Int,
        cellHeight: Int,
        contentYOffset: Double,
    ): Int {
        return if (contentYOffset == 0.0) {
            row * cellHeight
        } else {
            floor(row.toDouble() * cellHeight.toDouble() + contentYOffset).toInt()
        }
    }

    private fun rowBottom(
        endRow: Int,
        cellHeight: Int,
        contentYOffset: Double,
    ): Int {
        return if (contentYOffset == 0.0) {
            endRow * cellHeight
        } else {
            ceil(endRow.toDouble() * cellHeight.toDouble() + contentYOffset).toInt()
        }
    }

    private fun visibleRows(
        cache: TerminalRenderCache,
        metrics: TerminalSwingMetrics,
        componentHeight: Int,
    ): Int {
        return minOf(cache.rows, componentHeight / metrics.cellHeight + 1)
    }
}
