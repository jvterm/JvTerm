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

import io.github.jvterm.render.cache.TerminalRenderCache
import io.github.jvterm.session.TerminalShellIntegrationState

/**
 * Renderer-local snapshot of shell integration decorations for one viewport.
 *
 * The shared [TerminalShellIntegrationState] is a session-owned command
 * timeline. Swing projects the visible rows into these primitive arrays before
 * painting so the row paint loop performs only array lookups.
 */
internal class TerminalShellIntegrationViewportDecorations {
    private var promptDividers = BooleanArray(0)
    private var failedCommandRails = BooleanArray(0)
    private var commandStarts = BooleanArray(0)
    private var commandEnds = BooleanArray(0)
    private var commandRecordIds = IntArray(0)
    private var commandLifecycleStates = IntArray(0)
    private var nextPromptDividers = BooleanArray(0)
    private var nextFailedCommandRails = BooleanArray(0)
    private var nextCommandStarts = BooleanArray(0)
    private var nextCommandEnds = BooleanArray(0)
    private var nextCommandRecordIds = IntArray(0)
    private var nextCommandLifecycleStates = IntArray(0)
    private var rowCount = 0

    /**
     * Copies visible shell integration decorations from [state] for [cache].
     *
     * @return true when any visible decoration flag changed.
     */
    fun updateFrom(
        state: TerminalShellIntegrationState,
        cache: TerminalRenderCache,
    ): Boolean {
        ensureCapacity(cache.rows)
        state.copyViewport(
            lineIds = cache.lineIds,
            rowCount = cache.rows,
            promptDividers = nextPromptDividers,
            failedCommandRails = nextFailedCommandRails,
            commandStarts = nextCommandStarts,
            commandEnds = nextCommandEnds,
            commandRecordIds = nextCommandRecordIds,
            commandLifecycleStates = nextCommandLifecycleStates,
        )
        suppressFirstPromptDivider(state, cache)
        val changed = decorationsChanged(cache.rows)
        swapBuffers()
        rowCount = cache.rows
        return changed
    }

    /**
     * Clears this viewport snapshot.
     */
    fun reset() {
        rowCount = 0
    }

    /**
     * Returns whether visible [row] should draw a prompt divider.
     */
    fun hasPromptDividerAt(row: Int): Boolean = row in 0 until rowCount && promptDividers[row]

    /**
     * Returns whether visible [row] should draw a failed-command rail.
     */
    fun hasFailedCommandRailAt(row: Int): Boolean = row in 0 until rowCount && failedCommandRails[row]

    /**
     * Returns whether visible [row] is the command-output start row.
     */
    fun hasCommandStartAt(row: Int): Boolean = row in 0 until rowCount && commandStarts[row]

    /**
     * Returns whether visible [row] is the command-output end row.
     */
    fun hasCommandEndAt(row: Int): Boolean = row in 0 until rowCount && commandEnds[row]

    /**
     * Returns the stable command record id associated with visible [row], or zero.
     */
    fun commandRecordIdAt(row: Int): Int = if (row in 0 until rowCount) commandRecordIds[row] else 0

    /**
     * Returns the command lifecycle associated with visible [row], or zero.
     */
    fun commandLifecycleAt(row: Int): Int = if (row in 0 until rowCount) commandLifecycleStates[row] else 0

    private fun suppressFirstPromptDivider(
        state: TerminalShellIntegrationState,
        cache: TerminalRenderCache,
    ) {
        val firstPromptLineId = state.firstPromptStartLineId()
        if (firstPromptLineId == NO_LINE_ID) return

        var row = 0
        while (row < cache.rows) {
            if (cache.lineIds[row] == firstPromptLineId) {
                nextPromptDividers[row] = false
                return
            }
            row++
        }
    }

    private fun ensureCapacity(rows: Int) {
        if (promptDividers.size >= rows) return
        promptDividers = BooleanArray(rows)
        failedCommandRails = BooleanArray(rows)
        commandStarts = BooleanArray(rows)
        commandEnds = BooleanArray(rows)
        commandRecordIds = IntArray(rows)
        commandLifecycleStates = IntArray(rows)
        nextPromptDividers = BooleanArray(rows)
        nextFailedCommandRails = BooleanArray(rows)
        nextCommandStarts = BooleanArray(rows)
        nextCommandEnds = BooleanArray(rows)
        nextCommandRecordIds = IntArray(rows)
        nextCommandLifecycleStates = IntArray(rows)
    }

    private fun decorationsChanged(nextRowCount: Int): Boolean {
        if (rowCount != nextRowCount) return true

        var row = 0
        while (row < nextRowCount) {
            if (promptDividers[row] != nextPromptDividers[row]) return true
            if (failedCommandRails[row] != nextFailedCommandRails[row]) return true
            if (commandStarts[row] != nextCommandStarts[row]) return true
            if (commandEnds[row] != nextCommandEnds[row]) return true
            if (commandRecordIds[row] != nextCommandRecordIds[row]) return true
            if (commandLifecycleStates[row] != nextCommandLifecycleStates[row]) return true
            row++
        }
        return false
    }

    private fun swapBuffers() {
        var prompt = promptDividers
        promptDividers = nextPromptDividers
        nextPromptDividers = prompt

        prompt = failedCommandRails
        failedCommandRails = nextFailedCommandRails
        nextFailedCommandRails = prompt

        prompt = commandStarts
        commandStarts = nextCommandStarts
        nextCommandStarts = prompt

        prompt = commandEnds
        commandEnds = nextCommandEnds
        nextCommandEnds = prompt

        var ids = commandRecordIds
        commandRecordIds = nextCommandRecordIds
        nextCommandRecordIds = ids

        ids = commandLifecycleStates
        commandLifecycleStates = nextCommandLifecycleStates
        nextCommandLifecycleStates = ids
    }

    private companion object {
        private const val NO_LINE_ID = 0L
    }
}
