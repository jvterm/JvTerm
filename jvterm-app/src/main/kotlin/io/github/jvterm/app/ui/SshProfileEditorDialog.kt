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
package io.github.jvterm.app.ui

import io.github.jvterm.workspace.TerminalSshProfile
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel

internal class SshProfileEditorDialog(
    parent: Frame,
    private val existingIds: Set<String>,
    profile: TerminalSshProfile?,
) : JDialog(parent, if (profile == null) "Add SSH Profile" else "Edit SSH Profile", true) {
    private val originalId = profile?.id
    private val idField = textField(profile?.id.orEmpty(), 220)
    private val displayNameField = textField(profile?.displayName.orEmpty(), 220)
    private val hostField = textField(profile?.host.orEmpty(), 220)
    private val usernameField = textField(profile?.username.orEmpty(), 220)
    private val portSpinner = JSpinner(SpinnerNumberModel(profile?.port ?: TerminalSshProfile.DEFAULT_PORT, 1, 65535, 1))
    private val terminalTypeField = textField(profile?.terminalType ?: TerminalSshProfile.DEFAULT_TERMINAL_TYPE, 220)
    private val knownHostsField = textField(profile?.knownHostsPath?.toString().orEmpty(), 220)
    private var result: TerminalSshProfile? = null

    init {
        layout = BorderLayout()
        minimumSize = Dimension(500, 360)
        add(buildForm(), BorderLayout.CENTER)
        add(buildButtons(), BorderLayout.SOUTH)
        pack()
        setLocationRelativeTo(parent)
    }

    fun showDialog(): TerminalSshProfile? {
        isVisible = true
        return result
    }

    private fun buildForm(): JPanel =
        JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(18, 18, 12, 18)
            background = Chrome.surface
            addRow(this, 0, "Profile ID:", idField)
            addRow(this, 1, "Display name:", displayNameField)
            addRow(this, 2, "Host:", hostField)
            addRow(this, 3, "Username:", usernameField)
            addRow(this, 4, "Port:", portSpinner)
            addRow(this, 5, "Terminal type:", terminalTypeField)

            val knownHostsPanel =
                JPanel(BorderLayout(8, 0)).apply {
                    isOpaque = false
                    add(knownHostsField, BorderLayout.CENTER)
                    add(
                        JButton("Browse...").apply {
                            preferredSize = Dimension(82, 26)
                            addActionListener { chooseKnownHostsFile() }
                        },
                        BorderLayout.EAST,
                    )
                }
            addRow(this, 6, "Known hosts:", knownHostsPanel)
        }

    private fun buildButtons(): JPanel {
        val saveButton = JButton("Save")
        saveButton.addActionListener { saveProfile() }
        val cancelButton =
            JButton("Cancel").apply {
                addActionListener { dispose() }
            }
        rootPane.defaultButton = saveButton
        return JPanel(FlowLayout(FlowLayout.RIGHT, 12, 12)).apply {
            border = BorderFactory.createMatteBorder(1, 0, 0, 0, Chrome.border)
            background = Chrome.surface
            add(cancelButton)
            add(saveButton)
        }
    }

    private fun saveProfile() {
        val id = idField.text.trim()
        val displayName = displayNameField.text.trim()
        val host = hostField.text.trim()
        val username = usernameField.text.trim()
        val terminalType = terminalTypeField.text.trim().ifEmpty { TerminalSshProfile.DEFAULT_TERMINAL_TYPE }
        val knownHostsText = knownHostsField.text.trim()
        val duplicate = id != originalId && id in existingIds
        val error =
            when {
                id.isBlank() -> "Profile ID is required."
                !isValidId(id) -> "Profile ID may contain only letters, numbers, hyphen, underscore, and dot."
                duplicate -> "Profile ID already exists."
                displayName.isBlank() -> "Display name is required."
                host.isBlank() -> "Host is required."
                username.isBlank() -> "Username is required."
                else -> null
            }
        if (error != null) {
            JOptionPane.showMessageDialog(this, error, "Invalid SSH Profile", JOptionPane.ERROR_MESSAGE)
            return
        }

        result =
            TerminalSshProfile(
                id = id,
                displayName = displayName,
                host = host,
                username = username,
                port = portSpinner.value as Int,
                terminalType = terminalType,
                knownHostsPath = knownHostsText.takeIf(String::isNotEmpty)?.let(Path::of),
            )
        dispose()
    }

    private fun chooseKnownHostsFile() {
        val chooser =
            JFileChooser(knownHostsField.text).apply {
                fileSelectionMode = JFileChooser.FILES_ONLY
            }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            knownHostsField.text = chooser.selectedFile.absolutePath
        }
    }

    private fun addRow(
        panel: JPanel,
        row: Int,
        labelText: String,
        component: java.awt.Component,
    ) {
        panel.add(
            JLabel(labelText).apply {
                foreground = Chrome.textPrimary
            },
            GridBagConstraints().apply {
                gridx = 0
                gridy = row
                anchor = GridBagConstraints.WEST
                insets = Insets(6, 0, 6, 12)
            },
        )
        panel.add(
            component,
            GridBagConstraints().apply {
                gridx = 1
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(6, 0, 6, 0)
            },
        )
    }

    private fun textField(
        value: String,
        width: Int,
    ): JTextField =
        JTextField(value).apply {
            preferredSize = Dimension(width, 26)
        }

    private fun isValidId(value: String): Boolean {
        for (character in value) {
            if (character.isLetterOrDigit() || character == '-' || character == '_' || character == '.') continue
            return false
        }
        return true
    }
}
