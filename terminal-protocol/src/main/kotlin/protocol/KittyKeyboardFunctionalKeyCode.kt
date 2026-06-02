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
package com.gagik.terminal.protocol

/**
 * Kitty keyboard numeric codes for functional keys in the initial input slice.
 *
 * Printable Unicode scalar keys use their Unicode codepoint directly. These
 * constants cover the non-printable control-equivalent keys planned for the
 * first Kitty keyboard encoder milestone.
 */
object KittyKeyboardFunctionalKeyCode {
    /** Tab key. */
    const val TAB: Int = 0x09

    /** Main Enter key. */
    const val ENTER: Int = 0x0d

    /** Escape key. */
    const val ESCAPE: Int = 0x1b

    /** Backspace key. */
    const val BACKSPACE: Int = 0x7f
}
