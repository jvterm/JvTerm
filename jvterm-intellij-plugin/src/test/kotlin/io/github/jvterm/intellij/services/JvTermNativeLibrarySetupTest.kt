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
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

/**
 * Tests for host-native library path selection.
 */
class JvTermNativeLibrarySetupTest {
    @Test
    fun `uses bundled Windows amd64 JNA directory when present`() {
        val home = Files.createTempDirectory("jvterm-ide-home")
        val jnaDirectory = home.resolve("lib").resolve("jna").resolve("amd64")
        Files.createDirectories(jnaDirectory)

        assertEquals(
            jnaDirectory,
            JvTermNativeLibrarySetup.jnaBootLibraryPath(home, "Windows 11", "amd64"),
        )
    }

    @Test
    fun `ignores non Windows hosts`() {
        val home = Files.createTempDirectory("jvterm-ide-home")

        assertNull(JvTermNativeLibrarySetup.jnaBootLibraryPath(home, "Linux", "amd64"))
    }
}
