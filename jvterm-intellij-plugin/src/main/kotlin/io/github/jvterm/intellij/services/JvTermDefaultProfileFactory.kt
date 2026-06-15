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

import com.intellij.openapi.project.Project
import io.github.jvterm.workspace.TerminalProfile
import io.github.jvterm.workspace.TerminalProfileRegistry
import java.nio.file.Path

/**
 * Creates launch profiles for IntelliJ-hosted local terminal tabs.
 */
internal object JvTermDefaultProfileFactory {
    /**
     * Creates the default profile for [project].
     *
     * The shell command comes from the host-neutral profile registry. The
     * IntelliJ-specific contribution is only the initial working directory.
     *
     * @param project current IntelliJ project.
     * @return local terminal launch profile.
     */
    fun defaultProfile(project: Project): TerminalProfile = defaultProfile(project.basePath)

    /**
     * Creates a default profile for a nullable project path.
     *
     * @param basePath project base path, or `null` when the IDE has no local project path.
     * @return local terminal launch profile.
     */
    fun defaultProfile(basePath: String?): TerminalProfile {
        val workingDirectory = workingDirectory(basePath)
        return TerminalProfileRegistry()
            .initialProfile(emptyList())
            .copy(workingDirectory = workingDirectory)
    }

    private fun workingDirectory(basePath: String?): Path =
        if (basePath.isNullOrBlank()) {
            Path.of(System.getProperty("user.home"))
        } else {
            Path.of(basePath)
        }
}
