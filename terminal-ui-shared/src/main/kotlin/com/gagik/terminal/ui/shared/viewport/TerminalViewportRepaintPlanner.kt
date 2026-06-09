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
package com.gagik.terminal.ui.shared.viewport

import com.gagik.terminal.render.api.TerminalRenderCursorShape
import com.gagik.terminal.ui.shared.api.TerminalRenderSnapshot
import com.gagik.terminal.ui.shared.render.TerminalRenderMetrics
import com.gagik.terminal.ui.shared.render.visualCellRangeSpan
import com.gagik.terminal.ui.shared.render.visualCellRangeStart
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Computes bounded repaint regions from primitive render snapshot metadata.
 *
 * The planner is host-owned and UI-thread confined. It keeps the previously
 * painted cursor so cursor-only updates can repaint both old and new cells
 * without repainting the full terminal surface.
 */
class TerminalViewportRepaintPlanner {
    private var lastCursorKnown: Boolean = false
    private var lastCursorColumn: Int = 0
    private var lastCursorRow: Int = 0
    private var lastCursorVisible: Boolean = false
    private var lastCursorBlinking: Boolean = false
    private var lastCursorShape: TerminalRenderCursorShape = TerminalRenderCursorShape.BLOCK
    private var lastCursorGeneration: Long = UNINITIALIZED_GENERATION
    private var lastColumns: Int = 0
    private var lastRows: Int = 0
    private var lastStructureGeneration: Long = UNINITIALIZED_GENERATION
    private var lastScrollbackOffset: Int = UNINITIALIZED_OFFSET
    private var lastActiveBufferOrdinal: Int = UNINITIALIZED_ACTIVE_BUFFER
    private var lastLineGenerations: LongArray = LongArray(0)
    private var lastLineWrapped: BooleanArray = BooleanArray(0)

    /**
     * Clears remembered cursor and snapshot state when the host unbinds.
     */
    fun reset() {
        lastCursorKnown = false
        lastCursorColumn = 0
        lastCursorRow = 0
        lastCursorVisible = false
        lastCursorBlinking = false
        lastCursorShape = TerminalRenderCursorShape.BLOCK
        lastCursorGeneration = UNINITIALIZED_GENERATION
        lastColumns = 0
        lastRows = 0
        lastStructureGeneration = UNINITIALIZED_GENERATION
        lastScrollbackOffset = UNINITIALIZED_OFFSET
        lastActiveBufferOrdinal = UNINITIALIZED_ACTIVE_BUFFER
        lastLineGenerations = LongArray(0)
        lastLineWrapped = BooleanArray(0)
    }

    /**
     * Requests the smallest repaint regions needed for the latest published
     * [snapshot]. [contentYOffset] must match the vertical translation used by
     * painting the same snapshot.
     */
    fun requestFrameRepaint(
        snapshot: TerminalRenderSnapshot,
        metrics: TerminalRenderMetrics,
        componentWidth: Int,
        componentHeight: Int,
        contentYOffset: Double,
        repaintSink: TerminalRepaintSink,
    ) {
        if (requiresFullRepaint(snapshot)) {
            snapshotCacheState(snapshot)
            snapshotCursor(snapshot)
            repaintSink.requestFullRepaint()
            return
        }

        val visibleRows = visibleRows(snapshot, metrics, componentHeight)
        repaintChangedRows(
            snapshot = snapshot,
            metrics = metrics,
            componentWidth = componentWidth,
            componentHeight = componentHeight,
            visibleRows = visibleRows,
            contentYOffset = contentYOffset,
            repaintSink = repaintSink,
        )

        if (cursorChanged(snapshot)) {
            repaintCursorIfNeeded(
                known = lastCursorKnown,
                column = lastCursorColumn,
                row = lastCursorRow,
                visible = lastCursorVisible,
                snapshot = snapshot,
                metrics = metrics,
                componentWidth = componentWidth,
                componentHeight = componentHeight,
                visibleRows = visibleRows,
                contentYOffset = contentYOffset,
                skipChangedRows = true,
                repaintSink = repaintSink,
            )
            repaintCursorIfNeeded(
                known = true,
                column = snapshot.cursorColumn,
                row = snapshot.cursorRow,
                visible = snapshot.cursorVisible,
                snapshot = snapshot,
                metrics = metrics,
                componentWidth = componentWidth,
                componentHeight = componentHeight,
                visibleRows = visibleRows,
                contentYOffset = contentYOffset,
                skipChangedRows = true,
                repaintSink = repaintSink,
            )
        }

        snapshotCacheState(snapshot)
        snapshotCursor(snapshot)
    }

    /**
     * Requests a repaint for the current blinking cursor cell only.
     */
    fun requestCursorBlinkRepaint(
        snapshot: TerminalRenderSnapshot,
        metrics: TerminalRenderMetrics,
        componentWidth: Int,
        componentHeight: Int,
        contentYOffset: Double,
        repaintSink: TerminalRepaintSink,
    ) {
        if (!snapshot.cursorVisible || !snapshot.cursorBlinking) return

        repaintCursorIfNeeded(
            known = true,
            column = snapshot.cursorColumn,
            row = snapshot.cursorRow,
            visible = true,
            snapshot = snapshot,
            metrics = metrics,
            componentWidth = componentWidth,
            componentHeight = componentHeight,
            visibleRows = visibleRows(snapshot, metrics, componentHeight),
            contentYOffset = contentYOffset,
            skipChangedRows = false,
            repaintSink = repaintSink,
        )
    }

    private fun repaintChangedRows(
        snapshot: TerminalRenderSnapshot,
        metrics: TerminalRenderMetrics,
        componentWidth: Int,
        componentHeight: Int,
        visibleRows: Int,
        contentYOffset: Double,
        repaintSink: TerminalRepaintSink,
    ) {
        var row = 0
        while (row < visibleRows) {
            if (!rowChanged(snapshot, row)) {
                row++
                continue
            }

            val startRow = row
            row++
            while (row < visibleRows && rowChanged(snapshot, row)) {
                row++
            }

            repaintRowRun(
                startRow = startRow,
                endRow = row,
                metrics = metrics,
                componentWidth = componentWidth,
                componentHeight = componentHeight,
                contentYOffset = contentYOffset,
                repaintSink = repaintSink,
            )
        }
    }

    private fun repaintCursorIfNeeded(
        known: Boolean,
        column: Int,
        row: Int,
        visible: Boolean,
        snapshot: TerminalRenderSnapshot,
        metrics: TerminalRenderMetrics,
        componentWidth: Int,
        componentHeight: Int,
        visibleRows: Int,
        contentYOffset: Double,
        skipChangedRows: Boolean,
        repaintSink: TerminalRepaintSink,
    ): Boolean {
        if (!known || !visible) return false
        if (column !in 0 until snapshot.columns || row !in 0 until visibleRows) return false
        if (skipChangedRows && rowChanged(snapshot, row)) return false

        val flags = snapshot.flags[snapshot.rowOffset(row) + column]
        val startColumn = visualCellRangeStart(flags, column)
        val columnSpan = visualCellRangeSpan(flags, column, snapshot.columns)
        val x = startColumn * metrics.cellWidth
        if (x >= componentWidth) return false
        val regionWidth = minOf(columnSpan * metrics.cellWidth, componentWidth - x)
        if (regionWidth <= 0) return false

        val y = rowTop(row, metrics.cellHeight, contentYOffset)
        val bottom = rowBottom(row + 1, metrics.cellHeight, contentYOffset)
        if (bottom <= 0 || y >= componentHeight) return false
        val clippedY = maxOf(0, y)
        val clippedBottom = minOf(componentHeight, bottom)
        val regionHeight = clippedBottom - clippedY
        if (regionHeight <= 0) return false

        repaintSink.requestRegionRepaint(
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
        metrics: TerminalRenderMetrics,
        componentWidth: Int,
        componentHeight: Int,
        contentYOffset: Double,
        repaintSink: TerminalRepaintSink,
    ) {
        if (componentWidth <= 0 || componentHeight <= 0) return

        val y = rowTop(startRow, metrics.cellHeight, contentYOffset)
        val bottom = rowBottom(endRow, metrics.cellHeight, contentYOffset)
        if (bottom <= 0 || y >= componentHeight) return

        val clippedY = maxOf(0, y)
        val clippedBottom = minOf(componentHeight, bottom)
        val regionHeight = clippedBottom - clippedY
        if (regionHeight <= 0) return

        repaintSink.requestRegionRepaint(0, clippedY, componentWidth, regionHeight)
    }

    private fun rowTop(
        row: Int,
        cellHeight: Int,
        contentYOffset: Double,
    ): Int =
        if (contentYOffset == 0.0) {
            row * cellHeight
        } else {
            floor(row.toDouble() * cellHeight.toDouble() + contentYOffset).toInt()
        }

    private fun rowBottom(
        endRow: Int,
        cellHeight: Int,
        contentYOffset: Double,
    ): Int =
        if (contentYOffset == 0.0) {
            endRow * cellHeight
        } else {
            ceil(endRow.toDouble() * cellHeight.toDouble() + contentYOffset).toInt()
        }

    private fun visibleRows(
        snapshot: TerminalRenderSnapshot,
        metrics: TerminalRenderMetrics,
        componentHeight: Int,
    ): Int = minOf(snapshot.rows, componentHeight / metrics.cellHeight + 1)

    private fun rowChanged(
        snapshot: TerminalRenderSnapshot,
        row: Int,
    ): Boolean =
        lastLineGenerations[row] != snapshot.lineGenerations[row] ||
            lastLineWrapped[row] != snapshot.lineWrapped[row]

    private fun requiresFullRepaint(snapshot: TerminalRenderSnapshot): Boolean =
        snapshot.resizedOnLastUpdate ||
            lastColumns != snapshot.columns ||
            lastRows != snapshot.rows ||
            lastLineGenerations.size != snapshot.rows ||
            lastLineWrapped.size != snapshot.rows ||
            lastStructureGeneration != snapshot.structureGeneration ||
            lastScrollbackOffset != snapshot.scrollbackOffset ||
            lastActiveBufferOrdinal != snapshot.activeBuffer.ordinal

    private fun cursorChanged(snapshot: TerminalRenderSnapshot): Boolean =
        !lastCursorKnown ||
            lastCursorColumn != snapshot.cursorColumn ||
            lastCursorRow != snapshot.cursorRow ||
            lastCursorVisible != snapshot.cursorVisible ||
            lastCursorBlinking != snapshot.cursorBlinking ||
            lastCursorShape != snapshot.cursorShape ||
            lastCursorGeneration != snapshot.cursorGeneration

    private fun snapshotCursor(snapshot: TerminalRenderSnapshot) {
        lastCursorKnown = true
        lastCursorColumn = snapshot.cursorColumn
        lastCursorRow = snapshot.cursorRow
        lastCursorVisible = snapshot.cursorVisible
        lastCursorBlinking = snapshot.cursorBlinking
        lastCursorShape = snapshot.cursorShape
        lastCursorGeneration = snapshot.cursorGeneration
    }

    private fun snapshotCacheState(snapshot: TerminalRenderSnapshot) {
        if (lastLineGenerations.size != snapshot.rows) {
            lastLineGenerations = LongArray(snapshot.rows)
            lastLineWrapped = BooleanArray(snapshot.rows)
        }

        var row = 0
        while (row < snapshot.rows) {
            lastLineGenerations[row] = snapshot.lineGenerations[row]
            lastLineWrapped[row] = snapshot.lineWrapped[row]
            row++
        }

        lastColumns = snapshot.columns
        lastRows = snapshot.rows
        lastStructureGeneration = snapshot.structureGeneration
        lastScrollbackOffset = snapshot.scrollbackOffset
        lastActiveBufferOrdinal = snapshot.activeBuffer.ordinal
    }

    private companion object {
        private const val UNINITIALIZED_GENERATION = -1L
        private const val UNINITIALIZED_OFFSET = Int.MIN_VALUE
        private const val UNINITIALIZED_ACTIVE_BUFFER = -1
    }
}
