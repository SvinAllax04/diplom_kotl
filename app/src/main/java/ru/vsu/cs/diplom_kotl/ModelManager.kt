package ru.vsu.cs.diplom_kotl.data.model

import android.content.Context
import com.google.android.filament.Engine
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class ModelManager(
    context: Context,
    engine: Engine
) {
    private val modelLoader = ModelLoader(context = context, engine = engine)
    private val cache = ConcurrentHashMap<String, ModelInstance>()

    suspend fun getOrLoad(assetPath: String): ModelInstance {
        cache[assetPath]?.let { return it }
        return withContext(Dispatchers.IO) {
            cache[assetPath] ?: modelLoader.createModelInstance(assetFileLocation = assetPath).also {
                cache[assetPath] = it
            }
        }
    }

    fun clear() {
        cache.clear()
    }
}
