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
package com.gagik.terminal.standalone.profile

import java.nio.file.Path

/**
 * Host-owned terminal launch profile.
 *
 * A profile describes the process contract for one terminal pane. It deliberately
 * stays outside reusable Swing UI and terminal core modules because shell
 * discovery, working directories, and user-facing names are standalone host
 * policy.
 */
internal data class StandaloneTerminalProfile(
    val id: String,
    val displayName: String,
    val command: List<String>,
    val environment: Map<String, String> = emptyMap(),
    val workingDirectory: Path? = null,
) {
    init {
        require(id.isNotBlank()) { "profile id must not be blank" }
        require(displayName.isNotBlank()) { "profile displayName must not be blank" }
        require(command.isNotEmpty()) { "profile command must not be empty" }
        require(command.none { it.isEmpty() }) { "profile command elements must not be empty" }
    }
}
