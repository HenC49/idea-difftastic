package com.github.henc.difftastic.engine

import com.intellij.util.EnvironmentUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Locates the `difft` executable. A GUI-launched IDE on macOS does not inherit the
 * shell PATH, so common installation directories are probed in addition to PATH.
 */
object DifftLocator {
    private val LOG = Logger.getInstance(DifftLocator::class.java)

    private val EXECUTABLE_NAMES = listOf("difft", "difftastic")

    /** Cached version probe results per executable path. */
    private val versionCache = ConcurrentHashMap<String, String?>()

    data class Located(val executable: Path, val version: String?)

    /**
     * Returns the executable to use, or null when none is usable.
     * When [configuredPath] is set it wins — and a broken explicit path disables
     * auto-detection instead of silently falling back.
     */
    fun locate(configuredPath: String): Path? {
        val explicit = configuredPath.trim()
        if (explicit.isNotEmpty()) {
            val path = Paths.get(explicit).let { if (Files.isExecutable(it)) it else resolve(explicit) }
            return path.takeIf { Files.isExecutable(it) }
        }
        val home = System.getProperty("user.home")
        val searchDirs = buildList {
            EnvironmentUtil.getValue("PATH")?.split(File.pathSeparatorChar)?.forEach { if (it.isNotBlank()) add(it) }
            add("/opt/homebrew/bin")
            add("/usr/local/bin")
            add("$home/.cargo/bin")
            add("$home/bin")
        }
        for (dir in searchDirs.distinct()) {
            for (name in EXECUTABLE_NAMES) {
                val candidate = Paths.get(dir, name)
                if (Files.isExecutable(candidate)) return candidate
            }
        }
        return null
    }

    /** Resolves a bare command name against PATH (for settings without an absolute path). */
    private fun resolve(command: String): Path =
        Paths.get(command).takeIf { Files.isExecutable(it) }
            ?: locate("").takeIf { it?.fileName?.toString() == command }
            ?: Paths.get(command)

    /**
     * Runs `difft --version` and returns e.g. "0.72.0", or null when the output is
     * not recognizable. Results are cached per executable path.
     */
    fun probeVersion(executable: Path, timeoutSeconds: Int = 5): String? =
        versionCache.computeIfAbsent(executable.toString()) { runVersionProbe(executable, timeoutSeconds) }

    private fun runVersionProbe(executable: Path, timeoutSeconds: Int): String? = try {
        val process = ProcessBuilder(executable.toString(), "--version").start()
        try {
            if (!process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            val output = process.inputStream.bufferedReader().readText().trim()
            parseVersion(output).also { version ->
                if (version == null) LOG.info("Unrecognized difft --version output: ${output.take(200)}")
            }
        } finally {
            process.destroy()
        }
    } catch (t: Throwable) {
        LOG.info("difft version probe failed for $executable", t)
        null
    }

    fun parseVersion(output: String): String? {
        val firstLine = output.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        val match = Regex("(?i)difftastic\\s+v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?").find(firstLine) ?: return null
        return match.groupValues.drop(1).take(3).joinToString(".")
    }

    /** The JSON wire format this plugin understands requires difftastic >= 0.60. */
    fun isSupportedVersion(version: String): Boolean {
        val parts = version.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return false
        val major = parts[0]
        val minor = parts[1]
        return major == 0 && minor >= 60 || major in 1..1
    }

    /** Asynchronously checks the current configuration; [onResult] is invoked on EDT. */
    fun checkConfigurationAsync(configuredPath: String, onResult: (Located?) -> Unit) {
        AppExecutorUtil.getAppExecutorService().execute {
            val result = locate(configuredPath)?.let { Located(it, probeVersion(it)) }
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater { onResult(result) }
        }
    }
}
