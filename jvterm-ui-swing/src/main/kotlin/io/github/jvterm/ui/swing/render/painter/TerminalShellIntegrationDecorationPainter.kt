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
package io.github.jvterm.ui.swing.render.painter

import io.github.jvterm.ui.swing.render.TerminalShellIntegrationViewportDecorations
import io.github.jvterm.ui.swing.render.cache.AwtColorCache
import io.github.jvterm.ui.swing.settings.SwingMetrics
import io.github.jvterm.ui.swing.settings.SwingSettings
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

/**
 * Paints shell-integration prompt dividers and failed-command side rails.
 */
internal class TerminalShellIntegrationDecorationPainter(
    private val colorCache: AwtColorCache,
) {
    private val dividerScratch = Rectangle2D.Double()

    /**
     * Paints shell-integration decorations for one visible row.
     */
    fun paint(
        g: Graphics2D,
        settings: SwingSettings,
        metrics: SwingMetrics,
        decorations: TerminalShellIntegrationViewportDecorations?,
        row: Int,
        componentWidth: Int,
    ) {
        if (decorations == null) return

        val y = row * metrics.cellHeight
        if (settings.shellIntegrationFailedCommandRailsVisible && decorations.hasFailedCommandRailAt(row)) {
            val gutterWidth = settings.shellIntegrationDecorationGutterWidth.coerceAtMost(settings.padding.left)
            if (gutterWidth > 0) {
                val width = minOf(settings.shellIntegrationFailedCommandRailWidth, gutterWidth)
                val x = -gutterWidth
                g.color = colorCache.color(settings.shellIntegrationFailedCommandRailColor)
                g.fillRect(x, y, width, metrics.cellHeight)
            }
        }

        if (settings.shellIntegrationPromptDividersVisible && decorations.hasPromptDividerAt(row)) {
            val x = -settings.padding.left
            val width = componentWidth
            g.color = colorCache.color(settings.shellIntegrationPromptDividerColor)
            dividerScratch.setRect(
                x.toDouble(),
                y.toDouble(),
                width.toDouble(),
                promptDividerUserHeight(g, settings),
            )
            g.fill(dividerScratch)
        }
    }

    private fun promptDividerUserHeight(
        g: Graphics2D,
        settings: SwingSettings,
    ): Double {
        val scaleY = g.transform.scaleY
        val physicalPixelHeight = if (scaleY > 0.0) 1.0 / scaleY else 1.0
        return minOf(settings.shellIntegrationPromptDividerThickness.toDouble(), physicalPixelHeight).coerceAtLeast(MIN_USER_HEIGHT)
    }

    private companion object {
        private const val MIN_USER_HEIGHT = 0.05
    }
}
