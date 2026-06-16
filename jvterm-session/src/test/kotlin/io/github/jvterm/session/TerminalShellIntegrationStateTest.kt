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
package io.github.jvterm.session

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalShellIntegrationStateTest {
    @Test
    fun `prompt rows and failed command ranges copy into viewport flags`() {
        val state = TerminalShellIntegrationState()
        val promptDividers = BooleanArray(5)
        val failedCommandRails = BooleanArray(5)

        state.recordPromptStart(11)
        state.recordCommandStart(12)
        state.recordCommandFinished(14, exitCode = 7)
        state.copyViewport(
            firstAbsoluteRow = 10,
            rowCount = 5,
            promptDividers = promptDividers,
            failedCommandRails = failedCommandRails,
        )

        assertContentEquals(booleanArrayOf(false, true, false, false, false), promptDividers)
        assertContentEquals(booleanArrayOf(false, false, true, true, true), failedCommandRails)
    }

    @Test
    fun `zero null and missing exit states do not create failed command ranges`() {
        val state = TerminalShellIntegrationState()

        state.recordCommandStart(1)
        state.recordCommandFinished(3, exitCode = 0)
        state.recordCommandStart(4)
        state.recordCommandFinished(5, exitCode = null)
        state.recordCommandFinished(8, exitCode = 2)

        assertFalse(state.hasFailedCommandRailAt(1))
        assertFalse(state.hasFailedCommandRailAt(4))
        assertFalse(state.hasFailedCommandRailAt(8))
    }

    @Test
    fun `timeline rewind clears stale prompt and command rows`() {
        val state = TerminalShellIntegrationState()

        state.observeLiveBottomRow(100)
        state.recordPromptStart(90)
        state.recordCommandStart(91)
        state.recordCommandFinished(95, exitCode = 1)
        state.observeLiveBottomRow(10)

        assertFalse(state.hasPromptDividerAt(90))
        assertFalse(state.hasFailedCommandRailAt(91))
    }

    @Test
    fun `bounded storage evicts oldest prompt rows`() {
        val state = TerminalShellIntegrationState(capacity = 2)

        state.recordPromptStart(1)
        state.recordPromptStart(2)
        state.recordPromptStart(3)

        assertFalse(state.hasPromptDividerAt(1))
        assertTrue(state.hasPromptDividerAt(2))
        assertTrue(state.hasPromptDividerAt(3))
    }
}
