package ru.vsu.cs.diplom_kotl.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import ru.vsu.cs.diplom_kotl.R
import ru.vsu.cs.diplom_kotl.data.auth.AuthManager
import ru.vsu.cs.diplom_kotl.data.auth.UserRole
import ru.vsu.cs.diplom_kotl.data.preferences.UserPreferencesRepository
import ru.vsu.cs.diplom_kotl.ui.main.MainShellActivity
import ru.vsu.cs.diplom_kotl.ui.onboarding.OnboardingActivity

class AuthActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager
    private lateinit var rolePairs: List<Pair<UserRole, String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
        authManager = AuthManager(this).also { it.ensureBuiltinAdminAccount() }

        rolePairs = listOf(
            UserRole.USER to getString(R.string.role_user_label),
            UserRole.STORE to getString(R.string.role_store_label),
            UserRole.ADMIN to getString(R.string.role_admin_label),
        )

        val emailLayout = findViewById<TextInputLayout>(R.id.authEmailLayout)
        val passwordLayout = findViewById<TextInputLayout>(R.id.authPasswordLayout)
        val emailInput = findViewById<TextInputEditText>(R.id.authEmail)
        val passwordInput = findViewById<TextInputEditText>(R.id.authPassword)
        val roleDropdown = findViewById<AutoCompleteTextView>(R.id.authRoleDropdown)
        val status = findViewById<android.widget.TextView>(R.id.authStatus)
        val loginButton = findViewById<MaterialButton>(R.id.authLoginButton)
        val registerButton = findViewById<MaterialButton>(R.id.authRegisterButton)
        val quickAdminButton = findViewById<MaterialButton>(R.id.authQuickAdminButton)
        val forgotPasswordButton = findViewById<MaterialButton>(R.id.authForgotPasswordButton)

        val labels = rolePairs.map { it.second }
        roleDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, labels),
        )
        roleDropdown.threshold = 1
        roleDropdown.setText(labels.first(), false)
        roleDropdown.setOnClickListener { roleDropdown.showDropDown() }
        roleDropdown.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) roleDropdown.showDropDown()
        }

        fun clearFieldErrors() {
            emailLayout.error = null
            passwordLayout.error = null
        }

        fun showStatus(message: String, isError: Boolean) {
            status.visibility = View.VISIBLE
            status.text = message
            status.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (isError) R.color.auth_error else R.color.auth_success,
                ),
            )
        }

        loginButton.setOnClickListener {
            clearFieldErrors()
            val email = emailInput.text?.toString()?.trim().orEmpty()
            val password = passwordInput.text?.toString().orEmpty()
            if (email.isEmpty()) {
                emailLayout.error = getString(R.string.auth_hint_email)
                showStatus("Укажите почту", true)
                return@setOnClickListener
            }
            if (password.length < 6) {
                passwordLayout.error = getString(R.string.auth_hint_password)
                showStatus("Пароль слишком короткий", true)
                return@setOnClickListener
            }
            val result = authManager.login(email = email, password = password)
            result.fold(
                onSuccess = { user ->
                    val prefs = UserPreferencesRepository(this)
                    if (user.role != UserRole.USER) {
                        prefs.setOnboardingComplete(true)
                    }
                    startActivity(Intent(this, MainShellActivity::class.java))
                    finish()
                },
                onFailure = { showStatus(it.message ?: "Ошибка входа", true) },
            )
        }

        registerButton.setOnClickListener {
            clearFieldErrors()
            val email = emailInput.text?.toString()?.trim().orEmpty()
            val password = passwordInput.text?.toString().orEmpty()
            val role = selectedRole()

            if (email.isEmpty()) {
                emailLayout.error = getString(R.string.auth_hint_email)
                showStatus("Укажите почту", true)
                return@setOnClickListener
            }
            if (password.length < 6) {
                passwordLayout.error = getString(R.string.auth_hint_password)
                showStatus("Пароль должен быть не короче 6 символов", true)
                return@setOnClickListener
            }

            val registerResult = authManager.register(email = email, password = password, role = role)
            registerResult.fold(
                onSuccess = {
                    val loginResult = authManager.login(email = email, password = password)
                    loginResult.fold(
                        onSuccess = { user ->
                            val prefs = UserPreferencesRepository(this)
                            if (user.role == UserRole.USER) {
                                prefs.setOnboardingComplete(false)
                                startActivity(Intent(this, OnboardingActivity::class.java))
                            } else {
                                prefs.setOnboardingComplete(true)
                                startActivity(Intent(this, MainShellActivity::class.java))
                            }
                            finish()
                        },
                        onFailure = { err ->
                            showStatus(err.message ?: "Вход после регистрации не удался", true)
                        },
                    )
                },
                onFailure = { showStatus(it.message ?: "Ошибка регистрации", true) },
            )
        }

        quickAdminButton.setOnClickListener {
            clearFieldErrors()
            emailInput.setText(AuthManager.BUILTIN_ADMIN_EMAIL)
            passwordInput.setText(AuthManager.BUILTIN_ADMIN_PASSWORD)
            roleDropdown.setText(rolePairs.first { it.first == UserRole.ADMIN }.second, false)
            val result = authManager.login(
                email = AuthManager.BUILTIN_ADMIN_EMAIL,
                password = AuthManager.BUILTIN_ADMIN_PASSWORD,
            )
            result.fold(
                onSuccess = {
                    UserPreferencesRepository(this).setOnboardingComplete(true)
                    startActivity(Intent(this, MainShellActivity::class.java))
                    finish()
                },
                onFailure = { showStatus(it.message ?: "Не удалось войти как администратор", true) },
            )
        }

        forgotPasswordButton.setOnClickListener {
            startActivity(Intent(this, PasswordRecoveryActivity::class.java))
        }
    }

    private fun selectedRole(): UserRole {
        val dropdown = findViewById<AutoCompleteTextView>(R.id.authRoleDropdown)
        val text = dropdown.text?.toString()?.trim().orEmpty()
        return rolePairs.find { it.second == text }?.first ?: UserRole.USER
    }
}
