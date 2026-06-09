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
package com.gagik.terminal.ui.shared.api

/**
 * Half-open terminal cell selection consumed by shared painting code.
 */
interface TerminalCellSelection {
    /** Zero-based anchor caret column. */
    val anchorColumn: Int

    /** Zero-based anchor row. */
    val anchorRow: Int

    /** Zero-based moving caret column. */
    val caretColumn: Int

    /** Zero-based moving caret row. */
    val caretRow: Int

    /** Whether the selection is rectangular. */
    val isBlock: Boolean

    /**
     * Returns true when the selection covers no cells.
     */
    val isEmpty: Boolean
        get() = anchorColumn == caretColumn && anchorRow == caretRow

    /**
     * Returns the selected half-open column range for [row].
     *
     * @param row visible render row.
     * @param columns visible column count.
     * @param cache optional primitive snapshot used to include whole wide cells.
     * @return packed range, or [CellSelection.NO_RANGE].
     */
    fun packedColumnRange(
        row: Int,
        columns: Int,
        cache: TerminalRenderSnapshot? = null,
    ): Long
}
