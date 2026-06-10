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
import com.gagik.terminal.ui.swing.settings.TerminalTheme
import javax.swing.ButtonGroup
import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JRadioButtonMenuItem

/**
 * Builds standalone application menus and maps menu changes to host settings.
 */
internal class LatticeMenuBarFactory(
    private val settings: StandaloneTerminalSettings,
    private val tabManager: LatticeTabManager,
    private val profiles: List<StandaloneTerminalProfile>,
) {
    fun create(): JMenuBar {
        val menuBar = JMenuBar()
        menuBar.add(createFileMenu())
        menuBar.add(createThemeMenu())
        menuBar.add(createWidthMenu())
        return menuBar
    }

    private fun createFileMenu(): JMenu {
        val fileMenu = JMenu("File")
        val newTabMenu = JMenu("New Tab")
        for (profile in profiles) {
            val item = JMenuItem(profile.displayName)
            item.addActionListener {
                tabManager.openTab(profile)
            }
            newTabMenu.add(item)
        }
        val closeTabItem = JMenuItem("Close Tab")
        closeTabItem.addActionListener {
            tabManager.closeSelectedTab()
        }
        fileMenu.add(newTabMenu)
        fileMenu.add(closeTabItem)
        return fileMenu
    }

    private fun createThemeMenu(): JMenu {
        val themeMenu = JMenu("Theme")
        val themeGroup = ButtonGroup()
        TerminalTheme.entries.forEach { theme ->
            val item = JRadioButtonMenuItem(theme.displayName(), theme == settings.theme)
            themeGroup.add(item)
            item.addActionListener {
                settings.theme = theme
                tabManager.reloadAllPanes()
            }
            themeMenu.add(item)
        }
        return themeMenu
    }

    private fun createWidthMenu(): JMenu {
        val widthMenu = JMenu("Width")
        val ambiguousWidthItem = JCheckBoxMenuItem("Ambiguous as wide", settings.treatAmbiguousAsWide)
        ambiguousWidthItem.addActionListener {
            settings.treatAmbiguousAsWide = ambiguousWidthItem.isSelected
            tabManager.reloadAllPanes()
        }
        widthMenu.add(ambiguousWidthItem)
        return widthMenu
    }

    private fun TerminalTheme.displayName(): String =
        name.lowercase().split("_").joinToString(" ") {
            it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
        }
}
