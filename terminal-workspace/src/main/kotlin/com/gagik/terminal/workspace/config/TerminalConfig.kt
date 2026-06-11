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
package com.gagik.terminal.workspace.config

/**
 * Host-neutral configuration settings for the terminal emulator.
 *
 * This data class is immutable. For updates, a new instance is created via [copy].
 * The settings are serialized to/from a TOML configuration file on disk.
 */
data class TerminalConfig(
    val theme: String = "one-dark",
    val treatAmbiguousAsWide: Boolean = false,
    val fontFamily: String = "Cascadia Mono",
    val fontSize: Int = 16,
    val columns: Int = 100,
    val rows: Int = 30,
    val cursorBlinkMillis: Int = 600,
    val useSystemFallbackFonts: Boolean = false,
) {
    init {
        require(columns > 0) { "columns must be > 0, was $columns" }
        require(rows > 0) { "rows must be > 0, was $rows" }
        require(fontSize > 0) { "fontSize must be > 0, was $fontSize" }
        require(cursorBlinkMillis > 0) { "cursorBlinkMillis must be > 0, was $cursorBlinkMillis" }
        require(theme.isNotBlank()) { "theme must not be blank" }
        require(fontFamily.isNotBlank()) { "fontFamily must not be blank" }
    }
}
