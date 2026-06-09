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

import com.gagik.terminal.render.api.TerminalColorPalette
import com.gagik.terminal.render.api.TerminalRenderBufferKind
import com.gagik.terminal.render.api.TerminalRenderCursorShape

/**
 * Allocation-free primitive view consumed by the shared Java2D painter.
 *
 * Platform modules adapt their frame cache or renderer-owned snapshot to this
 * interface once and reuse that adapter during paint. Implementations must not
 * allocate while serving property reads, row offsets, or cluster reference
 * decoding.
 */
interface TerminalRenderSnapshot {
    /** Visible width in terminal cells. */
    val columns: Int

    /** Visible height in terminal rows. */
    val rows: Int

    /** Retained scrollback history line count. */
    val historySize: Int

    /** Resolved scrollback offset for this snapshot. */
    val scrollbackOffset: Int

    /** Count of history lines discarded before this snapshot. */
    val discardedCount: Long

    /** Row-major Unicode scalar or cell payload words. */
    val codeWords: IntArray

    /** Row-major public render attribute words. */
    val attrWords: LongArray

    /** Row-major public render cell flags. */
    val flags: IntArray

    /** Row-major optional extra-attribute words. */
    val extraAttrWords: LongArray

    /** Row-major hyperlink identifiers, where zero means no hyperlink. */
    val hyperlinkIds: IntArray

    /** Row-major packed cluster references. */
    val clusterRefs: LongArray

    /** Packed cluster code point storage. */
    val clusterCodepoints: IntArray

    /** Per-row visual generations. */
    val lineGenerations: LongArray

    /** Per-row soft-wrap flags. */
    val lineWrapped: BooleanArray

    /** Last copied frame generation. */
    val frameGeneration: Long

    /** Last copied structure generation. */
    val structureGeneration: Long

    /** Active terminal screen buffer. */
    val activeBuffer: TerminalRenderBufferKind

    /** Active resolved terminal palette. */
    val palette: TerminalColorPalette

    /** Cursor column in visible coordinates. */
    val cursorColumn: Int

    /** Cursor row in visible coordinates. */
    val cursorRow: Int

    /** Whether the cursor should be painted. */
    val cursorVisible: Boolean

    /** Whether blink visibility should affect the cursor. */
    val cursorBlinking: Boolean

    /** Cursor shape for this snapshot. */
    val cursorShape: TerminalRenderCursorShape

    /** Cursor state generation. */
    val cursorGeneration: Long

    /** Whether primitive storage resized during the last update. */
    val resizedOnLastUpdate: Boolean

    /** Whether cursor state changed during the last update. */
    val cursorChangedOnLastUpdate: Boolean

    /**
     * Returns the row-major base offset for [row].
     *
     * @param row visible row index.
     * @return first flattened cell index for [row].
     */
    fun rowOffset(row: Int): Int

    /**
     * Returns the code point offset encoded in [ref].
     *
     * @param ref packed cluster reference.
     * @return offset into [clusterCodepoints].
     */
    fun clusterOffset(ref: Long): Int

    /**
     * Returns the code point length encoded in [ref].
     *
     * @param ref packed cluster reference.
     * @return cluster code point length.
     */
    fun clusterLength(ref: Long): Int
}
