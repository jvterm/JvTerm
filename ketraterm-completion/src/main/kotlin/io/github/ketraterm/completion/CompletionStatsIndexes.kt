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

/**
 * Bounded exact-command stats index.
 *
 * This class owns exact command row compaction, mutation, sorting, and capacity
 * eviction. Thread-safety belongs to the public source that contains the index
 * so multi-index snapshots can stay coherent.
 */
internal class CommandCompletionStatsIndex(
    capacity: Int,
) {
    private val rows =
        BoundedStatsRowIndex(
            capacity = capacity,
            order = TERMINAL_COMMAND_COMPLETION_STATS_ORDER,
            keySelector = TerminalCommandCompletionStats::key,
            shouldReplace = ::isAtLeastAsRecent,
        )

    fun replaceAll(records: List<TerminalCommandCompletionStats>) = rows.replaceAll(records)

    fun snapshot(): List<TerminalCommandCompletionStats> = rows.snapshot()

    fun recordCommandResult(
        commandLine: String,
        successful: Boolean,
        profileId: String?,
        workingDirectoryUri: String?,
        usedAtEpochMillis: Long,
    ) {
        if (!isRecordableStatsEvent(commandLine, usedAtEpochMillis)) return
        mutate(commandLine, profileId, workingDirectoryUri) { previous, canonical ->
            previous.copy(
                commandLine = canonical,
                useCount = saturatedCompletionCounterIncrement(previous.useCount),
                successCount =
                    if (successful) {
                        saturatedCompletionCounterIncrement(previous.successCount)
                    } else {
                        previous.successCount
                    },
                failureCount =
                    if (successful) {
                        previous.failureCount
                    } else {
                        saturatedCompletionCounterIncrement(previous.failureCount)
                    },
                lastUsedEpochMillis = maxOf(previous.lastUsedEpochMillis, usedAtEpochMillis),
            )
        }
    }

    fun recordSuggestionFeedback(
        commandLine: String,
        feedback: TerminalCompletionFeedbackKind,
        profileId: String?,
        workingDirectoryUri: String?,
        feedbackAtEpochMillis: Long,
    ) {
        if (!isRecordableStatsEvent(commandLine, feedbackAtEpochMillis)) return
        mutate(commandLine, profileId, workingDirectoryUri) { previous, canonical ->
            previous.copy(
                commandLine = canonical,
                acceptedCount = incrementAccepted(previous.acceptedCount, feedback),
                dismissedCount = incrementDismissed(previous.dismissedCount, feedback),
                lastUsedEpochMillis = maxOf(previous.lastUsedEpochMillis, feedbackAtEpochMillis),
            )
        }
    }

    private fun mutate(
        commandLine: String,
        profileId: String?,
        workingDirectoryUri: String?,
        update: (TerminalCommandCompletionStats, String) -> TerminalCommandCompletionStats,
    ) {
        val canonical = commandLine.trim()
        val normalized = TerminalCommandCompletionStats.normalizeCommandLine(canonical)
        rows.mutate(
            key = CommandCompletionStatsKey(normalized, profileId, workingDirectoryUri),
            initialRow = {
                TerminalCommandCompletionStats(
                    commandLine = canonical,
                    normalizedCommandLine = normalized,
                    profileId = profileId,
                    workingDirectoryUri = workingDirectoryUri,
                )
            },
            update = { update(it, canonical) },
        )
    }
}

/**
 * Bounded privacy-preserving command-shape stats index.
 *
 * The index stores structural shapes only; raw positional arguments never
 * become part of shape keys or rows.
 */
internal class CommandShapeStatsIndex(
    capacity: Int,
    commandSpecs: List<TerminalCommandSpec>,
) {
    private val rows =
        BoundedStatsRowIndex(
            capacity = capacity,
            order = TERMINAL_COMMAND_SHAPE_STATS_ORDER,
            keySelector = TerminalCommandShapeStats::key,
            shouldReplace = ::isAtLeastAsRecent,
        )
    private val commandSpecs = commandSpecs.toList()

    fun replaceAll(records: List<TerminalCommandShapeStats>) = rows.replaceAll(records)

    fun snapshot(): List<TerminalCommandShapeStats> = rows.snapshot()

    fun recordCommandResult(
        commandLine: String,
        successful: Boolean,
        profileId: String?,
        workingDirectoryUri: String?,
        usedAtEpochMillis: Long,
    ) {
        if (!isRecordableStatsEvent(commandLine, usedAtEpochMillis)) return
        mutate(commandLine, profileId, workingDirectoryUri) { previous ->
            previous.copy(
                useCount = saturatedCompletionCounterIncrement(previous.useCount),
                successCount =
                    if (successful) {
                        saturatedCompletionCounterIncrement(previous.successCount)
                    } else {
                        previous.successCount
                    },
                failureCount =
                    if (successful) {
                        previous.failureCount
                    } else {
                        saturatedCompletionCounterIncrement(previous.failureCount)
                    },
                lastUsedEpochMillis = maxOf(previous.lastUsedEpochMillis, usedAtEpochMillis),
            )
        }
    }

    fun recordSuggestionFeedback(
        commandLine: String,
        feedback: TerminalCompletionFeedbackKind,
        profileId: String?,
        workingDirectoryUri: String?,
        feedbackAtEpochMillis: Long,
    ) {
        if (!isRecordableStatsEvent(commandLine, feedbackAtEpochMillis)) return
        mutate(commandLine, profileId, workingDirectoryUri) { previous ->
            previous.copy(
                acceptedCount = incrementAccepted(previous.acceptedCount, feedback),
                dismissedCount = incrementDismissed(previous.dismissedCount, feedback),
                lastUsedEpochMillis = maxOf(previous.lastUsedEpochMillis, feedbackAtEpochMillis),
            )
        }
    }

    private fun mutate(
        commandLine: String,
        profileId: String?,
        workingDirectoryUri: String?,
        update: (TerminalCommandShapeStats) -> TerminalCommandShapeStats,
    ) {
        val shape = shapeFor(commandLine) ?: return
        rows.mutate(
            key = CommandShapeStatsKey(shape.normalizedShapeKey, profileId, workingDirectoryUri),
            initialRow = {
                TerminalCommandShapeStats(
                    shape = shape,
                    profileId = profileId,
                    workingDirectoryUri = workingDirectoryUri,
                )
            },
            update = update,
        )
    }

    private fun shapeFor(commandLine: String): TerminalCommandLineShape? =
        TerminalCommandLineClassifier
            .classify(commandLine, commandSpecs)
            ?.shape
}

/**
 * Bounded source-specific feedback stats index.
 *
 * Rows are keyed by displayed-candidate metadata rather than command text so
 * path, spec, IDE, and history providers can learn independently.
 */
internal class CompletionFeedbackStatsIndex(
    capacity: Int,
) {
    private val rows =
        BoundedStatsRowIndex(
            capacity = capacity,
            order = TERMINAL_COMPLETION_FEEDBACK_STATS_ORDER,
            keySelector = TerminalCompletionFeedbackStats::key,
            shouldReplace = ::isAtLeastAsRecent,
        )

    fun replaceAll(records: List<TerminalCompletionFeedbackStats>) = rows.replaceAll(records)

    fun snapshot(): List<TerminalCompletionFeedbackStats> = rows.snapshot()

    fun recordSuggestionFeedback(
        context: TerminalCompletionFeedbackContext,
        feedback: TerminalCompletionFeedbackKind,
        profileId: String?,
        workingDirectoryUri: String?,
        feedbackAtEpochMillis: Long,
    ) {
        if (feedbackAtEpochMillis < 0L) return
        mutate(context, profileId, workingDirectoryUri) { previous ->
            previous.copy(
                acceptedCount = incrementAccepted(previous.acceptedCount, feedback),
                dismissedCount = incrementDismissed(previous.dismissedCount, feedback),
                lastUsedEpochMillis = maxOf(previous.lastUsedEpochMillis, feedbackAtEpochMillis),
            )
        }
    }

    private fun mutate(
        context: TerminalCompletionFeedbackContext,
        profileId: String?,
        workingDirectoryUri: String?,
        update: (TerminalCompletionFeedbackStats) -> TerminalCompletionFeedbackStats,
    ) {
        rows.mutate(
            key = context.key(profileId, workingDirectoryUri),
            initialRow = {
                TerminalCompletionFeedbackStats(
                    source = context.source,
                    candidateKind = context.candidateKind,
                    tokenPosition = context.tokenPosition,
                    replacementStartOffset = context.replacementStartOffset,
                    replacementEndOffset = context.replacementEndOffset,
                    profileId = profileId,
                    workingDirectoryUri = workingDirectoryUri,
                )
            },
            update = update,
        )
    }
}

private data class CommandCompletionStatsKey(
    val normalizedCommandLine: String,
    val profileId: String?,
    val workingDirectoryUri: String?,
)

private data class CommandShapeStatsKey(
    val normalizedShapeKey: String,
    val profileId: String?,
    val workingDirectoryUri: String?,
)

private data class CompletionFeedbackStatsKey(
    val source: String,
    val candidateKind: TerminalCompletionCandidateKind,
    val tokenPosition: TerminalCompletionTokenPosition,
    val replacementStartOffset: Int,
    val replacementEndOffset: Int,
    val profileId: String?,
    val workingDirectoryUri: String?,
)

private fun TerminalCommandCompletionStats.key(): CommandCompletionStatsKey =
    CommandCompletionStatsKey(
        normalizedCommandLine = normalizedCommandLine,
        profileId = profileId,
        workingDirectoryUri = workingDirectoryUri,
    )

private fun TerminalCommandShapeStats.key(): CommandShapeStatsKey =
    CommandShapeStatsKey(
        normalizedShapeKey = shape.normalizedShapeKey,
        profileId = profileId,
        workingDirectoryUri = workingDirectoryUri,
    )

private fun TerminalCompletionFeedbackStats.key(): CompletionFeedbackStatsKey =
    CompletionFeedbackStatsKey(
        source = source,
        candidateKind = candidateKind,
        tokenPosition = tokenPosition,
        replacementStartOffset = replacementStartOffset,
        replacementEndOffset = replacementEndOffset,
        profileId = profileId,
        workingDirectoryUri = workingDirectoryUri,
    )

private fun TerminalCompletionFeedbackContext.key(
    profileId: String?,
    workingDirectoryUri: String?,
): CompletionFeedbackStatsKey =
    CompletionFeedbackStatsKey(
        source = source,
        candidateKind = candidateKind,
        tokenPosition = tokenPosition,
        replacementStartOffset = replacementStartOffset,
        replacementEndOffset = replacementEndOffset,
        profileId = profileId,
        workingDirectoryUri = workingDirectoryUri,
    )

private fun isAtLeastAsRecent(
    current: TerminalCommandCompletionStats,
    candidate: TerminalCommandCompletionStats,
): Boolean = candidate.lastUsedEpochMillis >= current.lastUsedEpochMillis

private fun isAtLeastAsRecent(
    current: TerminalCommandShapeStats,
    candidate: TerminalCommandShapeStats,
): Boolean = candidate.lastUsedEpochMillis >= current.lastUsedEpochMillis

private fun isAtLeastAsRecent(
    current: TerminalCompletionFeedbackStats,
    candidate: TerminalCompletionFeedbackStats,
): Boolean = candidate.lastUsedEpochMillis >= current.lastUsedEpochMillis

private fun isRecordableStatsEvent(
    commandLine: String,
    eventAtEpochMillis: Long,
): Boolean = eventAtEpochMillis >= 0L && isRecordableTerminalCompletionCommand(commandLine)

private fun incrementAccepted(
    value: Int,
    feedback: TerminalCompletionFeedbackKind,
): Int =
    if (feedback == TerminalCompletionFeedbackKind.ACCEPTED) {
        saturatedCompletionCounterIncrement(value)
    } else {
        value
    }

private fun incrementDismissed(
    value: Int,
    feedback: TerminalCompletionFeedbackKind,
): Int =
    if (feedback == TerminalCompletionFeedbackKind.DISMISSED) {
        saturatedCompletionCounterIncrement(value)
    } else {
        value
    }
