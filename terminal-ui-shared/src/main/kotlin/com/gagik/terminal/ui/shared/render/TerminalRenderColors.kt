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

import com.gagik.terminal.render.api.TerminalColorPalette
import com.gagik.terminal.render.api.TerminalRenderAttrs

/**
 * Default Java2D interpretation of public terminal render colors.
 */
object TerminalRenderColors {
    /**
     * Resolves the foreground color to paint for [attrWord].
     *
     * @param palette active terminal color palette.
     * @param attrWord public render attribute word.
     * @return packed ARGB foreground color.
     */
    fun foreground(
        palette: TerminalColorPalette,
        attrWord: Long,
    ): Int {
        if (TerminalRenderAttrs.isInvisible(attrWord)) {
            return background(palette, attrWord)
        }

        val color = palette.foreground(attrWord)
        return if (TerminalRenderAttrs.isFaint(attrWord)) dim(color) else color
    }

    /**
     * Resolves the background color to paint for [attrWord].
     *
     * @param palette active terminal color palette.
     * @param attrWord public render attribute word.
     * @return packed ARGB background color.
     */
    fun background(
        palette: TerminalColorPalette,
        attrWord: Long,
    ): Int = palette.background(attrWord)

    /**
     * Applies the default faint rendering policy to a packed ARGB color.
     *
     * @param color packed ARGB color.
     * @return dimmed packed ARGB color.
     */
    fun dim(color: Int): Int {
        val alpha = color and 0xFF000000.toInt()
        val red = ((color ushr 16) and 0xFF) / 2
        val green = ((color ushr 8) and 0xFF) / 2
        val blue = (color and 0xFF) / 2
        return alpha or (red shl 16) or (green shl 8) or blue
    }
}
