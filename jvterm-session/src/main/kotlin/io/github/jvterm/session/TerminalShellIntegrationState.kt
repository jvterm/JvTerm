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
 * Shared host-side shell integration state for prompt and command markers.
 *
 * This model intentionally lives outside terminal core. Core owns screen
 * content, cursor physics, and scrollback storage; OSC 133 shell markers are
 * out-of-band host metadata anchored to absolute render rows. The model stores
 * only bounded primitive row data and exposes viewport copies for renderers.
 *
 * All methods are thread-safe. Writers are normally called from the session
 * parser thread while readers are called by UI threads before painting.
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
        private val promptRows = LongArray(capacity)
        private var promptCount = 0

        private val failedStartRows = LongArray(capacity)
        private val failedEndRows = LongArray(capacity)
        private var failedCount = 0

        private var activeCommandStartRow = NO_ROW
        private var lastObservedBottomRow = NO_ROW

        /**
         * Records a prompt-start marker at [absoluteRow].
         *
         * @param absoluteRow absolute render row for the marker.
         */
        fun recordPromptStart(absoluteRow: Long) {
            require(absoluteRow >= 0) { "absoluteRow must be >= 0, was $absoluteRow" }
            synchronized(lock) {
                promptCount = addSortedUnique(promptRows, promptCount, absoluteRow)
            }
        }

        /**
         * Records a command-start marker at [absoluteRow].
         *
         * @param absoluteRow absolute render row where command execution begins.
         */
        fun recordCommandStart(absoluteRow: Long) {
            require(absoluteRow >= 0) { "absoluteRow must be >= 0, was $absoluteRow" }
            synchronized(lock) {
                activeCommandStartRow = absoluteRow
            }
        }

        /**
         * Records command completion and stores a failed range for non-zero exit.
         *
         * `null` and zero exit codes do not create failure decorations.
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
                val startRow = activeCommandStartRow
                activeCommandStartRow = NO_ROW
                if (startRow == NO_ROW || exitCode == null || exitCode == 0) return

                val start = minOf(startRow, absoluteRow)
                val end = maxOf(startRow, absoluteRow)
                addFailedRange(start, end)
            }
        }

        /**
         * Observes the newest live viewport bottom row and clears stale marker
         * state if terminal history has been destructively rewound.
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
         * @return true when the row has a prompt-start marker.
         */
        fun hasPromptDividerAt(absoluteRow: Long): Boolean {
            require(absoluteRow >= 0) { "absoluteRow must be >= 0, was $absoluteRow" }
            synchronized(lock) {
                return binarySearch(promptRows, promptCount, absoluteRow) >= 0
            }
        }

        /**
         * Returns whether [absoluteRow] belongs to a failed command range.
         *
         * @param absoluteRow absolute render row to query.
         * @return true when the row is within a failed-command range.
         */
        fun hasFailedCommandRailAt(absoluteRow: Long): Boolean {
            require(absoluteRow >= 0) { "absoluteRow must be >= 0, was $absoluteRow" }
            synchronized(lock) {
                return hasFailedCommandRailAtLocked(absoluteRow)
            }
        }

        /**
         * Copies shell decorations for a visible viewport into caller-owned arrays.
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

            synchronized(lock) {
                var row = 0
                while (row < rowCount) {
                    val absoluteRow = firstAbsoluteRow + row
                    val destination = destinationOffset + row
                    promptDividers[destination] = binarySearch(promptRows, promptCount, absoluteRow) >= 0
                    failedCommandRails[destination] = hasFailedCommandRailAtLocked(absoluteRow)
                    row++
                }
            }
        }

        /**
         * Clears all stored prompt and command metadata.
         */
        fun clear() {
            synchronized(lock) {
                clearLocked()
                lastObservedBottomRow = NO_ROW
            }
        }

        private fun clearLocked() {
            promptCount = 0
            failedCount = 0
            activeCommandStartRow = NO_ROW
        }

        private fun addFailedRange(
            start: Long,
            end: Long,
        ) {
            if (failedCount > 0 && failedStartRows[failedCount - 1] == start && failedEndRows[failedCount - 1] == end) {
                return
            }
            if (failedCount == capacity) {
                failedStartRows.copyInto(failedStartRows, destinationOffset = 0, startIndex = 1, endIndex = failedCount)
                failedEndRows.copyInto(failedEndRows, destinationOffset = 0, startIndex = 1, endIndex = failedCount)
                failedCount--
            }
            val insertAt = insertionPoint(failedStartRows, failedCount, start)
            if (insertAt < failedCount) {
                failedStartRows.copyInto(failedStartRows, destinationOffset = insertAt + 1, startIndex = insertAt, endIndex = failedCount)
                failedEndRows.copyInto(failedEndRows, destinationOffset = insertAt + 1, startIndex = insertAt, endIndex = failedCount)
            }
            failedStartRows[insertAt] = start
            failedEndRows[insertAt] = end
            failedCount++
        }

        private fun addSortedUnique(
            rows: LongArray,
            count: Int,
            row: Long,
        ): Int {
            val existing = binarySearch(rows, count, row)
            if (existing >= 0) return count

            var nextCount = count
            if (nextCount == capacity) {
                rows.copyInto(rows, destinationOffset = 0, startIndex = 1, endIndex = nextCount)
                nextCount--
            }
            val insertAt = insertionPoint(rows, nextCount, row)
            if (insertAt < nextCount) {
                rows.copyInto(rows, destinationOffset = insertAt + 1, startIndex = insertAt, endIndex = nextCount)
            }
            rows[insertAt] = row
            return nextCount + 1
        }

        private fun hasFailedCommandRailAtLocked(absoluteRow: Long): Boolean {
            var low = 0
            var high = failedCount - 1
            var candidate = NO_INDEX
            while (low <= high) {
                val middle = (low + high) ushr 1
                val start = failedStartRows[middle]
                if (start <= absoluteRow) {
                    candidate = middle
                    low = middle + 1
                } else {
                    high = middle - 1
                }
            }
            return candidate != NO_INDEX && absoluteRow <= failedEndRows[candidate]
        }

        private fun binarySearch(
            rows: LongArray,
            count: Int,
            row: Long,
        ): Int {
            var low = 0
            var high = count - 1
            while (low <= high) {
                val middle = (low + high) ushr 1
                val value = rows[middle]
                when {
                    value < row -> low = middle + 1
                    value > row -> high = middle - 1
                    else -> return middle
                }
            }
            return NO_INDEX
        }

        private fun insertionPoint(
            rows: LongArray,
            count: Int,
            row: Long,
        ): Int {
            var low = 0
            var high = count
            while (low < high) {
                val middle = (low + high) ushr 1
                if (rows[middle] < row) {
                    low = middle + 1
                } else {
                    high = middle
                }
            }
            return low
        }

        private companion object {
            private const val DEFAULT_CAPACITY = 4096
            private const val NO_INDEX = -1
            private const val NO_ROW = Long.MIN_VALUE
        }
    }
