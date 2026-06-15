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
package io.github.jvterm.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowEx
import io.github.jvterm.intellij.JvTermBundle
import io.github.jvterm.intellij.services.JvTermProjectTerminalService

/**
 * Creates the IntelliJ tool window that hosts JvTerm terminal tabs.
 */
class JvTermToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val terminalService = JvTermProjectTerminalService.getInstance(project)
        installTitleActions(project, toolWindow, terminalService)
        terminalService.ensureInitialTab(toolWindow)
    }

    private fun installTitleActions(
        project: Project,
        toolWindow: ToolWindow,
        terminalService: JvTermProjectTerminalService,
    ) {
        val toolWindowEx = toolWindow as? ToolWindowEx ?: return
        toolWindowEx.setTitleActions(
            listOf(
                NewTerminalAction(project, toolWindow, terminalService),
            ),
        )
    }

    private class NewTerminalAction(
        private val project: Project,
        private val toolWindow: ToolWindow,
        private val terminalService: JvTermProjectTerminalService,
    ) : DumbAwareAction(
            JvTermBundle.message("action.jvterm.newTerminal.text"),
            JvTermBundle.message("action.jvterm.newTerminal.description"),
            AllIcons.General.Add,
        ) {
        override fun actionPerformed(event: AnActionEvent) {
            terminalService.openDefaultTab(toolWindow)
        }

        override fun update(event: AnActionEvent) {
            event.presentation.isEnabled = !project.isDisposed
        }
    }
}
