package com.gagik.terminal.ui.swing.render.primitives

import com.gagik.terminal.ui.swing.render.primitives.TerminalBlockElementGlyphs.LOWER_LEFT
import com.gagik.terminal.ui.swing.render.primitives.TerminalBlockElementGlyphs.LOWER_RIGHT
import com.gagik.terminal.ui.swing.render.primitives.TerminalBlockElementGlyphs.UPPER_LEFT
import com.gagik.terminal.ui.swing.render.primitives.TerminalBlockElementGlyphs.UPPER_RIGHT
import java.awt.Graphics2D

/**
 * Paints Unicode block element glyphs.
 */
internal class TerminalBlockElementPainter {
    fun paint(g: Graphics2D, codePoint: Int, x: Int, y: Int, width: Int, height: Int) {
        when (codePoint) {
            0x2580 -> paintUpperBlock(g, x, y, width, height, 4)
            in 0x2581..0x2587 -> paintLowerBlock(g, x, y, width, height, codePoint - 0x2580)
            0x2588 -> g.fillRect(x, y, width, height)
            in 0x2589..0x258F -> paintLeftBlock(g, x, y, width, height, 8 - (codePoint - 0x2588))
            0x2590 -> paintRightBlock(g, x, y, width, height, 4)
            0x2594 -> paintUpperBlock(g, x, y, width, height, 1)
            0x2595 -> paintRightBlock(g, x, y, width, height, 1)
            0x2591 -> paintShade(g, x, y, width, height, step = 4)
            0x2592 -> paintShade(g, x, y, width, height, step = 2)
            0x2593 -> paintDarkShade(g, x, y, width, height)
            else -> {
                val mask = TerminalBlockElementGlyphs.quadrantMask(codePoint)
                if (mask != 0) paintQuadrants(g, x, y, width, height, mask)
            }
        }
    }

    private fun paintLowerBlock(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, eighths: Int) {
        val blockHeight = maxOf(1, height * eighths / 8)
        g.fillRect(x, y + height - blockHeight, width, blockHeight)
    }

    private fun paintUpperBlock(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, eighths: Int) {
        g.fillRect(x, y, width, maxOf(1, height * eighths / 8))
    }

    private fun paintLeftBlock(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, eighths: Int) {
        g.fillRect(x, y, maxOf(1, width * eighths / 8), height)
    }

    private fun paintRightBlock(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, eighths: Int) {
        val blockWidth = maxOf(1, width * eighths / 8)
        g.fillRect(x + width - blockWidth, y, blockWidth, height)
    }

    private fun paintQuadrants(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, mask: Int) {
        val halfWidth = width / 2
        val halfHeight = height / 2
        if (mask and UPPER_LEFT != 0) g.fillRect(x, y, halfWidth, halfHeight)
        if (mask and UPPER_RIGHT != 0) g.fillRect(x + halfWidth, y, width - halfWidth, halfHeight)
        if (mask and LOWER_LEFT != 0) g.fillRect(x, y + halfHeight, halfWidth, height - halfHeight)
        if (mask and LOWER_RIGHT != 0) {
            g.fillRect(x + halfWidth, y + halfHeight, width - halfWidth, height - halfHeight)
        }
    }

    private fun paintShade(g: Graphics2D, x: Int, y: Int, width: Int, height: Int, step: Int) {
        var row = 0
        while (row < height) {
            var column = (row and 1) * (step / 2)
            while (column < width) {
                g.fillRect(x + column, y + row, 1, 1)
                column += step
            }
            row++
        }
    }

    private fun paintDarkShade(g: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
        var row = 0
        while (row < height) {
            var column = 0
            while (column < width) {
                if ((row + column) and 0x3 != 0) {
                    g.fillRect(x + column, y + row, 1, 1)
                }
                column++
            }
            row++
        }
    }
}
