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
package io.github.ketraterm.completion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShapeAwareCompletionSourceTest {
    @Test
    fun `accepted matching shape boosts candidate above otherwise higher score candidate`() {
        val source =
            ShapeAwareCompletionSource(
                delegate =
                    fixedSource(
                        candidate("status", score = 320),
                        candidate("switch", score = 300),
                    ),
                shapeStatsProvider = {
                    listOf(
                        shapeStats(
                            commandLine = "git switch main",
                            acceptedCount = 4,
                            profileId = "bash",
                            workingDirectoryUri = "file:///repo",
                        ),
                    )
                },
            )

        val candidates = source.complete(request("git s", profileId = "bash", workingDirectoryUri = "file:///repo"))

        assertEquals(listOf("switch", "status"), candidates.map { it.replacementText })
        assertTrue(candidates[0].score > candidates[1].score)
    }

    @Test
    fun `dismissed matching shape demotes candidate`() {
        val source =
            ShapeAwareCompletionSource(
                delegate =
                    fixedSource(
                        candidate("status", score = 320),
                        candidate("switch", score = 300),
                    ),
                shapeStatsProvider = {
                    listOf(
                        shapeStats(
                            commandLine = "git status",
                            dismissedCount = 4,
                            profileId = "bash",
                            workingDirectoryUri = "file:///repo",
                        ),
                    )
                },
            )

        val candidates = source.complete(request("git s", profileId = "bash", workingDirectoryUri = "file:///repo"))

        assertEquals(listOf("switch", "status"), candidates.map { it.replacementText })
    }

    @Test
    fun `profile and working directory matching shape receives stronger adjustment`() {
        val source =
            ShapeAwareCompletionSource(
                delegate =
                    fixedSource(
                        candidate("status", score = 320),
                        candidate("switch", score = 320),
                    ),
                shapeStatsProvider = {
                    listOf(
                        shapeStats(
                            commandLine = "git status",
                            acceptedCount = 1,
                            profileId = "bash",
                            workingDirectoryUri = "file:///other",
                        ),
                        shapeStats(
                            commandLine = "git switch main",
                            acceptedCount = 1,
                            profileId = "bash",
                            workingDirectoryUri = "file:///repo",
                        ),
                    )
                },
            )

        val candidates = source.complete(request("git s", profileId = "bash", workingDirectoryUri = "file:///repo"))

        assertEquals(listOf("switch", "status"), candidates.map { it.replacementText })
    }

    @Test
    fun `specific rejected shape overrides broad accepted shape`() {
        val source =
            ShapeAwareCompletionSource(
                delegate =
                    fixedSource(
                        candidate("status", score = 320),
                        candidate("switch", score = 300),
                    ),
                shapeStatsProvider = {
                    listOf(
                        shapeStats(
                            commandLine = "git status",
                            acceptedCount = 10,
                        ),
                        shapeStats(
                            commandLine = "git status",
                            dismissedCount = 4,
                            profileId = "bash",
                            workingDirectoryUri = "file:///repo",
                        ),
                    )
                },
            )

        val candidates = source.complete(request("git s", profileId = "bash", workingDirectoryUri = "file:///repo"))

        assertEquals(listOf("switch", "status"), candidates.map { it.replacementText })
    }

    @Test
    fun `shape stats do not create candidates`() {
        val source =
            ShapeAwareCompletionSource(
                delegate = TerminalCompletionSource.NONE,
                shapeStatsProvider = {
                    listOf(
                        shapeStats(commandLine = "git status", successCount = 10),
                    )
                },
            )

        assertTrue(source.complete(request("git s")).isEmpty())
    }

    private fun fixedSource(vararg candidates: TerminalCompletionCandidate): TerminalCompletionSource =
        TerminalCompletionSource { candidates.toList() }

    private fun candidate(
        replacementText: String,
        score: Int,
    ): TerminalCompletionCandidate =
        TerminalCompletionCandidate(
            replacementText = replacementText,
            replacementStartOffset = 4,
            replacementEndOffset = 5,
            displayText = replacementText,
            source = "test",
            kind = TerminalCompletionCandidateKind.SUBCOMMAND,
            score = score,
        )

    private fun shapeStats(
        commandLine: String,
        profileId: String? = null,
        workingDirectoryUri: String? = null,
        useCount: Int = 0,
        successCount: Int = 0,
        failureCount: Int = 0,
        acceptedCount: Int = 0,
        dismissedCount: Int = 0,
    ): TerminalCommandShapeStats =
        TerminalCommandShapeStats(
            shape = TerminalCommandLineShape.fromCommandLine(commandLine)!!,
            profileId = profileId,
            workingDirectoryUri = workingDirectoryUri,
            useCount = useCount,
            successCount = successCount,
            failureCount = failureCount,
            acceptedCount = acceptedCount,
            dismissedCount = dismissedCount,
            lastUsedEpochMillis = 100,
        )

    private fun request(
        commandLine: String,
        profileId: String? = null,
        workingDirectoryUri: String? = null,
    ): TerminalCompletionRequest =
        TerminalCompletionRequest(
            commandLine = commandLine,
            cursorOffset = commandLine.length,
            profileId = profileId,
            workingDirectoryUri = workingDirectoryUri,
        )
}
