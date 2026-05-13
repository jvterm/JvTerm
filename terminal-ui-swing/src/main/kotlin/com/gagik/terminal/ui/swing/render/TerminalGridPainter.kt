package com.gagik.terminal.ui.swing.render

import com.gagik.terminal.render.cache.TerminalRenderCache
import com.gagik.terminal.ui.swing.settings.TerminalColorPalette
import com.gagik.terminal.ui.swing.settings.TerminalSwingMetrics
import com.gagik.terminal.ui.swing.settings.TerminalSwingSettings
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints

/**
 * Java2D renderer facade for cached primitive terminal frames.
 *
 * The facade owns renderer-local caches and delegates row backgrounds, text,
 * decorations, and cursor presentation to smaller collaborators. Callers keep a
 * single component-owned painter instance so those caches are reused across
 * paint calls.
 */
internal class TerminalGridPainter {
    private val colorCache = AwtColorCache()
    private val backgroundPainter = TerminalBackgroundPainter(colorCache)
    private val decorationPainter = TerminalDecorationPainter(colorCache)
    private val textPainter = TerminalTextPainter(colorCache, decorationPainter)
    private val cursorPainter = TerminalCursorPainter(colorCache, textPainter)

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
        settings: TerminalSwingSettings,
        metrics: TerminalSwingMetrics,
        width: Int,
        height: Int,
        cursorBlinkVisible: Boolean,
    ) {
        val palette = settings.palette
        textPainter.updateSettings(settings)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, settings.textAntialiasing)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, settings.fractionalMetrics)
        g.font = textPainter.font(Font.PLAIN)
        val fontRenderContext = g.fontRenderContext

        val rows = minOf(cache.rows, height / metrics.cellHeight + 1)
        backgroundPainter.clear(g, palette, width, height)

        var row = 0
        while (row < rows) {
            backgroundPainter.paintRow(g, cache, palette, metrics, row)
            textPainter.paintRow(g, cache, palette, metrics, row, fontRenderContext)
            row++
        }

        cursorPainter.paint(g, cache, palette, metrics, cursorBlinkVisible, fontRenderContext)
    }
}
