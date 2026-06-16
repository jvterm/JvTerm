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
 * The shared [TerminalShellIntegrationState] is session-owned and thread-safe.
 * Swing copies the visible rows into these primitive arrays before painting so
 * the row paint loop performs only array lookups.
 */
internal class TerminalShellIntegrationViewportDecorations {
    private var promptDividers = BooleanArray(0)
    private var failedCommandRails = BooleanArray(0)
    private var rowCount = 0

    /**
     * Copies visible shell integration decorations from [state] for [cache].
     */
    fun updateFrom(
        state: TerminalShellIntegrationState,
        cache: TerminalRenderCache,
    ) {
        ensureCapacity(cache.rows)
        rowCount = cache.rows
        val firstAbsoluteRow = cache.discardedCount + cache.historySize - cache.scrollbackOffset
        val bottomAbsoluteRow = firstAbsoluteRow + cache.rows - 1
        state.observeLiveBottomRow(bottomAbsoluteRow)
        state.copyViewport(
            firstAbsoluteRow = firstAbsoluteRow,
            rowCount = cache.rows,
            promptDividers = promptDividers,
            failedCommandRails = failedCommandRails,
        )
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

    private fun ensureCapacity(rows: Int) {
        if (promptDividers.size >= rows) return
        promptDividers = BooleanArray(rows)
        failedCommandRails = BooleanArray(rows)
    }
}
