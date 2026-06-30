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
package io.github.ketraterm.app.completion

import io.github.ketraterm.completion.*

internal class StandaloneCompletionRegistry(
    specs: List<TerminalCommandSpec> = TerminalCommandSpecs.defaults(),
    private val sessionMruCapacity: Int = DEFAULT_SESSION_MRU_CAPACITY,
) {
    init {
        require(sessionMruCapacity > 0) { "sessionMruCapacity must be > 0, was $sessionMruCapacity" }
    }

    private val lock = Any()
    private val specSource = TerminalCompletionSources.fromSpecs(specs)
    private val sessionMruSources = HashMap<String, TerminalSessionMruCompletionSource>()

    fun createProvider(
        sessionId: String,
        profileId: String? = null,
        workingDirectoryUriProvider: () -> String? = { null },
    ): CompletionSuggestionProvider {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        val mruSource = TerminalCompletionSources.sessionMru(sessionMruCapacity)
        synchronized(lock) {
            sessionMruSources[sessionId] = mruSource
        }
        return CompletionSuggestionProvider(
            engine =
                TerminalCompletionEngines.fromSources(
                    listOf(
                        TerminalCompletionSourceEntry(mruSource, priority = SESSION_MRU_PRIORITY),
                        TerminalCompletionSourceEntry(specSource, priority = SPEC_PRIORITY),
                    ),
                ),
            contextProvider = {
                CompletionSuggestionContext(
                    profileId = profileId,
                    workingDirectoryUri = workingDirectoryUriProvider(),
                )
            },
        )
    }

    fun recordSuccessfulCommand(
        sessionId: String,
        commandLine: String,
        profileId: String?,
        workingDirectoryUri: String?,
    ) {
        val source =
            synchronized(lock) {
                sessionMruSources[sessionId]
            } ?: return
        source.recordSuccessfulCommand(
            commandLine = commandLine,
            profileId = profileId,
            workingDirectoryUri = workingDirectoryUri,
        )
    }

    fun removeSession(sessionId: String) {
        val source =
            synchronized(lock) {
                sessionMruSources.remove(sessionId)
            }
        source?.clear()
    }

    private companion object {
        private const val DEFAULT_SESSION_MRU_CAPACITY = 128
        private const val SESSION_MRU_PRIORITY = 100
        private const val SPEC_PRIORITY = 0
    }
}
