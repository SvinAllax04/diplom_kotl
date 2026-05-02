package ru.vsu.cs.diplom_kotl.data.auth

import android.content.Context
import kotlin.random.Random

data class AuthUser(
    val email: String,
    val role: UserRole
)

class AuthManager(context: Context) {
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    /**
     * Готовый аккаунт администратора для демо / защиты диплома.
     * В production заменить на выдачу роли только с сервера.
     */
    fun ensureBuiltinAdminAccount() {
        val keyPassword = "user:$BUILTIN_ADMIN_EMAIL:password"
        if (prefs.getString(keyPassword, null) != null) return
        prefs.edit()
            .putString(keyPassword, BUILTIN_ADMIN_PASSWORD)
            .putString("user:$BUILTIN_ADMIN_EMAIL:role", UserRole.ADMIN.name)
            .apply()
        rememberEmailForDirectory(BUILTIN_ADMIN_EMAIL)
    }

    fun register(email: String, password: String, role: UserRole): Result<Unit> {
        if (email.isBlank() || password.length < 6) {
            return Result.failure(IllegalArgumentException("Некорректные email/пароль"))
        }
        val existing = prefs.getString("user:$email:password", null)
        if (existing != null) {
            return Result.failure(IllegalStateException("Пользователь уже существует"))
        }
        prefs.edit()
            .putString("user:$email:password", password)
            .putString("user:$email:role", role.name)
            .apply()
        rememberEmailForDirectory(email)
        return Result.success(Unit)
    }

    fun login(email: String, password: String): Result<AuthUser> {
        val saved = prefs.getString("user:$email:password", null)
            ?: return Result.failure(IllegalArgumentException("Аккаунт не найден"))
        if (saved != password) {
            return Result.failure(IllegalArgumentException("Неверный пароль"))
        }
        val role = prefs.getString("user:$email:role", UserRole.USER.name)
            ?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
            ?: UserRole.USER
        prefs.edit()
            .putString("session:email", email)
            .putString("session:role", role.name)
            .apply()
        rememberEmailForDirectory(email)
        return Result.success(AuthUser(email, role))
    }

    fun currentUser(): AuthUser? {
        val email = prefs.getString("session:email", null) ?: return null
        val role = prefs.getString("session:role", null)
            ?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
            ?: UserRole.USER
        return AuthUser(email, role)
    }

    fun logout() {
        prefs.edit()
            .remove("session:email")
            .remove("session:role")
            .apply()
    }

    /**
     * Демо-флоу восстановления:
     * генерируем код, сохраняем локально и возвращаем его для отображения пользователю.
     * Для production нужна отправка кода с сервера на реальную почту.
     */
    fun requestPasswordRecovery(email: String): Result<String> {
        val saved = prefs.getString("user:$email:password", null)
            ?: return Result.failure(IllegalArgumentException("Аккаунт с такой почтой не найден"))
        if (saved.isBlank()) {
            return Result.failure(IllegalStateException("Некорректные данные аккаунта"))
        }
        val code = (100000 + Random.nextInt(900000)).toString()
        val expiresAt = System.currentTimeMillis() + 10 * 60 * 1000L
        prefs.edit()
            .putString("recovery:$email:code", code)
            .putLong("recovery:$email:expires", expiresAt)
            .apply()
        return Result.success(code)
    }

    /**
     * Список зарегистрированных email для демо-админки (без паролей).
     * В будущем заменяется запросом к API / Room.
     */
    fun listRegisteredAccounts(): List<Pair<String, UserRole>> {
        val emails = linkedSetOf<String>()
        prefs.getString(KEY_EMAIL_INDEX, null)
            ?.split(',')
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.forEach { emails.add(it) }
        emails.add(BUILTIN_ADMIN_EMAIL.lowercase())
        return emails.sorted().map { email ->
            val roleName = prefs.getString("user:$email:role", UserRole.USER.name)
            val role = runCatching { UserRole.valueOf(roleName!!) }.getOrDefault(UserRole.USER)
            email to role
        }
    }

    private fun rememberEmailForDirectory(email: String) {
        val key = email.trim().lowercase()
        if (key.isEmpty()) return
        val existing = prefs.getString(KEY_EMAIL_INDEX, null)
            ?.split(',')
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.toMutableSet()
            ?: mutableSetOf()
        if (existing.add(key)) {
            prefs.edit().putString(KEY_EMAIL_INDEX, existing.joinToString(",")).apply()
        }
    }

    fun resetPassword(email: String, code: String, newPassword: String): Result<Unit> {
        if (newPassword.length < 6) {
            return Result.failure(IllegalArgumentException("Пароль должен быть не короче 6 символов"))
        }
        val savedCode = prefs.getString("recovery:$email:code", null)
            ?: return Result.failure(IllegalStateException("Сначала запросите recovery code"))
        val expiresAt = prefs.getLong("recovery:$email:expires", 0L)
        if (System.currentTimeMillis() > expiresAt) {
            return Result.failure(IllegalStateException("Recovery code истёк. Запросите новый"))
        }
        if (savedCode != code.trim()) {
            return Result.failure(IllegalArgumentException("Неверный recovery code"))
        }
        val hasUser = prefs.getString("user:$email:password", null) != null
        if (!hasUser) return Result.failure(IllegalArgumentException("Аккаунт не найден"))

        prefs.edit()
            .putString("user:$email:password", newPassword)
            .remove("recovery:$email:code")
            .remove("recovery:$email:expires")
            .apply()
        return Result.success(Unit)
    }

    companion object {
        const val BUILTIN_ADMIN_EMAIL = "admin@diplom.local"
        const val BUILTIN_ADMIN_PASSWORD = "admin12345"
        private const val KEY_EMAIL_INDEX = "registered_emails_csv"
    }
}
