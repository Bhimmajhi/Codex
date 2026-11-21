package com.example.codexterminal

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var terminal: TerminalEnvironment
    private lateinit var shell: ShellSession
    private lateinit var outputView: TextView
    private lateinit var outputScroll: ScrollView
    private lateinit var commandInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        outputView = findViewById(R.id.outputView)
        outputScroll = findViewById(R.id.outputScroll)
        commandInput = findViewById(R.id.commandInput)
        val sendButton: Button = findViewById(R.id.sendButton)

        terminal = TerminalEnvironment(this)
        shell = ShellSession(
            env = terminal.variables(),
            workDir = terminal.homeDir,
            onOutput = { text -> appendOutput(text) },
            onExit = { appendOutput("\n[shell exited]\n") },
        )

        if (!terminal.codexBinary.exists()) {
            appendOutput("Warning: Codex binary not found at ${terminal.codexBinary.path}\n")
        }
        appendOutput("Codex Terminal. Start with: codex-raw --version, then key <API_KEY>, then codex \"hello\"\n")

        sendButton.setOnClickListener { submit() }
        commandInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit()
                true
            } else {
                false
            }
        }
    }

    override fun onDestroy() {
        shell.close()
        terminal.close()
        super.onDestroy()
    }

    private fun submit() {
        val line = commandInput.text.toString().trim()
        commandInput.setText("")
        if (line.isEmpty()) return

        when {
            line == "clear" -> outputView.text = ""
            line == "key" || line.startsWith("key ") -> saveApiKey(line.removePrefix("key").trim())
            else -> {
                appendOutput("\n$ $line\n")
                shell.send(terminal.toShellCommand(line))
            }
        }
    }

    private fun saveApiKey(key: String) {
        if (key.isEmpty()) {
            appendOutput("\nUsage: key <OPENAI_API_KEY>\n")
            return
        }
        terminal.saveApiKey(key)
        shell.send(terminal.exportApiKeyCommand(key))
        appendOutput("\nAPI key saved. It is not shown on screen.\n")
    }

    private fun appendOutput(text: String) {
        outputView.append(text)
        outputScroll.post { outputScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
