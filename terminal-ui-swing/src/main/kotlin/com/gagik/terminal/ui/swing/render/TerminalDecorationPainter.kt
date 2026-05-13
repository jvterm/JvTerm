package com.gagik.terminal.ui.swing.render

import com.gagik.terminal.render.api.TerminalRenderAttrs
import com.gagik.terminal.render.api.TerminalRenderColorKind
import com.gagik.terminal.render.api.TerminalRenderExtraAttrs
import com.gagik.terminal.render.api.TerminalRenderUnderline
import com.gagik.terminal.ui.swing.settings.TerminalColorPalette
import com.gagik.terminal.ui.swing.settings.TerminalSwingMetrics
import java.awt.Graphics2D

/**
 * Paints text decorations exported through render attribute words.
 */
internal class TerminalDecorationPainter(
    private val colorCache: AwtColorCache,
) {
    /**
     * Paints underline, strikethrough, and overline for a contiguous cell span.
     */
    fun paint(
        g: Graphics2D,
        palette: TerminalColorPalette,
        attr: Long,
        extraAttr: Long,
        foreground: Int,
        startColumn: Int,
        endColumn: Int,
        row: Int,
        metrics: TerminalSwingMetrics,
    ) {
        val underline = TerminalRenderAttrs.underlineStyle(attr)
        val strikethrough = TerminalRenderAttrs.isStrikethrough(attr)
        val overline = TerminalRenderExtraAttrs.isOverline(extraAttr)
        if (underline == TerminalRenderUnderline.NONE && !strikethrough && !overline) return

        val x = startColumn * metrics.cellWidth
        val width = (endColumn - startColumn) * metrics.cellWidth
        val rowY = row * metrics.cellHeight

        if (underline != TerminalRenderUnderline.NONE) {
            g.color = colorCache.color(underlineColor(palette, extraAttr, foreground))
            val y = rowY + metrics.underlineY
            g.drawLine(x, y, x + width, y)
            if (underline == TerminalRenderUnderline.DOUBLE) {
                val secondY = minOf(rowY + metrics.cellHeight - 1, y + DOUBLE_UNDERLINE_OFFSET)
                g.drawLine(x, secondY, x + width, secondY)
            }
        }

        if (strikethrough) {
            g.color = colorCache.color(foreground)
            val y = rowY + metrics.strikethroughY
            g.drawLine(x, y, x + width, y)
        }

        if (overline) {
            g.color = colorCache.color(foreground)
            val y = rowY + metrics.overlineY
            g.drawLine(x, y, x + width, y)
        }
    }

    private fun underlineColor(
        palette: TerminalColorPalette,
        extraAttr: Long,
        defaultColor: Int,
    ): Int {
        val value = TerminalRenderExtraAttrs.underlineColorValue(extraAttr)
        return when (TerminalRenderExtraAttrs.underlineColorKind(extraAttr)) {
            TerminalRenderColorKind.DEFAULT -> defaultColor
            TerminalRenderColorKind.INDEXED -> palette.indexedColor(value)
            TerminalRenderColorKind.RGB -> 0xFF000000.toInt() or value
            else -> defaultColor
        }
    }

    private companion object {
        private const val DOUBLE_UNDERLINE_OFFSET = 2
    }
}
