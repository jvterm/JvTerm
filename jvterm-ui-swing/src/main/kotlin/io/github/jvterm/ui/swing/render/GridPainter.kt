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

import io.github.jvterm.render.api.TerminalColorPalette
import io.github.jvterm.render.cache.TerminalRenderCache
import io.github.jvterm.ui.swing.api.CellSelection
import io.github.jvterm.ui.swing.render.cache.AwtColorCache
import io.github.jvterm.ui.swing.render.painter.*
import io.github.jvterm.ui.swing.search.TerminalSearchViewportHighlights
import io.github.jvterm.ui.swing.settings.SwingMetrics
import io.github.jvterm.ui.swing.settings.SwingSettings
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Java2D renderer facade for cached primitive terminal frames.
 *
 * The facade owns renderer-local caches and delegates row backgrounds, text,
 * decorations, and cursor presentation to smaller collaborators. Callers keep a
 * single component-owned painter instance so those caches are reused across
 * paint calls.
 */
internal class GridPainter {
    private val colorCache = AwtColorCache()
    private val backgroundPainter = TerminalBackgroundPainter(colorCache)
    private val selectionPainter = TerminalSelectionPainter(colorCache)
    private val searchPainter = TerminalSearchPainter(colorCache)
    private val shellIntegrationDecorationPainter = TerminalShellIntegrationDecorationPainter(colorCache)
    private val decorationPainter = TerminalDecorationPainter(colorCache)
    private val textPainter = TerminalTextPainter(colorCache, decorationPainter)
    private val cursorPainter = TerminalCursorPainter(colorCache, textPainter)
    private val clipScratch = Rectangle()

    /**
     * Clears [width] x [height] with the terminal default background.
     */
    fun clear(
        g: Graphics2D,
        palette: TerminalColorPalette,
        width: Int,
        height: Int,
    ) {
        backgroundPainter.clear(g, palette, width, height)
    }

    /**
     * Paints [cache] into the supplied graphics context.
     */
    fun paint(
        g: Graphics2D,
        cache: TerminalRenderCache,
        settings: SwingSettings,
        metrics: SwingMetrics,
        width: Int,
        height: Int,
        cursorBlinkVisible: Boolean,
        textBlinkVisible: Boolean = true,
        cursorVisible: Boolean = true,
        contentYOffset: Double = 0.0,
        selection: CellSelection? = null,
        searchHighlights: TerminalSearchViewportHighlights? = null,
        shellIntegrationDecorations: TerminalShellIntegrationViewportDecorations? = null,
        shellIntegrationRowLayout: TerminalShellIntegrationRowLayout? = null,
        hoveredHyperlinkId: Int = 0,
        hyperlinkActivationHover: Boolean = false,
    ) {
        val palette = cache.palette
        textPainter.updateSettings(settings)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, settings.textAntialiasing)
        g.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, TEXT_LCD_CONTRAST)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, settings.fractionalMetrics)
        g.font = textPainter.font(Font.PLAIN)
        val fontRenderContext = g.fontRenderContext

        val clip = g.getClipBounds(clipScratch)
        backgroundPainter.clear(g, palette, width, height)

        val padding = settings.padding
        val rowLayout = shellIntegrationRowLayout?.takeIf { it.rowCount == cache.rows }
        val firstRow = firstPaintRow(clip, metrics, contentYOffset, padding.top, rowLayout)
        val rows = lastPaintRowExclusive(clip, cache, metrics, height, contentYOffset, padding.top, padding.bottom, rowLayout)
        g.translate(padding.left.toDouble(), padding.top.toDouble() + contentYOffset)
        var appliedPromptDividerOffset = 0
        try {
            var row = firstRow
            while (row < rows) {
                val promptDividerOffset = rowLayout?.translationForRow(row) ?: 0
                if (appliedPromptDividerOffset != promptDividerOffset) {
                    g.translate(0.0, (promptDividerOffset - appliedPromptDividerOffset).toDouble())
                    appliedPromptDividerOffset = promptDividerOffset
                }
                backgroundPainter.paintRow(g, cache, palette, metrics, row)
                shellIntegrationDecorationPainter.paint(
                    g = g,
                    settings = settings,
                    metrics = metrics,
                    decorations = shellIntegrationDecorations,
                    row = row,
                    componentWidth = width,
                    dividerBandHeight = rowLayout?.dividerBandHeight ?: 0,
                )
                searchPainter.paint(
                    g = g,
                    metrics = metrics,
                    row = row,
                    highlights = searchHighlights,
                    matchBackground = settings.searchMatchBackground,
                    activeMatchBackground = settings.searchActiveMatchBackground,
                )
                selectionPainter.paint(g, cache, metrics, row, selection, settings.selectionBackground)
                textPainter.paintRow(
                    g = g,
                    cache = cache,
                    palette = palette,
                    metrics = metrics,
                    row = row,
                    fontRenderContext = fontRenderContext,
                    textBlinkVisible = textBlinkVisible,
                    hoveredHyperlinkId = hoveredHyperlinkId,
                    hyperlinkActivationHover = hyperlinkActivationHover,
                    hyperlinkActivationForeground = settings.hyperlinkActivationForeground,
                )
                row++
            }

            val cursorPromptDividerOffset = rowLayout?.translationForRow(cache.cursorRow) ?: 0
            if (appliedPromptDividerOffset != cursorPromptDividerOffset) {
                g.translate(0.0, (cursorPromptDividerOffset - appliedPromptDividerOffset).toDouble())
                appliedPromptDividerOffset = cursorPromptDividerOffset
            }
            cursorPainter.paint(
                g,
                cache,
                palette,
                metrics,
                cursorBlinkVisible,
                textBlinkVisible,
                fontRenderContext,
                cursorVisible = cursorVisible,
            )
        } finally {
            if (appliedPromptDividerOffset != 0) {
                g.translate(0.0, -appliedPromptDividerOffset.toDouble())
            }
            g.translate(-padding.left.toDouble(), -(padding.top.toDouble() + contentYOffset))
        }
    }

    private fun firstPaintRow(
        clip: Rectangle?,
        metrics: SwingMetrics,
        contentYOffset: Double,
        paddingTop: Int,
        rowLayout: TerminalShellIntegrationRowLayout?,
    ): Int {
        if (clip == null) return 0
        if (rowLayout != null) {
            val localY = floor(clip.y.toDouble() - paddingTop.toDouble() - contentYOffset).toInt()
            return rowLayout.rowAt(localY)
        }
        return maxOf(0, floor((clip.y - paddingTop - contentYOffset) / metrics.cellHeight).toInt())
    }

    private fun lastPaintRowExclusive(
        clip: Rectangle?,
        cache: TerminalRenderCache,
        metrics: SwingMetrics,
        componentHeight: Int,
        contentYOffset: Double,
        paddingTop: Int,
        paddingBottom: Int,
        rowLayout: TerminalShellIntegrationRowLayout?,
    ): Int {
        val availableHeight = componentHeight - paddingTop - paddingBottom
        val visibleRows =
            if (rowLayout != null) {
                val localBottom = ceil(availableHeight.toDouble() - contentYOffset).toInt()
                minOf(cache.rows, rowLayout.rowAt(localBottom) + 2)
            } else {
                minOf(cache.rows, maxOf(1, availableHeight / metrics.cellHeight) + 2)
            }
        if (clip == null || clip.height <= 0) return visibleRows

        val clipBottom = clip.y + clip.height
        if (clipBottom <= 0) return 0
        val clippedRows =
            if (rowLayout != null) {
                val localBottom = ceil(clipBottom.toDouble() - paddingTop.toDouble() - contentYOffset).toInt()
                rowLayout.rowAt(localBottom) + 1
            } else {
                ceil((clipBottom - paddingTop - contentYOffset) / metrics.cellHeight).toInt()
            }
        return clippedRows.coerceIn(0, visibleRows)
    }

    private companion object {
        private const val TEXT_LCD_CONTRAST = 140
    }
}
