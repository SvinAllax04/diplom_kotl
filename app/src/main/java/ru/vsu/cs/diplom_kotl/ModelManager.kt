package ru.vsu.cs.diplom_kotl.data.model

import android.content.Context
import com.google.android.filament.Engine
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import ru.vsu.cs.diplom_kotl.data.diagnostics.ArCameraDiagnosticsLog
import java.util.concurrent.ConcurrentHashMap

class ModelManager(
    context: Context,
    engine: Engine,
) {
    private val appContext = context.applicationContext
    private val modelLoader = ModelLoader(context = context, engine = engine)
    private val cache = ConcurrentHashMap<String, ModelInstance>()

    companion object {
        /**
         * Старые версии приложения и сохранённые сцены ссылались на пути вроде `models/chair.glb` без префикса `catalog/`.
         */
        fun normalizeAssetPath(path: String): String {
            val p = path.trim()
            if (p.isEmpty()) return p
            if (!p.startsWith("catalog/") && p.startsWith("models/")) return "catalog/$p"
            return p
        }
    }

    private fun assetExists(path: String): Boolean =
        path.isNotBlank() && runCatching {
            appContext.assets.open(path).use { }
            true
        }.getOrDefault(false)

    /**
     * Загрузка модели из assets. При отсутствии файла или ошибке — null (без вылета приложения).
     */
    fun getOrLoad(assetPath: String): ModelInstance? {
        val path = normalizeAssetPath(assetPath)
        if (path.isBlank()) {
            ArCameraDiagnosticsLog.append(ArCameraDiagnosticsLog.SOURCE_AR, "Пустой assetPath модели")
            return null
        }
        if (path != assetPath.trim()) {
            ArCameraDiagnosticsLog.append(
                ArCameraDiagnosticsLog.SOURCE_AR,
                "Путь модели нормализован: $assetPath → $path",
            )
        }
        cache[path]?.let { cached ->
            ArCameraDiagnosticsLog.append(
                ArCameraDiagnosticsLog.SOURCE_AR,
                "Модель из кэша (повторная загрузка не нужна): $path",
            )
            return cached
        }
        if (!assetExists(path)) {
            ArCameraDiagnosticsLog.append(
                ArCameraDiagnosticsLog.SOURCE_AR,
                "Файл модели не найден в assets: $path (положите .glb в app/src/main/assets/)",
            )
            return null
        }
        ArCameraDiagnosticsLog.append(
            ArCameraDiagnosticsLog.SOURCE_AR,
            "Загрузка .glb из assets (ModelLoader.createModelInstance): $path",
        )
        // ВАЖНО: createModelInstance обращается к Filament-движку, который требует
        // вызова с главного (или GL) потока. withContext(Dispatchers.IO) здесь убьёт
        // приложение через SIGABRT. ModelLoader сам управляет внутренним threading.
        return runCatching {
            cache[path]?.let { return it }
            val instance = modelLoader.createModelInstance(assetFileLocation = path)
            cache[path] = instance
            ArCameraDiagnosticsLog.append(
                ArCameraDiagnosticsLog.SOURCE_AR,
                "Модель создана и помещена в кэш: $path",
            )
            instance
        }.getOrElse { e ->
            ArCameraDiagnosticsLog.append(
                ArCameraDiagnosticsLog.SOURCE_AR,
                "Ошибка загрузки модели $path: ${e.javaClass.simpleName} ${e.message}",
            )
            null
        }
    }

    fun clear() {
        val n = cache.size
        cache.clear()
        if (n > 0) {
            ArCameraDiagnosticsLog.append(
                ArCameraDiagnosticsLog.SOURCE_AR,
                "ModelManager.clear: очищен кэш моделей ($n записей)",
            )
        }
    }
}
