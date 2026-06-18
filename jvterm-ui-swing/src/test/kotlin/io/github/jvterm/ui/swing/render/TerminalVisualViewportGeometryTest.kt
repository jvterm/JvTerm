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

import io.github.jvterm.render.api.TerminalRenderAttrs
import io.github.jvterm.render.api.TerminalRenderBufferKind
import io.github.jvterm.render.api.TerminalRenderCellFlags
import io.github.jvterm.render.api.TerminalRenderClusterDataSink
import io.github.jvterm.render.api.TerminalRenderClusterSink
import io.github.jvterm.render.api.TerminalRenderCursor
import io.github.jvterm.render.api.TerminalRenderCursorShape
import io.github.jvterm.render.api.TerminalRenderExtraAttrs
import io.github.jvterm.render.api.TerminalRenderFrame
import io.github.jvterm.render.api.TerminalRenderFrameConsumer
import io.github.jvterm.render.api.TerminalRenderFrameReader
import io.github.jvterm.render.cache.TerminalRenderCache
import io.github.jvterm.session.TerminalShellIntegrationState
import io.github.jvterm.ui.swing.settings.SwingMetrics
import io.github.jvterm.ui.swing.settings.SwingSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalVisualViewportGeometryTest {
    @Test
    fun `divider band is inserted before second and later prompt rows`() {
        val settings = SwingSettings(shellIntegrationPromptDividerGap = 6)
        val cache = TerminalRenderCache(columns = 3, rows = 3)
        cache.updateFrom(TextRowsFrame(lineIds = longArrayOf(1, 2, 3)))
        val state = TerminalShellIntegrationState()
        state.recordPromptStart(1)
        state.recordPromptStart(2)
        val decorations = TerminalShellIntegrationViewportDecorations()
        decorations.updateFrom(state, cache)
        val layout = TerminalVisualViewportGeometry()

        assertTrue(
            layout.updateLayout(
                settings = settings,
                metrics = METRICS,
                decorations = decorations,
                rows = cache.rows,
                terminalRows = 3,
                viewportPixelHeight = METRICS.cellHeight * 3,
            ),
        )

        assertFalse(layout.hasDividerBefore(0))
        assertTrue(layout.hasDividerBefore(1))
        assertEquals(0, layout.rowTop(0))
        assertEquals(METRICS.cellHeight + settings.shellIntegrationPromptDividerGap, layout.rowTop(1))
        assertEquals(METRICS.cellHeight * 2 + settings.shellIntegrationPromptDividerGap, layout.rowTop(2))
        assertEquals(METRICS.cellHeight * 2 + settings.shellIntegrationPromptDividerGap, layout.visualHeightForRows(2))
        assertEquals(settings.shellIntegrationPromptDividerGap, layout.liveVisualOverflowPixels)
    }

    @Test
    fun `hit testing assigns divider band to following prompt row`() {
        val settings = SwingSettings(shellIntegrationPromptDividerGap = 6)
        val cache = TerminalRenderCache(columns = 3, rows = 3)
        cache.updateFrom(TextRowsFrame(lineIds = longArrayOf(1, 2, 3)))
        val state = TerminalShellIntegrationState()
        state.recordPromptStart(1)
        state.recordPromptStart(2)
        val decorations = TerminalShellIntegrationViewportDecorations()
        decorations.updateFrom(state, cache)
        val layout = TerminalVisualViewportGeometry()
        layout.updateLayout(
            settings = settings,
            metrics = METRICS,
            decorations = decorations,
            rows = cache.rows,
            terminalRows = 3,
            viewportPixelHeight = METRICS.cellHeight * 3,
        )

        assertEquals(0, layout.rowAt(METRICS.cellHeight - 1))
        assertEquals(1, layout.rowAt(METRICS.cellHeight))
        assertEquals(1, layout.rowAt(METRICS.cellHeight + settings.shellIntegrationPromptDividerGap - 1))
        assertEquals(1, layout.rowAt(layout.rowTop(1)))
    }

    @Test
    fun `terminal pixel y excludes divider band`() {
        val settings = SwingSettings(shellIntegrationPromptDividerGap = 6)
        val cache = TerminalRenderCache(columns = 3, rows = 3)
        cache.updateFrom(TextRowsFrame(lineIds = longArrayOf(1, 2, 3)))
        val state = TerminalShellIntegrationState()
        state.recordPromptStart(1)
        state.recordPromptStart(2)
        val decorations = TerminalShellIntegrationViewportDecorations()
        decorations.updateFrom(state, cache)
        val layout = TerminalVisualViewportGeometry()
        layout.updateLayout(
            settings = settings,
            metrics = METRICS,
            decorations = decorations,
            rows = cache.rows,
            terminalRows = 3,
            viewportPixelHeight = METRICS.cellHeight * 3,
        )

        assertEquals(METRICS.cellHeight, layout.terminalPixelY(METRICS.cellHeight, row = 1))
        assertEquals(METRICS.cellHeight, layout.terminalPixelY(layout.rowTop(1), row = 1))
        assertEquals(METRICS.cellHeight + 3, layout.terminalPixelY(layout.rowTop(1) + 3, row = 1))
    }

    @Test
    fun `component hit testing subtracts content origin before row lookup`() {
        val settings = SwingSettings(shellIntegrationPromptDividerGap = 6)
        val cache = TerminalRenderCache(columns = 3, rows = 3)
        cache.updateFrom(TextRowsFrame(lineIds = longArrayOf(1, 2, 3)))
        val state = TerminalShellIntegrationState()
        state.recordPromptStart(1)
        state.recordPromptStart(2)
        val decorations = TerminalShellIntegrationViewportDecorations()
        decorations.updateFrom(state, cache)
        val layout = TerminalVisualViewportGeometry()
        layout.updateLayout(
            settings = settings,
            metrics = METRICS,
            decorations = decorations,
            rows = cache.rows,
            terminalRows = 3,
            viewportPixelHeight = METRICS.cellHeight * 3,
        )

        assertTrue(layout.updateContentOrigin(-6.0))

        assertEquals(1, layout.rowAtComponentY(y = METRICS.cellHeight, paddingTop = 0))
        assertEquals(METRICS.cellHeight, layout.terminalPixelYAtComponentY(y = METRICS.cellHeight, paddingTop = 0))
    }

    @Test
    fun `top retained row suppresses divider because there is no previous row to separate`() {
        val settings = SwingSettings(shellIntegrationPromptDividerGap = 6)
        val cache = TerminalRenderCache(columns = 3, rows = 2)
        cache.updateFrom(TextRowsFrame(lineIds = longArrayOf(10, 11), historySize = 0, scrollbackOffset = 0))
        val state = TerminalShellIntegrationState()
        state.recordPromptStart(99)
        state.recordPromptStart(10)
        val decorations = TerminalShellIntegrationViewportDecorations()

        assertTrue(decorations.updateFrom(state, cache))

        assertFalse(decorations.hasPromptDividerAt(0))
    }

    @Test
    fun `live viewport keeps row zero divider when retained history has a previous row`() {
        val settings = SwingSettings(shellIntegrationPromptDividerGap = 6)
        val cache = TerminalRenderCache(columns = 3, rows = 2)
        cache.updateFrom(TextRowsFrame(lineIds = longArrayOf(10, 11), historySize = 5, scrollbackOffset = 0))
        val state = TerminalShellIntegrationState()
        state.recordPromptStart(99)
        state.recordPromptStart(10)
        val decorations = TerminalShellIntegrationViewportDecorations()

        assertTrue(decorations.updateFrom(state, cache))

        assertTrue(decorations.hasPromptDividerAt(0))
    }

    private class TextRowsFrame(
        private val lineIds: LongArray,
        override val historySize: Int = 0,
        override val scrollbackOffset: Int = 0,
    ) : TerminalRenderFrameReader,
        TerminalRenderFrame {
        override val columns: Int = 3
        override val rows: Int = lineIds.size
        override val frameGeneration: Long = 1
        override val structureGeneration: Long = 1
        override val activeBuffer: TerminalRenderBufferKind = TerminalRenderBufferKind.PRIMARY
        override val cursor: TerminalRenderCursor =
            TerminalRenderCursor(
                column = 0,
                row = 0,
                visible = false,
                blinking = false,
                shape = TerminalRenderCursorShape.BLOCK,
                generation = 1,
            )

        override fun readRenderFrame(consumer: TerminalRenderFrameConsumer) {
            consumer.accept(this)
        }

        override fun lineGeneration(row: Int): Long = 1

        override fun lineId(row: Int): Long = lineIds[row]

        override fun lineWrapped(row: Int): Boolean = false

        override fun copyLine(
            row: Int,
            codeWords: IntArray,
            codeOffset: Int,
            attrWords: LongArray,
            attrOffset: Int,
            flags: IntArray,
            flagOffset: Int,
            extraAttrWords: LongArray?,
            extraAttrOffset: Int,
            hyperlinkIds: IntArray?,
            hyperlinkOffset: Int,
            clusterSink: TerminalRenderClusterSink?,
            clusterDataSink: TerminalRenderClusterDataSink?,
        ) {
            var column = 0
            while (column < columns) {
                codeWords[codeOffset + column] = 0
                attrWords[attrOffset + column] = TerminalRenderAttrs.DEFAULT
                flags[flagOffset + column] = TerminalRenderCellFlags.EMPTY
                extraAttrWords?.set(extraAttrOffset + column, TerminalRenderExtraAttrs.DEFAULT)
                hyperlinkIds?.set(hyperlinkOffset + column, 0)
                column++
            }
        }
    }

    private companion object {
        private val METRICS =
            SwingMetrics(
                cellWidth = 8,
                cellHeight = 16,
                baseline = 12,
                underlineY = 13,
                strikethroughY = 8,
                overlineY = 0,
                cursorStrokeWidth = 1,
            )
    }
}
