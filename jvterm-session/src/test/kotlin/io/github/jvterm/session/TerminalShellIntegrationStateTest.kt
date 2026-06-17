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
            lineIds = longArrayOf(10, 11, 12, 13, 14),
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
            lineIds = longArrayOf(7, 8, 9),
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

        assertFalse(state.hasFailedCommandRailAtLine(1))
        assertFalse(state.hasFailedCommandRailAtLine(4))
        assertFalse(state.hasFailedCommandRailAtLine(8))
    }

    @Test
    fun `duplicate command starts close over the newest active command only`() {
        val state = TerminalShellIntegrationState()

        state.recordCommandStart(1)
        state.recordCommandStart(3)
        state.recordCommandFinished(4, exitCode = 2)

        assertFalse(state.hasFailedCommandRailAtLine(1))
        assertTrue(state.hasFailedCommandRailAtLine(3))
        assertTrue(state.hasFailedCommandRailAtLine(4))
    }

    @Test
    fun `prompt end without prompt start is ignored`() {
        val state = TerminalShellIntegrationState()
        val promptDividers = BooleanArray(2)
        val failedCommandRails = BooleanArray(2)

        state.recordPromptEnd(1)
        state.copyViewport(
            lineIds = longArrayOf(1, 2),
            rowCount = 2,
            promptDividers = promptDividers,
            failedCommandRails = failedCommandRails,
        )

        assertContentEquals(booleanArrayOf(false, false), promptDividers)
        assertContentEquals(booleanArrayOf(false, false), failedCommandRails)
    }

    @Test
    fun `new prompt abandons unfinished command so stale finish marker cannot create a failed range`() {
        val state = TerminalShellIntegrationState()

        state.recordCommandStart(10)
        state.recordPromptStart(20)
        state.recordCommandFinished(19, exitCode = 1)

        assertFalse(state.hasFailedCommandRailAtLine(10))
        assertTrue(state.hasPromptDividerAtLine(20))
    }

    @Test
    fun `repeated command finish closes only the active command once`() {
        val state = TerminalShellIntegrationState()

        state.recordCommandStart(10)
        state.recordCommandFinished(11, exitCode = 1)
        state.recordCommandFinished(20, exitCode = 1)

        assertTrue(state.hasFailedCommandRailAtLine(10))
        assertTrue(state.hasFailedCommandRailAtLine(11))
        assertFalse(state.hasFailedCommandRailAtLine(20))
    }

    @Test
    fun `prompt end applies only to newest prompt after duplicate prompt starts`() {
        val state = TerminalShellIntegrationState()

        state.recordPromptStart(1)
        state.recordPromptStart(2)
        state.recordPromptEnd(3)
        state.recordCommandStart(4)
        state.recordCommandFinished(5, exitCode = 1)

        assertTrue(state.hasPromptDividerAtLine(1))
        assertTrue(state.hasPromptDividerAtLine(2))
        assertFalse(state.hasFailedCommandRailAtLine(3))
        assertTrue(state.hasFailedCommandRailAtLine(4))
        assertTrue(state.hasFailedCommandRailAtLine(5))
    }

    @Test
    fun `destructive row rewind does not clear identity anchored records`() {
        val state = TerminalShellIntegrationState()

        state.observeLiveBottomRow(100)
        state.recordPromptStart(90)
        state.recordCommandStart(91)
        state.recordCommandFinished(95, exitCode = 1)
        state.observeLiveBottomRow(10)

        assertTrue(state.hasPromptDividerAtLine(90))
        assertTrue(state.hasFailedCommandRailAtLine(91))
    }

    @Test
    fun `bounded command timeline evicts oldest records`() {
        val state = TerminalShellIntegrationState(capacity = 2)

        state.recordPromptStart(1)
        state.recordPromptStart(2)
        state.recordPromptStart(3)

        assertFalse(state.hasPromptDividerAtLine(1))
        assertTrue(state.hasPromptDividerAtLine(2))
        assertTrue(state.hasPromptDividerAtLine(3))
    }

    @Test
    fun `evicting active prompt prevents command start from attaching to removed record`() {
        val state = TerminalShellIntegrationState(capacity = 1)

        state.recordPromptStart(1)
        state.recordPromptStart(2)
        state.recordCommandStart(3)
        state.recordCommandFinished(4, exitCode = 1)

        assertFalse(state.hasFailedCommandRailAtLine(1))
        assertTrue(state.hasPromptDividerAtLine(2))
        assertTrue(state.hasFailedCommandRailAtLine(3))
        assertTrue(state.hasFailedCommandRailAtLine(4))
    }

    @Test
    fun `viewport projection follows line ids when visible row positions change`() {
        val state = TerminalShellIntegrationState()
        val promptDividers = BooleanArray(4)
        val failedCommandRails = BooleanArray(4)

        state.recordPromptStart(40)
        state.recordCommandStart(41)
        state.recordCommandFinished(42, exitCode = 1)
        state.copyViewport(
            lineIds = longArrayOf(70, 42, 40, 41),
            rowCount = 4,
            promptDividers = promptDividers,
            failedCommandRails = failedCommandRails,
        )

        assertContentEquals(booleanArrayOf(false, false, true, false), promptDividers)
        assertContentEquals(booleanArrayOf(false, true, false, true), failedCommandRails)
    }

    @Test
    fun `exclusive command start excludes prompt input line from failed rail`() {
        val state = TerminalShellIntegrationState()
        val promptDividers = BooleanArray(4)
        val failedCommandRails = BooleanArray(4)
        val commandStarts = BooleanArray(4)
        val commandEnds = BooleanArray(4)

        state.recordCommandStart(10, includeLine = false)
        state.recordCommandFinished(12, exitCode = 1)
        state.copyViewport(
            lineIds = longArrayOf(10, 11, 12, 13),
            rowCount = 4,
            promptDividers = promptDividers,
            failedCommandRails = failedCommandRails,
            commandStarts = commandStarts,
            commandEnds = commandEnds,
        )

        assertContentEquals(booleanArrayOf(false, false, false, false), promptDividers)
        assertContentEquals(booleanArrayOf(false, true, true, false), failedCommandRails)
        assertContentEquals(booleanArrayOf(true, false, false, false), commandStarts)
        assertContentEquals(booleanArrayOf(false, false, true, false), commandEnds)
    }

    @Test
    fun `inclusive command start includes same line output in failed rail`() {
        val state = TerminalShellIntegrationState()
        val promptDividers = BooleanArray(3)
        val failedCommandRails = BooleanArray(3)

        state.recordCommandStart(10, includeLine = true)
        state.recordCommandFinished(11, exitCode = 1)
        state.copyViewport(
            lineIds = longArrayOf(10, 11, 12),
            rowCount = 3,
            promptDividers = promptDividers,
            failedCommandRails = failedCommandRails,
        )

        assertContentEquals(booleanArrayOf(false, false, false), promptDividers)
        assertContentEquals(booleanArrayOf(true, true, false), failedCommandRails)
    }

    @Test
    fun `failed rail covers duplicate physical rows from command output reflow`() {
        val state = TerminalShellIntegrationState()
        val promptDividers = BooleanArray(5)
        val failedCommandRails = BooleanArray(5)
        val commandStarts = BooleanArray(5)
        val commandEnds = BooleanArray(5)

        state.recordCommandStart(10, includeLine = false)
        state.recordCommandFinished(12, exitCode = 1)
        state.copyViewport(
            lineIds = longArrayOf(10, 11, 11, 12, 12),
            rowCount = 5,
            promptDividers = promptDividers,
            failedCommandRails = failedCommandRails,
            commandStarts = commandStarts,
            commandEnds = commandEnds,
        )

        assertContentEquals(booleanArrayOf(false, false, false, false, false), promptDividers)
        assertContentEquals(booleanArrayOf(false, true, true, true, true), failedCommandRails)
        assertContentEquals(booleanArrayOf(true, false, false, false, false), commandStarts)
        assertContentEquals(booleanArrayOf(false, false, false, true, false), commandEnds)
    }

    @Test
    fun `exclusive command with no output does not draw a failed rail`() {
        val state = TerminalShellIntegrationState()
        val promptDividers = BooleanArray(2)
        val failedCommandRails = BooleanArray(2)

        state.recordCommandStart(10, includeLine = false)
        state.recordCommandFinished(10, exitCode = 1)
        state.copyViewport(
            lineIds = longArrayOf(10, 11),
            rowCount = 2,
            promptDividers = promptDividers,
            failedCommandRails = failedCommandRails,
        )

        assertContentEquals(booleanArrayOf(false, false), promptDividers)
        assertContentEquals(booleanArrayOf(false, false), failedCommandRails)
    }

    @Test
    fun `prompt divider is projected once when resize reflow exposes duplicate physical rows`() {
        val state = TerminalShellIntegrationState()
        val promptDividers = BooleanArray(3)
        val failedCommandRails = BooleanArray(3)

        state.recordPromptStart(9)
        state.copyViewport(
            lineIds = longArrayOf(9, 9, 9),
            rowCount = 3,
            promptDividers = promptDividers,
            failedCommandRails = failedCommandRails,
        )

        assertContentEquals(booleanArrayOf(true, false, false), promptDividers)
        assertContentEquals(booleanArrayOf(false, false, false), failedCommandRails)
    }
}
