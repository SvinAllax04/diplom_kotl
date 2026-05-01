package ru.vsu.cs.diplom_kotl.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureCatalog
import ru.vsu.cs.diplom_kotl.data.preferences.UserPreferencesRepository

class ArViewModelFactory(
    private val appContext: Context,
    private val prefs: UserPreferencesRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ArViewModel::class.java)) {
            return ArViewModel(
                catalog = FurnitureCatalog(appContext),
                prefs = prefs,
            ) as T
        }
        error("Unknown ViewModel: ${modelClass.name}")
    }
}
