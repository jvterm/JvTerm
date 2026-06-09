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

import com.gagik.terminal.ui.shared.render.TerminalRenderSettings
import com.gagik.terminal.ui.swing.settings.TerminalSwingSettings

/**
 * Converts host-rich Swing settings into shared Java2D rendering settings.
 */
internal fun TerminalSwingSettings.toRenderSettings(): TerminalRenderSettings =
    TerminalRenderSettings(
        font = font,
        fallbackFonts = fallbackFonts,
        useSystemFallbackFonts = useSystemFallbackFonts,
        textAntialiasing = textAntialiasing,
        fractionalMetrics = fractionalMetrics,
        hyperlinkActivationForeground = hyperlinkActivationForeground,
        selectionBackground = selectionBackground,
    )
