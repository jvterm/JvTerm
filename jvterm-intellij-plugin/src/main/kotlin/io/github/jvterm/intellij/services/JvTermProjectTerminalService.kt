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
package io.github.jvterm.intellij.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import io.github.jvterm.intellij.settings.JvTermIntellijSettings
import io.github.jvterm.intellij.ui.JvTermTerminalPane
import io.github.jvterm.ui.swing.settings.SwingSettings
import io.github.jvterm.workspace.TerminalWorkspace
import io.github.jvterm.workspace.TerminalWorkspaceListener
import io.github.jvterm.workspace.TerminalWorkspaceOpenOptions
import io.github.jvterm.workspace.TerminalWorkspaceTab

/**
 * Project-level owner for IntelliJ-hosted JvTerm tabs and sessions.
 *
 * The service adapts IntelliJ `Content` tabs to the host-neutral
 * [TerminalWorkspace]. Closing an IDE tab disposes the corresponding pane and
 * terminal session; closing the project disposes all remaining sessions.
 *
 * @property project IntelliJ project that owns this terminal workspace.
 */
@Service(Service.Level.PROJECT)
class JvTermProjectTerminalService(
    private val project: Project,
) : Disposable {
    private val contentsByTabId = LinkedHashMap<String, Content>()
    private val panesByTabId = LinkedHashMap<String, JvTermTerminalPane>()
    private val workspace = TerminalWorkspace(IntellijWorkspaceListener())
    private var disposed = false

    /**
     * Returns true when this project already has an open terminal tab.
     */
    fun hasOpenTabs(): Boolean = contentsByTabId.isNotEmpty()

    /**
     * Opens the initial terminal tab if no terminal content exists yet.
     *
     * @param toolWindow target IntelliJ tool window.
     */
    fun ensureInitialTab(toolWindow: ToolWindow) {
        if (hasOpenTabs()) return
        openDefaultTab(toolWindow)
    }

    /**
     * Opens one local terminal tab in [toolWindow].
     *
     * @param toolWindow target IntelliJ tool window.
     * @return created content, or `null` if the PTY could not start.
     */
    fun openDefaultTab(toolWindow: ToolWindow): Content? {
        check(!disposed) { "JvTerm project terminal service is disposed" }

        val profile = JvTermDefaultProfileFactory.defaultProfile(project)
        val settings = JvTermIntellijSettings.current()
        val workspaceTab =
            try {
                workspace.openTab(
                    profile = profile,
                    options = openOptions(settings),
                )
            } catch (exception: Exception) {
                Messages.showErrorDialog(
                    project,
                    exception.message ?: exception.javaClass.name,
                    "Unable to start ${profile.displayName}",
                )
                return null
            }

        val pane = JvTermTerminalPane.create(workspaceTab)
        val contentManager = toolWindow.contentManager
        val content =
            contentManager.factory.createContent(
                pane.component,
                workspaceTab.title,
                false,
            )
        val tabDisposable = TerminalTabDisposable(workspaceTab.id)

        content.isCloseable = true
        content.setPreferredFocusableComponent(pane.terminal)
        content.setDisposer(tabDisposable)

        contentsByTabId[workspaceTab.id] = content
        panesByTabId[workspaceTab.id] = pane

        contentManager.addContent(content)
        contentManager.setSelectedContent(content, true)
        pane.requestFocus()
        return content
    }

    override fun dispose() {
        if (disposed) return
        disposed = true

        val panes = panesByTabId.values.toList()
        panesByTabId.clear()
        contentsByTabId.clear()

        for (pane in panes) {
            pane.close()
        }
        workspace.close()
    }

    private fun closeTabFromContent(tabId: String) {
        if (disposed) return

        panesByTabId.remove(tabId)?.close()
        contentsByTabId.remove(tabId)
        workspace.closeTab(tabId)
    }

    private fun openOptions(settings: SwingSettings): TerminalWorkspaceOpenOptions =
        TerminalWorkspaceOpenOptions(
            columns = settings.columns,
            rows = settings.rows,
            treatAmbiguousAsWide = settings.treatAmbiguousAsWide,
            maxHistory = settings.scrollbackLines,
        )

    private inner class TerminalTabDisposable(
        private val tabId: String,
    ) : Disposable {
        override fun dispose() {
            closeTabFromContent(tabId)
        }
    }

    private inner class IntellijWorkspaceListener : TerminalWorkspaceListener {
        override fun titleChanged(
            tab: TerminalWorkspaceTab,
            title: String,
        ) {
            invokeLaterIfAlive {
                contentsByTabId[tab.id]?.displayName = title
            }
        }

        override fun tabClosed(tabId: String) {
            invokeLaterIfAlive {
                contentsByTabId.remove(tabId)
                panesByTabId.remove(tabId)
            }
        }
    }

    private fun invokeLaterIfAlive(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (!disposed) {
                action()
            }
        }
    }

    companion object {
        /**
         * Returns the terminal service for [project].
         *
         * @param project IntelliJ project.
         * @return project terminal service.
         */
        fun getInstance(project: Project): JvTermProjectTerminalService = project.service()
    }
}
