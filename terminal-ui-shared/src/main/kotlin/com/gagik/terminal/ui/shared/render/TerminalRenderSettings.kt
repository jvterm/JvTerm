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
package com.gagik.terminal.ui.shared.render

import java.awt.Font
import java.awt.RenderingHints

/**
 * Platform-neutral Java2D rendering settings snapshot.
 *
 * @property font primary terminal font.
 * @property fallbackFonts ordered fallback fonts for complex text rendering.
 * @property useSystemFallbackFonts whether installed system fonts may be used.
 * @property textAntialiasing text antialiasing hint used during painting.
 * @property fractionalMetrics fractional metrics hint used during painting.
 * @property hyperlinkActivationForeground packed ARGB foreground used for
 * Ctrl-hover hyperlink activation affordance.
 * @property selectionBackground packed ARGB selection overlay color.
 */
data class TerminalRenderSettings(
    val font: Font,
    val fallbackFonts: List<Font>,
    val useSystemFallbackFonts: Boolean,
    val textAntialiasing: Any = RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB,
    val fractionalMetrics: Any = RenderingHints.VALUE_FRACTIONALMETRICS_OFF,
    val hyperlinkActivationForeground: Int = DEFAULT_HYPERLINK_ACTIVATION_FOREGROUND,
    val selectionBackground: Int = DEFAULT_SELECTION_BACKGROUND,
) {
    companion object {
        private const val DEFAULT_HYPERLINK_ACTIVATION_FOREGROUND = 0xFF4DA3FF.toInt()
        private const val DEFAULT_SELECTION_BACKGROUND = 0x66FFFFFF
    }
}
