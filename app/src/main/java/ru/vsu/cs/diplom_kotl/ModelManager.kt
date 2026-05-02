package ru.vsu.cs.diplom_kotl.data.model

import android.content.Context
import com.google.android.filament.Engine
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.vsu.cs.diplom_kotl.data.diagnostics.ArCameraDiagnosticsLog
import java.util.concurrent.ConcurrentHashMap

class ModelManager(
    context: Context,
    engine: Engine,
) {
    private val appContext = context.applicationContext
    private val modelLoader = ModelLoader(context = context, engine = engine)
    private val cache = ConcurrentHashMap<String, ModelInstance>()

    private fun assetExists(path: String): Boolean =
        path.isNotBlank() && runCatching {
            appContext.assets.open(path).use { }
            true
        }.getOrDefault(false)

    /**
     * Загрузка модели из assets. При отсутствии файла или ошибке — null (без вылета приложения).
     */
    suspend fun getOrLoad(assetPath: String): ModelInstance? {
        if (assetPath.isBlank()) {
            ArCameraDiagnosticsLog.append(ArCameraDiagnosticsLog.SOURCE_AR, "Пустой assetPath модели")
            return null
        }
        cache[assetPath]?.let { return it }
        if (!assetExists(assetPath)) {
            ArCameraDiagnosticsLog.append(
                ArCameraDiagnosticsLog.SOURCE_AR,
                "Файл модели не найден в assets: $assetPath (положите .glb в app/src/main/assets/)",
            )
            return null
        }
        return runCatching {
            withContext(Dispatchers.IO) {
                cache[assetPath] ?: modelLoader.createModelInstance(assetFileLocation = assetPath).also {
                    cache[assetPath] = it
                }
            }
        }.getOrElse { e ->
            ArCameraDiagnosticsLog.append(
                ArCameraDiagnosticsLog.SOURCE_AR,
                "Ошибка загрузки модели $assetPath: ${e.javaClass.simpleName} ${e.message}",
            )
            null
        }
    }

    fun clear() {
        cache.clear()
    }
}
