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
package io.github.jvterm.intellij.settings

import com.intellij.util.ui.JBFont
import io.github.jvterm.ui.swing.settings.SwingSettings

/**
 * Provides immutable Swing terminal settings for the IntelliJ plugin host.
 *
 * The first plugin slice intentionally keeps settings static. A later IDE
 * settings bridge can replace this object with a persisted project/application
 * service while preserving the same [SwingSettings] snapshot contract.
 */
internal object JvTermIntellijSettings {
    /**
     * Returns the current terminal rendering and input settings snapshot.
     *
     * @return immutable settings consumed by `SwingTerminal`.
     */
    fun current(): SwingSettings =
        SwingSettings(
            font = JBFont.create(SwingSettings.defaultTerminalFont()),
            padding = java.awt.Insets(8, 8, 8, 8),
            shellRequestResizeWindow = false,
            shellRequestWindowManipulation = false,
        )
}
