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

import com.gagik.terminal.render.api.TerminalColorPalette
import com.gagik.terminal.ui.shared.api.TerminalPlatformDriver
import com.gagik.terminal.ui.shared.render.TerminalRenderColors
import com.gagik.terminal.ui.shared.render.cache.AwtColorCache
import com.gagik.terminal.ui.swing.settings.TerminalSwingSettings
import java.awt.Color
import javax.swing.SwingUtilities

/**
 * Swing host implementation of shared terminal rendering capabilities.
 */
internal class SwingTerminalPlatformDriver {
    private val colorCache = AwtColorCache()
    private var palette: TerminalColorPalette = TerminalSwingSettings.defaultPalette()
    private var settings: TerminalSwingSettings = TerminalSwingSettings()

    /**
     * Publishes the active Swing settings for clipboard access.
     */
    fun updateSettings(settings: TerminalSwingSettings) {
        this.settings = settings
        this.palette = settings.palette
    }

    /**
     * Publishes the active frame palette for cell color resolution.
     */
    fun updatePalette(palette: TerminalColorPalette) {
        this.palette = palette
    }

    val driver: TerminalPlatformDriver =
        object : TerminalPlatformDriver {
            override fun resolveColor(
                packedColorWord: Long,
                isBackground: Boolean,
            ): Color {
                val argb =
                    if (isBackground) {
                        TerminalRenderColors.background(palette, packedColorWord)
                    } else {
                        TerminalRenderColors.foreground(palette, packedColorWord)
                    }
                return colorCache.color(argb)
            }

            override fun invokeLaterOnEventThread(runnable: Runnable) {
                SwingUtilities.invokeLater(runnable)
            }

            override fun copyToClipboard(text: String) {
                settings.clipboardHandler.copyText(text)
            }

            override fun pasteFromClipboard(): String? = settings.clipboardHandler.readText()
        }
}
