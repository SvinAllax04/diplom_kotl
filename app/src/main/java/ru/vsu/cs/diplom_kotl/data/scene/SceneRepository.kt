package ru.vsu.cs.diplom_kotl.data.scene

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class SceneRepository(
    context: Context
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    private val file = File(context.filesDir, "scene_state.json")

    suspend fun save(state: SceneState) = withContext(Dispatchers.IO) {
        file.writeText(json.encodeToString(SceneState.serializer(), state))
    }

    suspend fun load(): SceneState = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext SceneState()
        json.decodeFromString(SceneState.serializer(), file.readText())
    }
}
