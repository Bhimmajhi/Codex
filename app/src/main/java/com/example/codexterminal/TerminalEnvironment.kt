package com.example.codexterminal

import android.content.Context
import java.io.File

private const val EXEC_FLAGS = "--dangerously-bypass-approvals-and-sandbox --skip-git-repo-check"

/**
 * Builds the shell environment and turns short commands into full shell commands.
 */
class TerminalEnvironment(private val context: Context) {

    val homeDir: File = context.filesDir
    private val codexHome: File = File(homeDir, "codex-home")
    private val caBundle: File = File(homeDir, "cacert.pem")
    private val prefs = context.getSharedPreferences("terminal", Context.MODE_PRIVATE)

    /** Android extracts jniLibs into this folder with execute permission. */
    val codexBinary: File = File(context.applicationInfo.nativeLibraryDir, "libcodex.so")

    /** Resolves hostnames through Android's resolver, since the Codex binary cannot. */
    private val proxy = ConnectProxy()

    init {
        codexHome.mkdirs()
        copyCaBundle()
    }

    fun variables(): Map<String, String> {
        val proxyUrl = "http://127.0.0.1:${proxy.port}"
        val vars = mutableMapOf(
            "HOME" to homeDir.path,
            "CODEX_HOME" to codexHome.path,
            "SSL_CERT_FILE" to caBundle.path,
            "HTTPS_PROXY" to proxyUrl,
            "HTTP_PROXY" to proxyUrl,
            "TMPDIR" to context.cacheDir.path,
            "PATH" to "/system/bin:/system/xbin:/vendor/bin",
        )
        savedApiKey()?.let { vars["OPENAI_API_KEY"] = it }
        return vars
    }

    fun savedApiKey(): String? = prefs.getString("api_key", null)

    fun saveApiKey(key: String) {
        prefs.edit().putString("api_key", key).apply()
    }

    fun exportApiKeyCommand(key: String): String = "export OPENAI_API_KEY=${shellQuote(key)}"

    /**
     * `codex "prompt"` runs non-interactive `codex exec`.
     * `codex-raw <args>` passes the arguments straight to the binary, e.g. `codex-raw --version`.
     * Any other line goes to the shell unchanged.
     */
    fun toShellCommand(line: String): String {
        val binary = shellQuote(codexBinary.path)
        return when {
            line == "codex" -> "echo 'Usage: codex \"your prompt\"'"
            line.startsWith("codex ") -> {
                val prompt = line.removePrefix("codex ").trim()
                "$binary exec $EXEC_FLAGS ${shellQuote(prompt)} </dev/null"
            }
            line == "codex-raw" -> "$binary --help </dev/null"
            line.startsWith("codex-raw ") -> "$binary ${line.removePrefix("codex-raw ")} </dev/null"
            else -> line
        }
    }

    fun close() {
        proxy.close()
    }

    private fun copyCaBundle() {
        context.assets.open("cacert.pem").use { input ->
            caBundle.outputStream().use { output -> input.copyTo(output) }
        }
    }
}

private fun shellQuote(text: String): String = "'" + text.replace("'", "'\\''") + "'"
