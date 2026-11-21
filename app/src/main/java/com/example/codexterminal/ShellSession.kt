package com.example.codexterminal

import android.os.Handler
import android.os.Looper
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A long-running /system/bin/sh process. Commands are written to its stdin and
 * stdout/stderr are streamed back to the UI. The shell keeps its state, so `cd`
 * and `export` carry over between commands.
 *
 * This is a plain pipe, not a pseudo-terminal, so full-screen programs such as
 * the interactive Codex TUI will not work here. Use `codex "prompt"` instead.
 */
class ShellSession(
    env: Map<String, String>,
    workDir: File,
    private val onOutput: (String) -> Unit,
    private val onExit: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val writer: ExecutorService = Executors.newSingleThreadExecutor()

    private val process: Process = ProcessBuilder("/system/bin/sh")
        .directory(workDir)
        .redirectErrorStream(true)
        .apply { environment().putAll(env) }
        .start()

    private val stdin = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))

    init {
        Thread {
            process.inputStream.reader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(4096)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    val text = String(buffer, 0, count)
                    mainHandler.post { onOutput(text) }
                }
            }
            mainHandler.post { onExit() }
        }.apply { isDaemon = true }.start()
    }

    fun send(command: String) {
        writer.execute {
            try {
                stdin.write(command)
                stdin.newLine()
                stdin.flush()
            } catch (error: IOException) {
                mainHandler.post { onOutput("\n[write failed: ${error.message}]\n") }
            }
        }
    }

    fun close() {
        writer.shutdownNow()
        process.destroy()
    }
}
