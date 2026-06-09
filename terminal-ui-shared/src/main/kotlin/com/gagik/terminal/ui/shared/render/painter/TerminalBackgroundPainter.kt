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
package com.gagik.terminal.ui.shared.render.painter

import com.gagik.terminal.ui.shared.api.TerminalPlatformDriver
import com.gagik.terminal.ui.shared.api.TerminalRenderSnapshot
import com.gagik.terminal.ui.shared.render.TerminalRenderMetrics
import java.awt.Graphics2D

/**
 * Paints terminal default clears and row background runs.
 */
internal class TerminalBackgroundPainter(
    private val driver: TerminalPlatformDriver,
) {
    /**
     * Clears a rectangular component area with the palette default background.
     */
    fun clear(
        g: Graphics2D,
        width: Int,
        height: Int,
    ) {
        g.color = driver.resolveColor(DEFAULT_ATTR_WORD, isBackground = true)
        g.fillRect(0, 0, width, height)
    }

    /**
     * Paints contiguous background runs for [row].
     */
    fun paintRow(
        g: Graphics2D,
        cache: TerminalRenderSnapshot,
        metrics: TerminalRenderMetrics,
        row: Int,
    ) {
        val attrWords = cache.attrWords
        val rowOffset = cache.rowOffset(row)
        val y = row * metrics.cellHeight
        var column = 0
        while (column < cache.columns) {
            val background = driver.resolveColor(attrWords[rowOffset + column], isBackground = true)
            val backgroundArgb = background.rgb
            val start = column

            column++
            while (
                column < cache.columns &&
                driver.resolveColor(attrWords[rowOffset + column], isBackground = true).rgb == backgroundArgb
            ) {
                column++
            }

            g.color = background
            g.fillRect(
                start * metrics.cellWidth,
                y,
                (column - start) * metrics.cellWidth,
                metrics.cellHeight,
            )
        }
    }

    private companion object {
        private const val DEFAULT_ATTR_WORD = 0L
    }
}
