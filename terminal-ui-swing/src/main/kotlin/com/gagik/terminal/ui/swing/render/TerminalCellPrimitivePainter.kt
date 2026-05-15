package com.gagik.terminal.ui.swing.render

import com.gagik.terminal.ui.swing.settings.TerminalSwingMetrics
import java.awt.Graphics2D

/**
 * Paints terminal cell-native glyphs whose font outlines commonly fail to align
 * with terminal cell edges.
 */
internal class TerminalCellPrimitivePainter {
    /**
     * Returns true when [codePoint] is handled by this primitive painter.
     */
    fun canPaint(codePoint: Int): Boolean {
        return codePoint in 0x2500..0x257F || codePoint in 0x2580..0x259F
    }

    /**
     * Paints one supported cell-native glyph.
     */
    fun paint(
        g: Graphics2D,
        codePoint: Int,
        column: Int,
        row: Int,
        metrics: TerminalSwingMetrics,
    ) {
        val x = column * metrics.cellWidth
        val y = row * metrics.cellHeight
        when (codePoint) {
            in 0x2500..0x257F -> paintBoxDrawing(g, codePoint, x, y, metrics.cellWidth, metrics.cellHeight)
            in 0x2580..0x259F -> paintBlockElement(g, codePoint, x, y, metrics.cellWidth, metrics.cellHeight)
        }
    }

    private fun paintBoxDrawing(
        g: Graphics2D,
        codePoint: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        when (codePoint) {
            0x2504, 0x2508 -> {
                paintDashedHorizontal(g, x, y, width, height, LIGHT)
                return
            }
            0x2505, 0x2509 -> {
                paintDashedHorizontal(g, x, y, width, height, HEAVY)
                return
            }
            0x2506, 0x250A -> {
                paintDashedVertical(g, x, y, width, height, LIGHT)
                return
            }
            0x2507, 0x250B -> {
                paintDashedVertical(g, x, y, width, height, HEAVY)
                return
            }
            0x254C -> {
                paintDashedHorizontal(g, x, y, width, height, LIGHT)
                return
            }
            0x254D -> {
                paintDashedHorizontal(g, x, y, width, height, HEAVY)
                return
            }
            0x254E -> {
                paintDashedVertical(g, x, y, width, height, LIGHT)
                return
            }
            0x254F -> {
                paintDashedVertical(g, x, y, width, height, HEAVY)
                return
            }
            0x2571 -> {
                g.drawLine(x, y + height - 1, x + width - 1, y)
                return
            }
            0x2572 -> {
                g.drawLine(x, y, x + width - 1, y + height - 1)
                return
            }
            0x2573 -> {
                g.drawLine(x, y + height - 1, x + width - 1, y)
                g.drawLine(x, y, x + width - 1, y + height - 1)
                return
            }
        }

        val packed = boxDrawingEdges(codePoint)
        if (packed == NONE) return

        val left = edge(packed, LEFT_SHIFT)
        val right = edge(packed, RIGHT_SHIFT)
        val up = edge(packed, UP_SHIFT)
        val down = edge(packed, DOWN_SHIFT)

        paintHorizontal(g, x, y, width, height, left, left = true)
        paintHorizontal(g, x, y, width, height, right, left = false)
        paintVertical(g, x, y, width, height, up, up = true)
        paintVertical(g, x, y, width, height, down, up = false)
        paintMixedDoubleBridges(g, x, y, width, height, left, right, up, down)
    }

    private fun paintHorizontal(
        g: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        style: Int,
        left: Boolean,
    ) {
        if (style == NONE) return
        val centerX = x + width / 2
        val startX = if (left) x else centerX - thickness(style, width, height) / 2
        val endX = if (left) centerX + (thickness(style, width, height) + 1) / 2 else x + width
        if (style == DOUBLE) {
            val thin = thin(width, height)
            val offset = doubleOffset(width, height)
            fillHorizontal(g, startX, endX, y + height / 2 - offset, thin)
            fillHorizontal(g, startX, endX, y + height / 2 + offset, thin)
        } else {
            val lineThickness = thickness(style, width, height)
            fillHorizontal(g, startX, endX, y + height / 2, lineThickness)
        }
    }

    private fun paintVertical(
        g: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        style: Int,
        up: Boolean,
    ) {
        if (style == NONE) return
        val centerY = y + height / 2
        val startY = if (up) y else centerY - thickness(style, width, height) / 2
        val endY = if (up) centerY + (thickness(style, width, height) + 1) / 2 else y + height
        if (style == DOUBLE) {
            val thin = thin(width, height)
            val offset = doubleOffset(width, height)
            fillVertical(g, x + width / 2 - offset, startY, endY, thin)
            fillVertical(g, x + width / 2 + offset, startY, endY, thin)
        } else {
            val lineThickness = thickness(style, width, height)
            fillVertical(g, x + width / 2, startY, endY, lineThickness)
        }
    }

    private fun fillHorizontal(g: Graphics2D, startX: Int, endX: Int, centerY: Int, thickness: Int) {
        g.fillRect(startX, centerY - thickness / 2, maxOf(1, endX - startX), thickness)
    }

    private fun fillVertical(g: Graphics2D, centerX: Int, startY: Int, endY: Int, thickness: Int) {
        g.fillRect(centerX - thickness / 2, startY, thickness, maxOf(1, endY - startY))
    }

    private fun paintMixedDoubleBridges(
        g: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        left: Int,
        right: Int,
        up: Int,
        down: Int,
    ) {
        val horizontalHasDouble = left == DOUBLE || right == DOUBLE
        val verticalHasDouble = up == DOUBLE || down == DOUBLE
        val horizontalAny = left != NONE || right != NONE
        val verticalAny = up != NONE || down != NONE
        val offset = doubleOffset(width, height)

        if (horizontalHasDouble && verticalAny && !verticalHasDouble) {
            val style = stronger(up, down)
            fillVertical(
                g = g,
                centerX = x + width / 2,
                startY = y + height / 2 - offset,
                endY = y + height / 2 + offset + thin(width, height),
                thickness = thickness(style, width, height),
            )
        }

        if (verticalHasDouble && horizontalAny && !horizontalHasDouble) {
            val style = stronger(left, right)
            fillHorizontal(
                g = g,
                startX = x + width / 2 - offset,
                endX = x + width / 2 + offset + thin(width, height),
                centerY = y + height / 2,
                thickness = thickness(style, width, height),
            )
        }
    }

    private fun paintDashedHorizontal(
        g: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        style: Int,
    ) {
        val lineThickness = thickness(style, width, height)
        val centerY = y + height / 2
        val dash = maxOf(1, width / 3)
        fillHorizontal(g, x, minOf(x + width, x + dash), centerY, lineThickness)
        fillHorizontal(g, maxOf(x, x + width - dash), x + width, centerY, lineThickness)
    }

    private fun paintDashedVertical(
        g: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        style: Int,
    ) {
        val lineThickness = thickness(style, width, height)
        val centerX = x + width / 2
        val dash = maxOf(1, height / 3)
        fillVertical(g, centerX, y, minOf(y + height, y + dash), lineThickness)
        fillVertical(g, centerX, maxOf(y, y + height - dash), y + height, lineThickness)
    }

    private fun paintBlockElement(
        g: Graphics2D,
        codePoint: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        when (codePoint) {
            0x2580 -> paintUpperBlock(g, x, y, width, height, 4)
            in 0x2581..0x2587 -> paintLowerBlock(g, x, y, width, height, codePoint - 0x2580)
            0x2588 -> g.fillRect(x, y, width, height)
            in 0x2589..0x258F -> paintLeftBlock(g, x, y, width, height, 8 - (codePoint - 0x2588))
            0x2590 -> paintRightBlock(g, x, y, width, height, 4)
            0x2594 -> paintUpperBlock(g, x, y, width, height, 1)
            0x2595 -> paintRightBlock(g, x, y, width, height, 1)
            0x2596 -> paintQuadrants(g, x, y, width, height, lowerLeft = true)
            0x2597 -> paintQuadrants(g, x, y, width, height, lowerRight = true)
            0x2598 -> paintQuadrants(g, x, y, width, height, upperLeft = true)
            0x2599 -> paintQuadrants(g, x, y, width, height, upperLeft = true, lowerLeft = true, lowerRight = true)
            0x259A -> paintQuadrants(g, x, y, width, height, upperLeft = true, lowerRight = true)
            0x259B -> paintQuadrants(g, x, y, width, height, upperLeft = true, upperRight = true, lowerLeft = true)
            0x259C -> paintQuadrants(g, x, y, width, height, upperLeft = true, upperRight = true, lowerRight = true)
            0x259D -> paintQuadrants(g, x, y, width, height, upperRight = true)
            0x259E -> paintQuadrants(g, x, y, width, height, upperRight = true, lowerLeft = true)
            0x259F -> paintQuadrants(g, x, y, width, height, upperRight = true, lowerLeft = true, lowerRight = true)
            0x2591 -> paintShade(g, x, y, width, height, step = 4)
            0x2592 -> paintShade(g, x, y, width, height, step = 2)
            0x2593 -> paintDarkShade(g, x, y, width, height)
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

    private fun paintQuadrants(
        g: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        upperLeft: Boolean = false,
        upperRight: Boolean = false,
        lowerLeft: Boolean = false,
        lowerRight: Boolean = false,
    ) {
        val halfWidth = width / 2
        val halfHeight = height / 2
        if (upperLeft) g.fillRect(x, y, halfWidth, halfHeight)
        if (upperRight) g.fillRect(x + halfWidth, y, width - halfWidth, halfHeight)
        if (lowerLeft) g.fillRect(x, y + halfHeight, halfWidth, height - halfHeight)
        if (lowerRight) g.fillRect(x + halfWidth, y + halfHeight, width - halfWidth, height - halfHeight)
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

    private fun thin(width: Int, height: Int): Int {
        return maxOf(1, minOf(width, height) / 8)
    }

    private fun thickness(style: Int, width: Int, height: Int): Int {
        return when (style) {
            HEAVY -> maxOf(lightThickness(width, height) + 1, minOf(width, height) / 4)
            DOUBLE -> thin(width, height)
            else -> lightThickness(width, height)
        }
    }

    private fun lightThickness(width: Int, height: Int): Int {
        val minimum = minOf(width, height)
        return if (minimum >= 8) 2 else 1
    }

    private fun doubleOffset(width: Int, height: Int): Int {
        return maxOf(1, minOf(width, height) / 5)
    }

    private fun edge(packed: Int, shift: Int): Int {
        return packed ushr shift and STYLE_MASK
    }

    private fun stronger(first: Int, second: Int): Int {
        return maxOf(first, second).coerceAtLeast(LIGHT)
    }

    private fun boxDrawingEdges(codePoint: Int): Int {
        return when (codePoint) {
            0x2500 -> pack(LIGHT, LIGHT, NONE, NONE)
            0x2501 -> pack(HEAVY, HEAVY, NONE, NONE)
            0x2502 -> pack(NONE, NONE, LIGHT, LIGHT)
            0x2503 -> pack(NONE, NONE, HEAVY, HEAVY)
            0x250C, 0x256D -> pack(NONE, LIGHT, NONE, LIGHT)
            0x250D -> pack(NONE, HEAVY, NONE, LIGHT)
            0x250E -> pack(NONE, LIGHT, NONE, HEAVY)
            0x250F -> pack(NONE, HEAVY, NONE, HEAVY)
            0x2510, 0x256E -> pack(LIGHT, NONE, NONE, LIGHT)
            0x2511 -> pack(HEAVY, NONE, NONE, LIGHT)
            0x2512 -> pack(LIGHT, NONE, NONE, HEAVY)
            0x2513 -> pack(HEAVY, NONE, NONE, HEAVY)
            0x2514, 0x2570 -> pack(NONE, LIGHT, LIGHT, NONE)
            0x2515 -> pack(NONE, HEAVY, LIGHT, NONE)
            0x2516 -> pack(NONE, LIGHT, HEAVY, NONE)
            0x2517 -> pack(NONE, HEAVY, HEAVY, NONE)
            0x2518, 0x256F -> pack(LIGHT, NONE, LIGHT, NONE)
            0x2519 -> pack(HEAVY, NONE, LIGHT, NONE)
            0x251A -> pack(LIGHT, NONE, HEAVY, NONE)
            0x251B -> pack(HEAVY, NONE, HEAVY, NONE)
            0x251C -> pack(NONE, LIGHT, LIGHT, LIGHT)
            0x251D -> pack(NONE, HEAVY, LIGHT, LIGHT)
            0x251E -> pack(NONE, LIGHT, HEAVY, LIGHT)
            0x251F -> pack(NONE, LIGHT, LIGHT, HEAVY)
            0x2520 -> pack(NONE, LIGHT, HEAVY, HEAVY)
            0x2521 -> pack(NONE, HEAVY, HEAVY, LIGHT)
            0x2522 -> pack(NONE, HEAVY, LIGHT, HEAVY)
            0x2523 -> pack(NONE, HEAVY, HEAVY, HEAVY)
            0x2524 -> pack(LIGHT, NONE, LIGHT, LIGHT)
            0x2525 -> pack(HEAVY, NONE, LIGHT, LIGHT)
            0x2526 -> pack(LIGHT, NONE, HEAVY, LIGHT)
            0x2527 -> pack(LIGHT, NONE, LIGHT, HEAVY)
            0x2528 -> pack(LIGHT, NONE, HEAVY, HEAVY)
            0x2529 -> pack(HEAVY, NONE, HEAVY, LIGHT)
            0x252A -> pack(HEAVY, NONE, LIGHT, HEAVY)
            0x252B -> pack(HEAVY, NONE, HEAVY, HEAVY)
            0x252C -> pack(LIGHT, LIGHT, NONE, LIGHT)
            0x252D -> pack(HEAVY, LIGHT, NONE, LIGHT)
            0x252E -> pack(LIGHT, HEAVY, NONE, LIGHT)
            0x252F -> pack(HEAVY, HEAVY, NONE, LIGHT)
            0x2530 -> pack(LIGHT, LIGHT, NONE, HEAVY)
            0x2531 -> pack(HEAVY, LIGHT, NONE, HEAVY)
            0x2532 -> pack(LIGHT, HEAVY, NONE, HEAVY)
            0x2533 -> pack(HEAVY, HEAVY, NONE, HEAVY)
            0x2534 -> pack(LIGHT, LIGHT, LIGHT, NONE)
            0x2535 -> pack(HEAVY, LIGHT, LIGHT, NONE)
            0x2536 -> pack(LIGHT, HEAVY, LIGHT, NONE)
            0x2537 -> pack(HEAVY, HEAVY, LIGHT, NONE)
            0x2538 -> pack(LIGHT, LIGHT, HEAVY, NONE)
            0x2539 -> pack(HEAVY, LIGHT, HEAVY, NONE)
            0x253A -> pack(LIGHT, HEAVY, HEAVY, NONE)
            0x253B -> pack(HEAVY, HEAVY, HEAVY, NONE)
            0x253C -> pack(LIGHT, LIGHT, LIGHT, LIGHT)
            0x253D -> pack(HEAVY, LIGHT, LIGHT, LIGHT)
            0x253E -> pack(LIGHT, HEAVY, LIGHT, LIGHT)
            0x253F -> pack(HEAVY, HEAVY, LIGHT, LIGHT)
            0x2540 -> pack(LIGHT, LIGHT, HEAVY, LIGHT)
            0x2541 -> pack(LIGHT, LIGHT, LIGHT, HEAVY)
            0x2542 -> pack(LIGHT, LIGHT, HEAVY, HEAVY)
            0x2543 -> pack(HEAVY, LIGHT, HEAVY, LIGHT)
            0x2544 -> pack(LIGHT, HEAVY, HEAVY, LIGHT)
            0x2545 -> pack(HEAVY, LIGHT, LIGHT, HEAVY)
            0x2546 -> pack(LIGHT, HEAVY, LIGHT, HEAVY)
            0x2547 -> pack(HEAVY, HEAVY, HEAVY, LIGHT)
            0x2548 -> pack(HEAVY, HEAVY, LIGHT, HEAVY)
            0x2549 -> pack(HEAVY, LIGHT, HEAVY, HEAVY)
            0x254A -> pack(LIGHT, HEAVY, HEAVY, HEAVY)
            0x254B -> pack(HEAVY, HEAVY, HEAVY, HEAVY)
            0x2550 -> pack(DOUBLE, DOUBLE, NONE, NONE)
            0x2551 -> pack(NONE, NONE, DOUBLE, DOUBLE)
            0x2552 -> pack(NONE, DOUBLE, NONE, LIGHT)
            0x2553 -> pack(NONE, LIGHT, NONE, DOUBLE)
            0x2554 -> pack(NONE, DOUBLE, NONE, DOUBLE)
            0x2555 -> pack(DOUBLE, NONE, NONE, LIGHT)
            0x2556 -> pack(LIGHT, NONE, NONE, DOUBLE)
            0x2557 -> pack(DOUBLE, NONE, NONE, DOUBLE)
            0x2558 -> pack(NONE, DOUBLE, LIGHT, NONE)
            0x2559 -> pack(NONE, LIGHT, DOUBLE, NONE)
            0x255A -> pack(NONE, DOUBLE, DOUBLE, NONE)
            0x255B -> pack(DOUBLE, NONE, LIGHT, NONE)
            0x255C -> pack(LIGHT, NONE, DOUBLE, NONE)
            0x255D -> pack(DOUBLE, NONE, DOUBLE, NONE)
            0x255E -> pack(NONE, DOUBLE, LIGHT, LIGHT)
            0x255F -> pack(NONE, LIGHT, DOUBLE, DOUBLE)
            0x2560 -> pack(NONE, DOUBLE, DOUBLE, DOUBLE)
            0x2561 -> pack(DOUBLE, NONE, LIGHT, LIGHT)
            0x2562 -> pack(LIGHT, NONE, DOUBLE, DOUBLE)
            0x2563 -> pack(DOUBLE, NONE, DOUBLE, DOUBLE)
            0x2564 -> pack(DOUBLE, DOUBLE, NONE, LIGHT)
            0x2565 -> pack(LIGHT, LIGHT, NONE, DOUBLE)
            0x2566 -> pack(DOUBLE, DOUBLE, NONE, DOUBLE)
            0x2567 -> pack(DOUBLE, DOUBLE, LIGHT, NONE)
            0x2568 -> pack(LIGHT, LIGHT, DOUBLE, NONE)
            0x2569 -> pack(DOUBLE, DOUBLE, DOUBLE, NONE)
            0x256A -> pack(DOUBLE, DOUBLE, LIGHT, LIGHT)
            0x256B -> pack(LIGHT, LIGHT, DOUBLE, DOUBLE)
            0x256C -> pack(DOUBLE, DOUBLE, DOUBLE, DOUBLE)
            0x2574 -> pack(LIGHT, NONE, NONE, NONE)
            0x2575 -> pack(NONE, NONE, LIGHT, NONE)
            0x2576 -> pack(NONE, LIGHT, NONE, NONE)
            0x2577 -> pack(NONE, NONE, NONE, LIGHT)
            0x2578 -> pack(HEAVY, NONE, NONE, NONE)
            0x2579 -> pack(NONE, NONE, HEAVY, NONE)
            0x257A -> pack(NONE, HEAVY, NONE, NONE)
            0x257B -> pack(NONE, NONE, NONE, HEAVY)
            0x257C -> pack(HEAVY, LIGHT, NONE, NONE)
            0x257D -> pack(NONE, NONE, HEAVY, LIGHT)
            0x257E -> pack(LIGHT, HEAVY, NONE, NONE)
            0x257F -> pack(NONE, NONE, LIGHT, HEAVY)
            else -> NONE
        }
    }

    private fun pack(left: Int, right: Int, up: Int, down: Int): Int {
        return left or (right shl RIGHT_SHIFT) or (up shl UP_SHIFT) or (down shl DOWN_SHIFT)
    }

    private companion object {
        private const val NONE = 0
        private const val LIGHT = 1
        private const val HEAVY = 2
        private const val DOUBLE = 3
        private const val STYLE_MASK = 0x3
        private const val LEFT_SHIFT = 0
        private const val RIGHT_SHIFT = 2
        private const val UP_SHIFT = 4
        private const val DOWN_SHIFT = 6
    }
}
