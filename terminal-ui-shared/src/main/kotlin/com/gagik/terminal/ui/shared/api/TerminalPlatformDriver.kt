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
package com.gagik.terminal.ui.shared.api

import java.awt.Color

/**
 * Host capability driver used by the shared Java2D terminal painter.
 *
 * Implementations are owned by the embedding platform, such as the standalone
 * Swing application or an IntelliJ Platform adapter. Rendering calls
 * [resolveColor] while traversing visible cells, so implementations must return
 * cached [Color] instances and avoid allocation on cache hits.
 */
interface TerminalPlatformDriver {
    /**
     * Resolves a public render attribute word into a concrete AWT color.
     *
     * @param packedColorWord public terminal render attribute word.
     * @param isBackground whether the background channel should be resolved.
     * @return cached AWT color for the active host palette.
     */
    fun resolveColor(
        packedColorWord: Long,
        isBackground: Boolean,
    ): Color

    /**
     * Schedules [runnable] on the host event thread.
     *
     * @param runnable task to execute on the host UI/event thread.
     */
    fun invokeLaterOnEventThread(runnable: Runnable)

    /**
     * Copies [text] to the host clipboard.
     *
     * @param text string selected by terminal UI code.
     */
    fun copyToClipboard(text: String)

    /**
     * Reads text from the host clipboard.
     *
     * @return clipboard text, or `null` when no string value is available.
     */
    fun pasteFromClipboard(): String?
}
