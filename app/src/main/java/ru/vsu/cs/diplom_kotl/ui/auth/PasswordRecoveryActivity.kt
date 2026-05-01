package ru.vsu.cs.diplom_kotl.ui.auth

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import ru.vsu.cs.diplom_kotl.R
import ru.vsu.cs.diplom_kotl.data.auth.AuthManager

class PasswordRecoveryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_recovery)

        val auth = AuthManager(this)
        val emailLayout = findViewById<TextInputLayout>(R.id.recoveryEmailLayout)
        val emailInput = findViewById<TextInputEditText>(R.id.recoveryEmailInput)
        val codeLayout = findViewById<TextInputLayout>(R.id.recoveryCodeLayout)
        val codeInput = findViewById<TextInputEditText>(R.id.recoveryCodeInput)
        val passLayout = findViewById<TextInputLayout>(R.id.recoveryPasswordLayout)
        val passInput = findViewById<TextInputEditText>(R.id.recoveryNewPasswordInput)
        val status = findViewById<TextView>(R.id.recoveryStatus)

        findViewById<MaterialButton>(R.id.recoverySendCodeButton).setOnClickListener {
            emailLayout.error = null
            val email = emailInput.text?.toString()?.trim().orEmpty()
            if (email.isBlank()) {
                emailLayout.error = getString(R.string.auth_hint_email)
                return@setOnClickListener
            }
            auth.requestPasswordRecovery(email).fold(
                onSuccess = { code ->
                    status.text = getString(R.string.recovery_code_sent_demo, email, code)
                },
                onFailure = { err ->
                    status.text = err.message ?: getString(R.string.recovery_error_generic)
                },
            )
        }

        findViewById<MaterialButton>(R.id.recoveryResetButton).setOnClickListener {
            codeLayout.error = null
            passLayout.error = null
            val email = emailInput.text?.toString()?.trim().orEmpty()
            val code = codeInput.text?.toString()?.trim().orEmpty()
            val newPassword = passInput.text?.toString().orEmpty()
            if (code.isBlank()) {
                codeLayout.error = getString(R.string.recovery_code_hint)
                return@setOnClickListener
            }
            if (newPassword.length < 6) {
                passLayout.error = getString(R.string.auth_hint_password)
                return@setOnClickListener
            }
            auth.resetPassword(email, code, newPassword).fold(
                onSuccess = {
                    Toast.makeText(this, R.string.recovery_success, Toast.LENGTH_SHORT).show()
                    finish()
                },
                onFailure = { err ->
                    status.text = err.message ?: getString(R.string.recovery_error_generic)
                },
            )
        }
    }
}
