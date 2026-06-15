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
package io.github.jvterm.intellij.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

/**
 * Tests for IntelliJ-specific profile defaults that do not require an IDE UI or PTY.
 */
class JvTermDefaultProfileFactoryTest {
    @Test
    fun `uses project base path as working directory`() {
        val profile = JvTermDefaultProfileFactory.defaultProfile("C:\\work\\project")

        assertEquals(Path.of("C:\\work\\project"), profile.workingDirectory)
        assertTrue(profile.command.isNotEmpty())
    }

    @Test
    fun `falls back to user home when project path is missing`() {
        val profile = JvTermDefaultProfileFactory.defaultProfile(null)

        assertEquals(Path.of(System.getProperty("user.home")), profile.workingDirectory)
        assertTrue(profile.command.isNotEmpty())
    }
}
