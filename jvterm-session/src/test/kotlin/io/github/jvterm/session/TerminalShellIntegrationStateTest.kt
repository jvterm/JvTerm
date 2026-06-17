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
    fun `prompt command lifecycle projects prompt dividers and failed command rails into viewport`() {
        val state = TerminalShellIntegrationState()
        val promptDividers = BooleanArray(5)
        val failedCommandRails = BooleanArray(5)

        state.recordPromptStart(11)
        state.recordPromptEnd(11)
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
    fun `viewport projection clips command ranges without allocating intermediate records`() {
        val state = TerminalShellIntegrationState()
        val promptDividers = BooleanArray(3)
        val failedCommandRails = BooleanArray(3)

        state.recordCommandStart(5)
        state.recordCommandFinished(10, exitCode = 1)
        state.copyViewport(
            firstAbsoluteRow = 7,
            rowCount = 3,
            promptDividers = promptDividers,
            failedCommandRails = failedCommandRails,
        )

        assertContentEquals(booleanArrayOf(false, false, false), promptDividers)
        assertContentEquals(booleanArrayOf(true, true, true), failedCommandRails)
    }

    @Test
    fun `zero null and missing command starts do not create failed command ranges`() {
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
    fun `duplicate command starts close over the newest active command only`() {
        val state = TerminalShellIntegrationState()

        state.recordCommandStart(1)
        state.recordCommandStart(3)
        state.recordCommandFinished(4, exitCode = 2)

        assertFalse(state.hasFailedCommandRailAt(1))
        assertTrue(state.hasFailedCommandRailAt(3))
        assertTrue(state.hasFailedCommandRailAt(4))
    }

    @Test
    fun `prompt end without prompt start is ignored`() {
        val state = TerminalShellIntegrationState()
        val promptDividers = BooleanArray(2)
        val failedCommandRails = BooleanArray(2)

        state.recordPromptEnd(1)
        state.copyViewport(
            firstAbsoluteRow = 0,
            rowCount = 2,
            promptDividers = promptDividers,
            failedCommandRails = failedCommandRails,
        )

        assertContentEquals(booleanArrayOf(false, false), promptDividers)
        assertContentEquals(booleanArrayOf(false, false), failedCommandRails)
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
    fun `bounded command timeline evicts oldest records`() {
        val state = TerminalShellIntegrationState(capacity = 2)

        state.recordPromptStart(1)
        state.recordPromptStart(2)
        state.recordPromptStart(3)

        assertFalse(state.hasPromptDividerAt(1))
        assertTrue(state.hasPromptDividerAt(2))
        assertTrue(state.hasPromptDividerAt(3))
    }

    @Test
    fun `evicting active prompt prevents command start from attaching to removed record`() {
        val state = TerminalShellIntegrationState(capacity = 1)

        state.recordPromptStart(1)
        state.recordPromptStart(2)
        state.recordCommandStart(3)
        state.recordCommandFinished(4, exitCode = 1)

        assertFalse(state.hasFailedCommandRailAt(1))
        assertTrue(state.hasPromptDividerAt(2))
        assertTrue(state.hasFailedCommandRailAt(3))
        assertTrue(state.hasFailedCommandRailAt(4))
    }
}
