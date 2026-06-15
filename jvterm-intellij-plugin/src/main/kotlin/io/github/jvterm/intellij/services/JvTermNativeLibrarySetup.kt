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

import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Configures native library lookup needed by PTY/JNA inside the IntelliJ host.
 *
 * IntelliJ distributions on Windows ship `jnidispatch.dll` under `lib/jna`
 * rather than inside the plugin's Maven JNA jar. Setting JNA's boot library
 * path before the first PTY is created lets JNA load the IDE-provided native
 * support library and avoids duplicate native extraction from the plugin
 * classloader.
 */
internal object JvTermNativeLibrarySetup {
    private const val JNA_BOOT_LIBRARY_PATH = "jna.boot.library.path"
    private val initialized = AtomicBoolean(false)

    /**
     * Installs host-native library lookup paths once per IDE process.
     */
    fun install() {
        if (!initialized.compareAndSet(false, true)) return

        val path = jnaBootLibraryPath(Path.of(PathManager.getHomePath()), System.getProperty("os.name"), System.getProperty("os.arch"))
        if (path != null && System.getProperty(JNA_BOOT_LIBRARY_PATH).isNullOrBlank()) {
            System.setProperty(JNA_BOOT_LIBRARY_PATH, path.toString())
        }
    }

    internal fun jnaBootLibraryPath(
        ideHome: Path,
        osName: String,
        osArch: String,
    ): Path? {
        if (!osName.contains("Windows", ignoreCase = true)) return null

        val architectureDirectory =
            when (osArch.lowercase()) {
                "amd64", "x86_64" -> "amd64"
                "aarch64", "arm64" -> "aarch64"
                else -> return null
            }
        val path = ideHome.resolve("lib").resolve("jna").resolve(architectureDirectory)
        return if (Files.isDirectory(path)) path else null
    }
}
