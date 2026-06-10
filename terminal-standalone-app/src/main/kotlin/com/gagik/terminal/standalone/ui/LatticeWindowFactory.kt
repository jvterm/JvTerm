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
package com.gagik.terminal.standalone.ui

import com.gagik.terminal.standalone.config.StandaloneTerminalSettings
import com.gagik.terminal.standalone.profile.StandaloneTerminalProfile
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Creates and wires the standalone terminal window chrome.
 */
internal class LatticeWindowFactory(
    private val settings: StandaloneTerminalSettings,
    private val profiles: List<StandaloneTerminalProfile>,
) {
    private val tabPane = JTabbedPane()

    fun createWindow(): LatticeWindow {
        val frame =
            JFrame(LatticeChrome.APP_TITLE).apply {
                defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
                contentPane = windowPanel()
                background = LatticeChrome.SURFACE
                minimumSize = Dimension(720, 420)
                rootPane.putClientProperty("JRootPane.titleBarBackground", LatticeChrome.SURFACE_RAISED)
                rootPane.putClientProperty("JRootPane.titleBarForeground", LatticeChrome.TITLE_FOREGROUND)
            }
        val tabManager = LatticeTabManager(frame, tabPane, settings)
        frame.jMenuBar = LatticeMenuBarFactory(settings, tabManager, profiles).create()
        frame.addWindowListener(
            object : WindowAdapter() {
                override fun windowClosed(event: WindowEvent) {
                    tabManager.closeAllTabs()
                }
            },
        )
        return LatticeWindow(frame, tabManager)
    }

    private fun windowPanel(): JPanel =
        JPanel(BorderLayout()).apply {
            background = LatticeChrome.SURFACE
            border = EmptyBorder(0, 0, 0, 0)
            tabPane.background = LatticeChrome.SURFACE
            tabPane.foreground = LatticeChrome.TITLE_FOREGROUND
            tabPane.isFocusable = false
            add(tabPane, BorderLayout.CENTER)
        }
}

/**
 * Standalone window and its terminal tab controller.
 */
internal data class LatticeWindow(
    val frame: JFrame,
    val tabManager: LatticeTabManager,
)
