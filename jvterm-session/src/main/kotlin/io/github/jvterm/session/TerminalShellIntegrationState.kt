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

/**
 * Session-owned OSC 133 shell command timeline.
 *
 * This model intentionally lives outside terminal core. Core owns bytes already
 * committed to the terminal grid; shell integration markers are host metadata
 * about prompt and command lifecycle. The timeline stores bounded primitive
 * command records and projects them into caller-owned viewport decoration arrays.
 *
 * Storage is data-oriented: each command field is a primitive column, records
 * are append-only until bounded eviction, and paint paths consume only projected
 * primitive boolean arrays. All methods are thread-safe. Writers are normally
 * invoked from the serialized session parser path; readers are UI threads that
 * snapshot visible rows before painting.
 */
class TerminalShellIntegrationState
    @JvmOverloads
    constructor(
        private val capacity: Int = DEFAULT_CAPACITY,
    ) {
        init {
            require(capacity > 0) { "capacity must be > 0, was $capacity" }
        }

        private val lock = Any()
        private val promptStartRows = LongArray(capacity) { NO_ROW }
        private val promptEndRows = LongArray(capacity) { NO_ROW }
        private val commandStartRows = LongArray(capacity) { NO_ROW }
        private val commandEndRows = LongArray(capacity) { NO_ROW }
        private val exitCodes = IntArray(capacity) { NO_EXIT_CODE }
        private val states = IntArray(capacity)

        private var count = 0
        private var activePromptIndex = NO_INDEX
        private var activeCommandIndex = NO_INDEX
        private var lastObservedBottomRow = NO_ROW

        /**
         * Records the start of a shell prompt.
         *
         * @param absoluteRow absolute render row where the prompt begins.
         */
        fun recordPromptStart(absoluteRow: Long) {
            require(absoluteRow >= 0) { "absoluteRow must be >= 0, was $absoluteRow" }
            synchronized(lock) {
                val index = appendCommandLocked()
                promptStartRows[index] = absoluteRow
                states[index] = states[index] or STATE_PROMPT_STARTED
                activePromptIndex = index
            }
        }

        /**
         * Records the end of the active shell prompt.
         *
         * @param absoluteRow absolute render row where prompt printing ended.
         */
        fun recordPromptEnd(absoluteRow: Long) {
            require(absoluteRow >= 0) { "absoluteRow must be >= 0, was $absoluteRow" }
            synchronized(lock) {
                val index = activePromptIndex
                if (index == NO_INDEX) return

                promptEndRows[index] = absoluteRow
                states[index] = states[index] or STATE_PROMPT_ENDED
            }
        }

        /**
         * Records command execution start.
         *
         * If no prompt record is active, an orphan command record is created so
         * the lifecycle remains represented without inventing a prompt marker.
         *
         * @param absoluteRow absolute render row where command output begins.
         */
        fun recordCommandStart(absoluteRow: Long) {
            require(absoluteRow >= 0) { "absoluteRow must be >= 0, was $absoluteRow" }
            synchronized(lock) {
                val index = attachablePromptIndexLocked()
                commandStartRows[index] = absoluteRow
                commandEndRows[index] = NO_ROW
                exitCodes[index] = NO_EXIT_CODE
                states[index] = states[index] or STATE_COMMAND_STARTED
                activeCommandIndex = index
            }
        }

        /**
         * Records command completion.
         *
         * A non-zero [exitCode] becomes a failed command range only when a
         * matching command-start marker was observed. Omitted or malformed exit
         * status remains `null` at the protocol layer and is stored as unknown.
         *
         * @param absoluteRow absolute render row where command completion was observed.
         * @param exitCode shell-reported exit code, or null if omitted/malformed.
         */
        fun recordCommandFinished(
            absoluteRow: Long,
            exitCode: Int?,
        ) {
            require(absoluteRow >= 0) { "absoluteRow must be >= 0, was $absoluteRow" }
            synchronized(lock) {
                val index = activeCommandIndex
                activeCommandIndex = NO_INDEX
                if (index == NO_INDEX || commandStartRows[index] == NO_ROW) return

                commandEndRows[index] = absoluteRow
                exitCodes[index] = exitCode ?: NO_EXIT_CODE
                states[index] = states[index] or STATE_COMMAND_FINISHED
            }
        }

        /**
         * Observes the newest live viewport bottom row and clears stale timeline
         * state if the terminal history has been destructively rewound.
         *
         * @param bottomAbsoluteRow absolute row of the live viewport bottom.
         */
        fun observeLiveBottomRow(bottomAbsoluteRow: Long) {
            require(bottomAbsoluteRow >= 0) { "bottomAbsoluteRow must be >= 0, was $bottomAbsoluteRow" }
            synchronized(lock) {
                if (lastObservedBottomRow != NO_ROW && bottomAbsoluteRow < lastObservedBottomRow) {
                    clearLocked()
                }
                lastObservedBottomRow = bottomAbsoluteRow
            }
        }

        /**
         * Returns whether [absoluteRow] has a prompt divider.
         *
         * @param absoluteRow absolute render row to query.
         * @return true when a prompt-start marker is anchored to the row.
         */
        fun hasPromptDividerAt(absoluteRow: Long): Boolean {
            require(absoluteRow >= 0) { "absoluteRow must be >= 0, was $absoluteRow" }
            synchronized(lock) {
                var index = 0
                while (index < count) {
                    if (promptStartRows[index] == absoluteRow) return true
                    index++
                }
                return false
            }
        }

        /**
         * Returns whether [absoluteRow] belongs to a failed command range.
         *
         * @param absoluteRow absolute render row to query.
         * @return true when the row is within a completed non-zero command range.
         */
        fun hasFailedCommandRailAt(absoluteRow: Long): Boolean {
            require(absoluteRow >= 0) { "absoluteRow must be >= 0, was $absoluteRow" }
            synchronized(lock) {
                return failedCommandIndexAtLocked(absoluteRow) != NO_INDEX
            }
        }

        /**
         * Copies projected shell decorations for a visible viewport.
         *
         * Existing values in [promptDividers] and [failedCommandRails] are
         * overwritten for exactly [rowCount] rows starting at [destinationOffset].
         *
         * @param firstAbsoluteRow absolute row represented by viewport row zero.
         * @param rowCount number of viewport rows to copy.
         * @param promptDividers destination flags for prompt-start dividers.
         * @param failedCommandRails destination flags for failed-command rails.
         * @param destinationOffset first destination index in both arrays.
         */
        @JvmOverloads
        fun copyViewport(
            firstAbsoluteRow: Long,
            rowCount: Int,
            promptDividers: BooleanArray,
            failedCommandRails: BooleanArray,
            destinationOffset: Int = 0,
        ) {
            require(firstAbsoluteRow >= 0) { "firstAbsoluteRow must be >= 0, was $firstAbsoluteRow" }
            require(rowCount >= 0) { "rowCount must be >= 0, was $rowCount" }
            require(destinationOffset >= 0) { "destinationOffset must be >= 0, was $destinationOffset" }
            require(destinationOffset + rowCount <= promptDividers.size) {
                "promptDividers is too small for offset=$destinationOffset rowCount=$rowCount size=${promptDividers.size}"
            }
            require(destinationOffset + rowCount <= failedCommandRails.size) {
                "failedCommandRails is too small for offset=$destinationOffset rowCount=$rowCount size=${failedCommandRails.size}"
            }

            clearViewport(promptDividers, failedCommandRails, destinationOffset, rowCount)
            if (rowCount == 0) return

            synchronized(lock) {
                val lastAbsoluteRow = firstAbsoluteRow + rowCount - 1
                var index = 0
                while (index < count) {
                    projectPromptDividerLocked(index, firstAbsoluteRow, lastAbsoluteRow, promptDividers, destinationOffset)
                    projectFailedCommandRailLocked(index, firstAbsoluteRow, lastAbsoluteRow, failedCommandRails, destinationOffset)
                    index++
                }
            }
        }

        /**
         * Clears all stored prompt and command timeline records.
         */
        fun clear() {
            synchronized(lock) {
                clearLocked()
                lastObservedBottomRow = NO_ROW
            }
        }

        private fun appendCommandLocked(): Int {
            if (count == capacity) {
                evictOldestLocked()
            }
            val index = count
            count++
            promptStartRows[index] = NO_ROW
            promptEndRows[index] = NO_ROW
            commandStartRows[index] = NO_ROW
            commandEndRows[index] = NO_ROW
            exitCodes[index] = NO_EXIT_CODE
            states[index] = STATE_EMPTY
            return index
        }

        private fun attachablePromptIndexLocked(): Int {
            val prompt = activePromptIndex
            if (prompt != NO_INDEX && commandStartRows[prompt] == NO_ROW) return prompt
            return appendCommandLocked()
        }

        private fun evictOldestLocked() {
            promptStartRows.copyInto(promptStartRows, destinationOffset = 0, startIndex = 1, endIndex = count)
            promptEndRows.copyInto(promptEndRows, destinationOffset = 0, startIndex = 1, endIndex = count)
            commandStartRows.copyInto(commandStartRows, destinationOffset = 0, startIndex = 1, endIndex = count)
            commandEndRows.copyInto(commandEndRows, destinationOffset = 0, startIndex = 1, endIndex = count)
            exitCodes.copyInto(exitCodes, destinationOffset = 0, startIndex = 1, endIndex = count)
            states.copyInto(states, destinationOffset = 0, startIndex = 1, endIndex = count)
            count--
            activePromptIndex = shiftIndexAfterEviction(activePromptIndex)
            activeCommandIndex = shiftIndexAfterEviction(activeCommandIndex)
        }

        private fun shiftIndexAfterEviction(index: Int): Int =
            when (index) {
                NO_INDEX, 0 -> NO_INDEX
                else -> index - 1
            }

        private fun failedCommandIndexAtLocked(absoluteRow: Long): Int {
            var index = 0
            while (index < count) {
                if (isFailedCommandAtLocked(index, absoluteRow)) return index
                index++
            }
            return NO_INDEX
        }

        private fun isFailedCommandAtLocked(
            index: Int,
            absoluteRow: Long,
        ): Boolean {
            if (!hasState(index, STATE_COMMAND_FINISHED)) return false
            if (exitCodes[index] <= 0) return false
            val start = commandStartRows[index]
            val end = commandEndRows[index]
            if (start == NO_ROW || end == NO_ROW) return false
            return absoluteRow in minOf(start, end)..maxOf(start, end)
        }

        private fun projectPromptDividerLocked(
            index: Int,
            firstAbsoluteRow: Long,
            lastAbsoluteRow: Long,
            promptDividers: BooleanArray,
            destinationOffset: Int,
        ) {
            val promptStart = promptStartRows[index]
            if (promptStart !in firstAbsoluteRow..lastAbsoluteRow) return
            promptDividers[destinationOffset + (promptStart - firstAbsoluteRow).toInt()] = true
        }

        private fun projectFailedCommandRailLocked(
            index: Int,
            firstAbsoluteRow: Long,
            lastAbsoluteRow: Long,
            failedCommandRails: BooleanArray,
            destinationOffset: Int,
        ) {
            if (!hasState(index, STATE_COMMAND_FINISHED)) return
            if (exitCodes[index] <= 0) return
            val start = commandStartRows[index]
            val end = commandEndRows[index]
            if (start == NO_ROW || end == NO_ROW) return

            val first = maxOf(minOf(start, end), firstAbsoluteRow)
            val last = minOf(maxOf(start, end), lastAbsoluteRow)
            if (first > last) return

            var row = first
            var destination = destinationOffset + (first - firstAbsoluteRow).toInt()
            while (row <= last) {
                failedCommandRails[destination] = true
                destination++
                row++
            }
        }

        private fun hasState(
            index: Int,
            state: Int,
        ): Boolean = states[index] and state != 0

        private fun clearLocked() {
            count = 0
            activePromptIndex = NO_INDEX
            activeCommandIndex = NO_INDEX
        }

        private companion object {
            private const val DEFAULT_CAPACITY = 4096
            private const val NO_INDEX = -1
            private const val NO_ROW = Long.MIN_VALUE
            private const val NO_EXIT_CODE = Int.MIN_VALUE

            private const val STATE_EMPTY = 0
            private const val STATE_PROMPT_STARTED = 1 shl 0
            private const val STATE_PROMPT_ENDED = 1 shl 1
            private const val STATE_COMMAND_STARTED = 1 shl 2
            private const val STATE_COMMAND_FINISHED = 1 shl 3

            private fun clearViewport(
                promptDividers: BooleanArray,
                failedCommandRails: BooleanArray,
                destinationOffset: Int,
                rowCount: Int,
            ) {
                val end = destinationOffset + rowCount
                var index = destinationOffset
                while (index < end) {
                    promptDividers[index] = false
                    failedCommandRails[index] = false
                    index++
                }
            }
        }
    }
