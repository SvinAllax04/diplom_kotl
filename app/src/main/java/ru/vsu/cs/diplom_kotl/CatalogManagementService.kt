package ru.vsu.cs.diplom_kotl.data.catalog

import ru.vsu.cs.diplom_kotl.data.auth.AccessControl
import ru.vsu.cs.diplom_kotl.data.auth.UserRole
import ru.vsu.cs.diplom_kotl.data.auth.UserSession
import java.util.concurrent.CopyOnWriteArrayList

class CatalogManagementService(
    initialItems: List<FurnitureItem> = FurnitureCatalog().all()
) {
    private val items = CopyOnWriteArrayList(initialItems)

    fun listVisibleFor(session: UserSession): List<FurnitureItem> {
        return when (session.role) {
            UserRole.ADMIN -> items.toList()
            UserRole.STORE, UserRole.USER -> items.filter { it.approved }
        }
    }

    fun uploadModel(session: UserSession, item: FurnitureItem): Result<Unit> {
        if (!AccessControl.canUploadFurniture(session)) {
            return Result.failure(IllegalAccessException("Недостаточно прав для загрузки моделей"))
        }
        items += item.copy(
            uploadedByRole = session.role,
            approved = session.role == UserRole.ADMIN
        )
        return Result.success(Unit)
    }

    fun approveModel(session: UserSession, modelId: String): Result<Unit> {
        if (!AccessControl.canModerateCatalog(session)) {
            return Result.failure(IllegalAccessException("Недостаточно прав для модерации"))
        }
        val index = items.indexOfFirst { it.id == modelId }
        if (index == -1) return Result.failure(NoSuchElementException("Модель не найдена"))
        items[index] = items[index].copy(approved = true)
        return Result.success(Unit)
    }
}
