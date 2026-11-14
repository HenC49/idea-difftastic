package com.github.henc.difftastic.engine

import com.github.henc.difftastic.DifftasticNotifications
import com.github.henc.difftastic.settings.DifftasticSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Runs difftastic on two texts and returns a normalized [DifftResult].
 * Never throws (except [ProcessCanceledException]); failures become [DifftResult.Unsupported].
 */
object DifftRunner {
    private val LOG = Logger.getInstance(DifftRunner::class.java)

    fun run(leftText: String, rightText: String, fileName: String, indicator: ProgressIndicator?): DifftResult {
        val state = DifftasticSettings.getInstance().state
        if (leftText.encodeToByteArray().size > state.maxFileSizeKb * 1024L ||
            rightText.encodeToByteArray().size > state.maxFileSizeKb * 1024L
        ) {
            return DifftResult.Unsupported("file exceeds the ${state.maxFileSizeKb} KB limit configured for Difftastic")
        }

        val executable = DifftLocator.locate(state.difftPath ?: "")
        if (executable == null) {
            // The viewer passes no project here; the computer reports with a project too.
            DifftasticNotifications.reportMissingBinary(null)
            return DifftResult.Unsupported("difft executable not found")
        }

        val version = DifftLocator.probeVersion(executable)
        if (version != null && !DifftLocator.isSupportedVersion(version)) {
            return DifftResult.Unsupported("difftastic $version is too old, the JSON format this plugin uses requires >= 0.60")
        }

        val extension = sanitizeExtension(fileName)
        var leftFile: Path? = null
        var rightFile: Path? = null
        return try {
            indicator?.checkCanceled()
            leftFile = createTempFile(leftText, "difft-left", extension)
            rightFile = createTempFile(rightText, "difft-right", extension)
            executeDifft(executable, leftFile, rightFile, state.extraArgs ?: "", state.timeoutSeconds, indicator).let { raw ->
                if (raw.exitCode != 0) {
                    DifftResult.Unsupported("difft exited with code ${raw.exitCode}: ${raw.stderr.trim().take(300)}")
                } else if (raw.stdout.isBlank()) {
                    DifftResult.Unsupported("difft produced no output")
                } else {
                    try {
                        DifftResultParser.parse(raw.stdout, leftText, rightText)
                    } catch (t: Throwable) {
                        LOG.info("Unparsable difft output", t)
                        DifftResult.Unsupported("could not parse difft output: ${t.message ?: t.toString()}")
                    }
                }
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (t: Throwable) {
            LOG.warn("difft invocation failed", t)
            DifftResult.Unsupported(t.message ?: t.toString())
        } finally {
            leftFile?.let { Files.deleteIfExists(it) }
            rightFile?.let { Files.deleteIfExists(it) }
        }
    }

    private data class ProcessResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun executeDifft(
        executable: Path,
        leftFile: Path,
        rightFile: Path,
        extraArgs: String,
        timeoutSeconds: Int,
        indicator: ProgressIndicator?,
    ): ProcessResult {
        val command = GeneralCommandLine(executable.toString())
            .withEnvironment("DFT_UNSTABLE", "yes")
            .withCharset(StandardCharsets.UTF_8)
        extraArgs.trim().takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))?.forEach { command.addParameter(it) }
        command.addParameters("--display", "json", "--color", "never", leftFile.toString(), rightFile.toString())

        val process = command.createProcess()
        val stdoutFuture = CompletableFuture.supplyAsync { process.inputStream.readBytes().toString(StandardCharsets.UTF_8) }
        val stderrFuture = CompletableFuture.supplyAsync { process.errorStream.readBytes().toString(StandardCharsets.UTF_8) }
        try {
            val deadline = System.nanoTime() + timeoutSeconds * 1_000_000_000L
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                indicator?.checkCanceled()
                if (System.nanoTime() > deadline) {
                    process.destroyForcibly()
                    throw IllegalStateException("difft did not finish within ${timeoutSeconds}s")
                }
            }
            return ProcessResult(
                exitCode = process.exitValue(),
                stdout = stdoutFuture.get(5, TimeUnit.SECONDS),
                stderr = stderrFuture.get(5, TimeUnit.SECONDS),
            )
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun createTempFile(content: String, prefix: String, extension: String): Path {
        val file = FileUtil.createTempFile(prefix, ".$extension", true)
        file.writeText(content, StandardCharsets.UTF_8)
        return file.toPath()
    }

    /** Keeps difft's language detection working while preventing junk in file names. */
    private fun sanitizeExtension(fileName: String): String {
        val raw = fileName.substringAfterLast('.', "").takeIf { it.isNotEmpty() } ?: return "txt"
        val cleaned = raw.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }.take(12)
        return cleaned.ifEmpty { "txt" }
    }
}
