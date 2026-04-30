package ru.vsu.cs.diplom_kotl.data.auth

enum class UserRole {
    USER,
    STORE,
    ADMIN
}

data class UserSession(
    val userId: String,
    val role: UserRole
)

object AccessControl {
    fun canPlaceFurniture(session: UserSession): Boolean {
        return session.role == UserRole.USER ||
            session.role == UserRole.STORE ||
            session.role == UserRole.ADMIN
    }

    fun canUploadFurniture(session: UserSession): Boolean {
        return session.role == UserRole.STORE || session.role == UserRole.ADMIN
    }

    fun canModerateCatalog(session: UserSession): Boolean {
        return session.role == UserRole.ADMIN
    }
}
