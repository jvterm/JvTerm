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

private fun String.hasClassifierLineBreak(): Boolean = indexOf('\n') >= 0 || indexOf('\r') >= 0

/**
 * Privacy-preserving category for one command-line argument token.
 */
enum class TerminalCommandArgumentKind {
    /**
     * Positional value after the recognized executable and subcommand path.
     */
    POSITIONAL,

    /**
     * Value consumed by an option that accepts a separate following token.
     */
    OPTION_VALUE,

    /**
     * Positional value after the shell option terminator `--`.
     */
    OPTION_TERMINATED_POSITIONAL,
}

/**
 * Privacy-preserving structural record for one command-line argument token.
 *
 * The model deliberately does not store the argument text. For option values,
 * [optionName] stores only the normalized option name that consumed the value.
 *
 * @property kind semantic argument category.
 * @property optionName normalized option name for [TerminalCommandArgumentKind.OPTION_VALUE],
 * or `null` for positional argument kinds.
 */
data class TerminalCommandArgumentShape
    @JvmOverloads
    constructor(
        val kind: TerminalCommandArgumentKind,
        val optionName: String? = null,
    ) {
        init {
            require(optionName == null || optionName.isNotBlank()) { "optionName must be null or non-blank" }
            require(kind == TerminalCommandArgumentKind.OPTION_VALUE || optionName == null) {
                "optionName is only valid for OPTION_VALUE arguments"
            }
        }
    }

/**
 * Result of classifying a command line against command specifications.
 *
 * [shape] contains the aggregate command family. [arguments] contains one
 * privacy-preserving entry for each classified argument value token without
 * retaining raw argument text.
 *
 * @property shape aggregate command-line shape.
 * @property arguments privacy-preserving argument classifications.
 * @property matchedSpec whether the executable matched a provided command spec.
 */
data class TerminalCommandLineClassification(
    val shape: TerminalCommandLineShape,
    val arguments: List<TerminalCommandArgumentShape>,
    val matchedSpec: Boolean,
)

/**
 * Spec-aware command-line classifier used by completion ranking.
 *
 * The classifier recognizes known executable and nested subcommand paths from
 * [TerminalCommandSpec] while treating unknown positional values as private
 * arguments. It performs no I/O and never stores raw argument values.
 */
object TerminalCommandLineClassifier {
    /**
     * Classifies [commandLine] using [specs].
     *
     * Blank, multi-line, assignment-only, and missing-executable inputs return
     * `null`. Unknown executables fall back to the generic
     * [TerminalCommandLineShape.fromCommandLine] classifier with private
     * positional argument categories.
     *
     * @param commandLine full command line to classify.
     * @param specs command specs used to recognize executable and subcommand paths.
     * @return privacy-preserving classification, or `null` when no command exists.
     */
    @JvmStatic
    fun classify(
        commandLine: String,
        specs: List<TerminalCommandSpec>,
    ): TerminalCommandLineClassification? {
        if (commandLine.isBlank() || commandLine.hasClassifierLineBreak()) return null
        val tokens = TerminalCommandLineTokenizer.parse(commandLine, commandLine.length).tokens
        var tokenIndex = skipEnvironmentAssignments(tokens)
        if (tokenIndex >= tokens.size) return null

        val executableToken = normalizeToken(tokens[tokenIndex].text)
        if (executableToken.isBlank()) return null
        val rootSpec = findSpec(specs, executableToken) ?: return classifyWithoutSpec(commandLine)

        tokenIndex++
        var currentSpec = rootSpec
        val subcommands = ArrayList<String>(DEFAULT_LIST_CAPACITY)
        val optionNames = ArrayList<String>(DEFAULT_LIST_CAPACITY)
        val arguments = ArrayList<TerminalCommandArgumentShape>(DEFAULT_LIST_CAPACITY)
        var expectingOptionValue: String? = null
        var acceptingSubcommands = true
        var optionsEnabled = true

        while (tokenIndex < tokens.size) {
            val normalized = normalizeToken(tokens[tokenIndex].text)
            if (normalized.isBlank()) {
                tokenIndex++
                continue
            }

            val optionValueFor = expectingOptionValue
            when {
                optionValueFor != null -> {
                    arguments += TerminalCommandArgumentShape(TerminalCommandArgumentKind.OPTION_VALUE, optionValueFor)
                    expectingOptionValue = null
                }
                normalized == OPTION_TERMINATOR -> {
                    acceptingSubcommands = false
                    optionsEnabled = false
                }
                optionsEnabled && normalized.isOptionToken() -> {
                    val optionName = normalized.substringBefore("=")
                    optionNames += optionName
                    if (!normalized.contains("=") && optionName.requiresSeparateValue(currentSpec, subcommands, rootSpec)) {
                        expectingOptionValue = optionName
                    }
                }
                acceptingSubcommands -> {
                    val next = findSpec(currentSpec.subcommands, normalized)
                    if (next != null) {
                        subcommands += normalizeToken(next.name)
                        currentSpec = next
                    } else {
                        arguments += TerminalCommandArgumentShape(TerminalCommandArgumentKind.POSITIONAL)
                        acceptingSubcommands = false
                    }
                }
                optionsEnabled -> {
                    arguments += TerminalCommandArgumentShape(TerminalCommandArgumentKind.POSITIONAL)
                }
                else -> {
                    arguments += TerminalCommandArgumentShape(TerminalCommandArgumentKind.OPTION_TERMINATED_POSITIONAL)
                }
            }
            tokenIndex++
        }

        return TerminalCommandLineClassification(
            shape =
                TerminalCommandLineShape(
                    executable = normalizeToken(rootSpec.name),
                    subcommands = subcommands,
                    optionNames = optionNames.sorted(),
                    positionalArgumentCount =
                        arguments.count {
                            it.kind == TerminalCommandArgumentKind.POSITIONAL ||
                                it.kind == TerminalCommandArgumentKind.OPTION_TERMINATED_POSITIONAL
                        },
                    optionValueCount = arguments.count { it.kind == TerminalCommandArgumentKind.OPTION_VALUE },
                ),
            arguments = arguments,
            matchedSpec = true,
        )
    }

    private fun classifyWithoutSpec(commandLine: String): TerminalCommandLineClassification? {
        val shape = TerminalCommandLineShape.fromCommandLine(commandLine) ?: return null
        val arguments =
            buildList(shape.positionalArgumentCount + shape.optionValueCount) {
                repeat(shape.optionValueCount) {
                    add(TerminalCommandArgumentShape(TerminalCommandArgumentKind.OPTION_VALUE, optionName = UNKNOWN_OPTION_NAME))
                }
                repeat(shape.positionalArgumentCount) {
                    add(TerminalCommandArgumentShape(TerminalCommandArgumentKind.POSITIONAL))
                }
            }
        return TerminalCommandLineClassification(
            shape = shape,
            arguments = arguments,
            matchedSpec = false,
        )
    }

    private fun skipEnvironmentAssignments(tokens: List<TerminalCommandLineToken>): Int {
        var index = 0
        while (index < tokens.size && tokens[index].text.isEnvironmentAssignment()) index++
        return index
    }

    private fun String.isEnvironmentAssignment(): Boolean {
        val equalsIndex = indexOf('=')
        if (equalsIndex <= 0) return false
        val name = substring(0, equalsIndex)
        return name.all { it == '_' || it.isLetterOrDigit() } && !name.first().isDigit()
    }

    private fun String.isOptionToken(): Boolean = length > 1 && startsWith("-") && this != "-"

    private fun String.requiresSeparateValue(
        currentSpec: TerminalCommandSpec,
        subcommands: List<String>,
        rootSpec: TerminalCommandSpec,
    ): Boolean {
        val option =
            commandPath(rootSpec, subcommands).asReversed().firstNotNullOfOrNull { spec ->
                spec.options.firstOrNull { option -> option.names.any { normalizeToken(it) == this } }
            }
        return option?.requiresValue == true
    }

    private fun commandPath(
        rootSpec: TerminalCommandSpec,
        subcommands: List<String>,
    ): List<TerminalCommandSpec> {
        val path = ArrayList<TerminalCommandSpec>(subcommands.size + 1)
        path += rootSpec
        var current = rootSpec
        for (subcommand in subcommands) {
            val next = findSpec(current.subcommands, subcommand) ?: break
            path += next
            current = next
        }
        return path
    }

    private fun findSpec(
        specs: List<TerminalCommandSpec>,
        token: String,
    ): TerminalCommandSpec? =
        specs.firstOrNull { spec ->
            normalizeToken(spec.name) == token || spec.aliases.any { normalizeToken(it) == token }
        }

    private fun normalizeToken(token: String): String = token.trim().lowercase()

    private const val DEFAULT_LIST_CAPACITY = 4
    private const val OPTION_TERMINATOR = "--"
    private const val UNKNOWN_OPTION_NAME = "<unknown>"
}
