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
package io.github.ketraterm.ui.swing.suggestion

import kotlin.test.*

class SwingShellSuggestionReplacementRangeTest {
    @Test
    fun `explicit replacement range returns validated exclusive range`() {
        val suggestion =
            SwingShellSuggestion(
                replacementText = "status",
                replacementStartOffset = 4,
                replacementEndOffset = 5,
            )

        val range = suggestion.explicitReplacementRangeFor(request("git s"))!!

        assertTrue(suggestion.hasExplicitReplacementRange())
        assertEquals(4, range.startOffset)
        assertEquals(5, range.endOffset)
    }

    @Test
    fun `missing explicit replacement range returns null`() {
        val suggestion = SwingShellSuggestion("git status")

        assertFalse(suggestion.hasExplicitReplacementRange())
        assertNull(suggestion.explicitReplacementRangeFor(request("git s")))
    }

    @Test
    fun `explicit replacement range must contain cursor`() {
        val suggestion =
            SwingShellSuggestion(
                replacementText = "checkout",
                replacementStartOffset = 4,
                replacementEndOffset = 7,
            )

        assertNull(suggestion.explicitReplacementRangeFor(request("git checkout", cursorOffset = 12)))
        assertNull(suggestion.explicitReplacementRangeFor(request("git checkout", cursorOffset = 3)))
    }

    @Test
    fun `explicit replacement range rejects offsets outside command text`() {
        val suggestion =
            SwingShellSuggestion(
                replacementText = "status",
                replacementStartOffset = 4,
                replacementEndOffset = 99,
            )

        assertNull(suggestion.explicitReplacementRangeFor(request("git s")))
    }

    @Test
    fun `explicit replacement range rejects surrogate pair splits`() {
        val commandText = "echo \uD83D\uDE02"
        val suggestion =
            SwingShellSuggestion(
                replacementText = "emoji",
                replacementStartOffset = 5,
                replacementEndOffset = 7,
            )

        assertNull(suggestion.explicitReplacementRangeFor(request(commandText, cursorOffset = 6)))
    }

    private fun request(
        commandText: String,
        cursorOffset: Int = commandText.length,
    ): SwingShellSuggestionRequest =
        SwingShellSuggestionRequest(
            commandText = commandText,
            cursorOffset = cursorOffset,
            anchorColumn = cursorOffset,
            anchorRow = 0,
        )
}
