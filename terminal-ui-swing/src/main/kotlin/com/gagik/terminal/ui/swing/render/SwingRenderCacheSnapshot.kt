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
package com.gagik.terminal.ui.swing.render

import com.gagik.terminal.render.cache.TerminalRenderCache
import com.gagik.terminal.ui.shared.api.TerminalRenderSnapshot

/**
 * Reusable allocation-free adapter from Swing's render cache to shared painter
 * input.
 */
internal class SwingRenderCacheSnapshot : TerminalRenderSnapshot {
    private var cache: TerminalRenderCache? = null

    /**
     * Points this adapter at [cache] for the duration of one paint/read pass.
     */
    fun wrap(cache: TerminalRenderCache): TerminalRenderSnapshot {
        this.cache = cache
        return this
    }

    private val current: TerminalRenderCache
        get() = requireNotNull(cache) { "SwingRenderCacheSnapshot is not wrapped" }

    override val columns: Int get() = current.columns
    override val rows: Int get() = current.rows
    override val historySize: Int get() = current.historySize
    override val scrollbackOffset: Int get() = current.scrollbackOffset
    override val discardedCount: Long get() = current.discardedCount
    override val codeWords: IntArray get() = current.codeWords
    override val attrWords: LongArray get() = current.attrWords
    override val flags: IntArray get() = current.flags
    override val extraAttrWords: LongArray get() = current.extraAttrWords
    override val hyperlinkIds: IntArray get() = current.hyperlinkIds
    override val clusterRefs: LongArray get() = current.clusterRefs
    override val clusterCodepoints: IntArray get() = current.clusterCodepoints
    override val lineGenerations: LongArray get() = current.lineGenerations
    override val lineWrapped: BooleanArray get() = current.lineWrapped
    override val frameGeneration: Long get() = current.frameGeneration
    override val structureGeneration: Long get() = current.structureGeneration
    override val activeBuffer get() = current.activeBuffer
    override val palette get() = current.palette
    override val cursorColumn: Int get() = current.cursorColumn
    override val cursorRow: Int get() = current.cursorRow
    override val cursorVisible: Boolean get() = current.cursorVisible
    override val cursorBlinking: Boolean get() = current.cursorBlinking
    override val cursorShape get() = current.cursorShape
    override val cursorGeneration: Long get() = current.cursorGeneration
    override val resizedOnLastUpdate: Boolean get() = current.resizedOnLastUpdate
    override val cursorChangedOnLastUpdate: Boolean get() = current.cursorChangedOnLastUpdate

    override fun rowOffset(row: Int): Int = current.rowOffset(row)

    override fun clusterOffset(ref: Long): Int = current.clusterOffset(ref)

    override fun clusterLength(ref: Long): Int = current.clusterLength(ref)
}
