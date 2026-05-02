package ru.vsu.cs.diplom_kotl.ar

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import dev.romainguy.kotlin.math.Float3
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import ru.vsu.cs.diplom_kotl.data.diagnostics.ArCameraDiagnosticsLog
import ru.vsu.cs.diplom_kotl.data.model.ModelManager
import ru.vsu.cs.diplom_kotl.data.scene.PlacedObjectState
import ru.vsu.cs.diplom_kotl.data.scene.SceneState
import java.util.Locale
import kotlin.math.atan2

class ArObjectController(
    private val arSceneView: ARSceneView
) {
    private data class PlacedObject(
        val anchorNode: AnchorNode,
        val modelNode: ModelNode,
        val assetPath: String,
        var scaleFactor: Float = 1.0f,
        var rotationYDegrees: Float = 0f
    )

    private val objects = mutableListOf<PlacedObject>()
    private var selectedObject: PlacedObject? = null
    private val maxObjects = 20

    private val scaleDetector = ScaleGestureDetector(
        arSceneView.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                selectedObject?.let { o ->
                    arDiag("Жест масштаба: начало (текущий коэффициент=${fmt(o.scaleFactor)})")
                }
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val obj = selectedObject ?: return false
                val updated = (obj.scaleFactor * detector.scaleFactor).coerceIn(0.2f, 3.0f)
                obj.scaleFactor = updated
                obj.modelNode.scale = Scale(updated, updated, updated)
                val now = SystemClock.elapsedRealtime()
                if (now - lastScaleLogMs >= 450L) {
                    lastScaleLogMs = now
                    arDiag("Жест масштаба: scale=${fmt(updated)}")
                }
                return true
            }
        }
    )

    private var prevRotationAngle = 0f
    private var isRotating = false
    private var lastDragLogMs = 0L
    private var lastRotateLogMs = 0L
    private var lastScaleLogMs = 0L

    private fun arDiag(message: String) {
        ArCameraDiagnosticsLog.append(ArCameraDiagnosticsLog.SOURCE_AR, message)
    }

    private fun fmt(v: Float) = String.format(Locale.US, "%.3f", v)

    fun register(anchorNode: AnchorNode, modelNode: ModelNode, assetPath: String, event: MotionEvent) {
        if (objects.size >= maxObjects) return
        val placed = PlacedObject(
            anchorNode = anchorNode,
            modelNode = modelNode,
            assetPath = assetPath
        )
        objects += placed
        selectedObject = placed
        val p = anchorNode.worldPosition
        arDiag(
            "Модель в сцене (жесты): зарегистрирован объект №${objects.size}, asset=$assetPath, " +
                "мировая позиция ≈ (${fmt(p.x)}, ${fmt(p.y)}, ${fmt(p.z)})",
        )
        onTouch(event)
    }

    fun bindGestures() {
        arSceneView.setOnTouchListener { _, event ->
            onTouch(event)
        }
    }

    private fun onTouch(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        handleRotation(event)
        handleDrag(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            isRotating = false
        }
        return true
    }

    private fun handleDrag(event: MotionEvent) {
        val obj = selectedObject ?: return
        if (event.pointerCount != 1) return
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return

        val frame = arSceneView.frame ?: return
        val hits = frame.hitTest(event.x, event.y)
        val validHit = hits.firstOrNull(::isPlaneHit) ?: return

        obj.anchorNode.anchor?.detach()
        obj.anchorNode.anchor = validHit.createAnchor()
        val now = SystemClock.elapsedRealtime()
        if (now - lastDragLogMs >= 550L) {
            lastDragLogMs = now
            val p = obj.anchorNode.worldPosition
            arDiag(
                "Перетаскивание: объект №${objects.indexOf(obj) + 1} перепривязан к плоскости, " +
                    "позиция ≈ (${fmt(p.x)}, ${fmt(p.y)}, ${fmt(p.z)})",
            )
        }
    }

    private fun handleRotation(event: MotionEvent) {
        val obj = selectedObject ?: return
        if (event.pointerCount < 2) return
        val angle = angleBetweenTouches(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                prevRotationAngle = angle
                isRotating = true
                arDiag("Жест вращения: начало (2 пальца)")
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isRotating) return
                val delta = angle - prevRotationAngle
                prevRotationAngle = angle
                obj.rotationYDegrees += delta
                obj.modelNode.rotation = obj.modelNode.rotation + Float3(0f, delta, 0f)
                val now = SystemClock.elapsedRealtime()
                if (now - lastRotateLogMs >= 450L) {
                    lastRotateLogMs = now
                    arDiag("Вращение: Δ=${fmt(delta)}°, накопленный rotationY≈${fmt(obj.rotationYDegrees)}°")
                }
            }
        }
    }

    private fun angleBetweenTouches(event: MotionEvent): Float {
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    }

    private fun isPlaneHit(hitResult: HitResult): Boolean {
        val trackable = hitResult.trackable
        return trackable is Plane && trackable.isPoseInPolygon(hitResult.hitPose)
    }

    fun snapshot(): List<PlacedObjectState> {
        return objects.map { obj ->
            PlacedObjectState(
                assetPath = obj.assetPath,
                tx = obj.anchorNode.worldPosition.x,
                ty = obj.anchorNode.worldPosition.y,
                tz = obj.anchorNode.worldPosition.z,
                scale = obj.scaleFactor,
                rotationY = obj.rotationYDegrees
            )
        }
    }

    fun canAddObject(): Boolean = objects.size < maxObjects
    fun objectCount(): Int = objects.size

    suspend fun restore(
        state: SceneState,
        modelManager: ModelManager
    ) {
        arDiag("Восстановление сцены: в файле ${state.objects.size} сохранённых объект(ов)")
        var restored = 0
        state.objects.forEach { item ->
            val path = ModelManager.normalizeAssetPath(item.assetPath)
            val model = modelManager.getOrLoad(path) ?: run {
                arDiag("Восстановление: пропуск — нет модели для пути $path (было в сохранении: ${item.assetPath})")
                return@forEach
            }
            val modelNode = ModelNode(modelInstance = model)
            modelNode.scale = Scale(item.scale, item.scale, item.scale)
            modelNode.rotation = Float3(0f, item.rotationY, 0f)

            val anchor = arSceneView.session?.createAnchor(
                com.google.ar.core.Pose(
                    floatArrayOf(item.tx, item.ty, item.tz),
                    floatArrayOf(0f, 0f, 0f, 1f)
                )
            ) ?: return@forEach

            val anchorNode = AnchorNode(engine = arSceneView.engine, anchor = anchor)
            anchorNode.addChildNode(modelNode)
            arSceneView.addChildNode(anchorNode)
            objects += PlacedObject(anchorNode, modelNode, path, item.scale, item.rotationY)
            restored++
            val p = anchorNode.worldPosition
            arDiag(
                "Восстановлен объект №$restored: $path, scale=${fmt(item.scale)}, " +
                    "pos≈(${fmt(p.x)}, ${fmt(p.y)}, ${fmt(p.z)})",
            )
        }
        arDiag("Восстановление сцены завершено: в сцене $restored из ${state.objects.size} записей")
    }
}
