package ru.vsu.cs.diplom_kotl

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.vsu.cs.diplom_kotl.ar.ArObjectController
import ru.vsu.cs.diplom_kotl.ar.ArPerformanceTuner
import ru.vsu.cs.diplom_kotl.ar.FrameBitmapExtractor
import ru.vsu.cs.diplom_kotl.data.catalog.InteriorStyle
import ru.vsu.cs.diplom_kotl.data.model.ModelManager
import ru.vsu.cs.diplom_kotl.data.scene.SceneRepository
import ru.vsu.cs.diplom_kotl.data.scene.SceneState
import ru.vsu.cs.diplom_kotl.domain.placement.PlacementPlanner
import ru.vsu.cs.diplom_kotl.domain.placement.RoomBounds
import ru.vsu.cs.diplom_kotl.presentation.ArViewModel
import ru.vsu.cs.diplom_kotl.ui.SimpleItemSelectedListener

class MainActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val viewModel: ArViewModel by viewModels()

    private lateinit var arSceneView: ARSceneView
    private lateinit var modelManager: ModelManager
    private lateinit var sceneRepository: SceneRepository
    private lateinit var objectController: ArObjectController
    private lateinit var frameBitmapExtractor: FrameBitmapExtractor
    private lateinit var performanceTuner: ArPerformanceTuner
    private val placementPlanner = PlacementPlanner()

    private var roomColorSamplingJob: Job? = null
    private var arInitialized = false
    private lateinit var statusText: TextView
    private var selectedAssetPath: String = "models/chair.glb"
    /** После INSTALL_REQUESTED нужно снова вызвать инициализацию в onResume. */
    private var retryArInitAfterResume = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            initializeArOrShowFallback()
        } else {
            showFallback("Нужно разрешение на камеру для AR-режима.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedAssetPath = intent.getStringExtra(EXTRA_SELECTED_ASSET_PATH) ?: "models/chair.glb"

        if (hasCameraPermission().not()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        initializeArOrShowFallback()
    }

    private fun initializeArOrShowFallback() {
        val arAvailability = ArCoreApk.getInstance().checkAvailability(this)
        if (arAvailability.isSupported.not()) {
            showFallback("ARCore не поддерживается на этом устройстве/эмуляторе.")
            return
        }

        try {
            when (ArCoreApk.getInstance().requestInstall(this, true)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    retryArInitAfterResume = true
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }
        } catch (_: UnavailableUserDeclinedInstallationException) {
            showFallback("Установите или обновите Google Play Services for AR.")
            return
        } catch (_: UnavailableDeviceNotCompatibleException) {
            showFallback("Это устройство не совместимо с ARCore.")
            return
        } catch (e: Exception) {
            showFallback("ARCore: ${e.message ?: e.javaClass.simpleName}")
            return
        }

        runCatching {
            arSceneView = ARSceneView(
                context = this,
                sharedActivity = this,
                sharedLifecycle = lifecycle,
            )
            statusText = TextView(this).apply {
                textSize = 13f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#99000000"))
                setPadding(20, 16, 20, 16)
            }

            val root = FrameLayout(this)
            setContentView(
                root
            )
            root.addView(
                arSceneView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            root.addView(
                createControlPanel(),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.TOP }
            )
            root.addView(
                statusText,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM
                    bottomMargin = 24
                }
            )

            statusText.text = "Готово к AR-размещению"

            modelManager = ModelManager(context = this, engine = arSceneView.engine)
            sceneRepository = SceneRepository(context = this)
            objectController = ArObjectController(arSceneView = arSceneView)
            frameBitmapExtractor = FrameBitmapExtractor(activity = this)
            performanceTuner = ArPerformanceTuner()
            objectController.bindGestures()
            performanceTuner.configure(arSceneView)

            arSceneView.onTouchEvent = { motionEvent, _ ->
                if (motionEvent.actionMasked == MotionEvent.ACTION_UP) {
                    val hitResult = arSceneView.hitTestAR(
                        xPx = motionEvent.x,
                        yPx = motionEvent.y,
                        planeTypes = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING)
                    )
                    if (hitResult != null) {
                        placeModel(hitResult = hitResult, motionEvent = motionEvent)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }

            activityScope.launch {
                objectController.restore(
                    state = sceneRepository.load(),
                    modelManager = modelManager
                )
                statusText.text = "Сцена загружена, объектов: ${objectController.objectCount()}"
            }

            activityScope.launch {
                viewModel.uiState.collect { state ->
                    val preload = state.recommendations.take(2)
                    preload.forEach { item ->
                        launch(Dispatchers.IO) { modelManager.getOrLoad(item.assetPath) }
                    }
                    statusText.text = buildString {
                        append("Рекомендаций: ${state.recommendations.size} | ")
                        append("Объектов: ${objectController.objectCount()}/20")
                        if (state.lowLightWarning) append(" | Низкая освещенность")
                    }
                }
            }
            arInitialized = true
            retryArInitAfterResume = false
        }.onFailure {
            showFallback("Не удалось инициализировать AR-сессию: ${it.message ?: "unknown error"}")
        }
    }

    override fun onResume() {
        super.onResume()
        if (retryArInitAfterResume && hasCameraPermission()) {
            retryArInitAfterResume = false
            initializeArOrShowFallback()
        }
    }

    private fun createControlPanel(): LinearLayout {
        val styleOptions = listOf("Auto", "MODERN", "SCANDI", "LOFT", "CLASSIC")
        val styleSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                styleOptions
            )
            setSelection(0)
        }
        styleSpinner.setOnItemSelectedListener(SimpleItemSelectedListener { position ->
            val selected = when (position) {
                1 -> InteriorStyle.MODERN
                2 -> InteriorStyle.SCANDI
                3 -> InteriorStyle.LOFT
                4 -> InteriorStyle.CLASSIC
                else -> null
            }
            viewModel.setStyle(selected)
        })

        val recommendationsSwitch = Switch(this).apply {
            text = "Рекомендации"
            isChecked = true
            setOnCheckedChangeListener { _, checked ->
                viewModel.setRecommendationsEnabled(checked)
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#66000000"))
            setPadding(12, 12, 12, 12)
            addView(styleSpinner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(recommendationsSwitch)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun showFallback(message: String) {
        arInitialized = false
        setContentView(
            FrameLayout(this).apply {
                addView(
                    TextView(context).apply {
                        text = message
                        textSize = 16f
                        setPadding(48, 120, 48, 48)
                    }
                )
            }
        )
    }

    private fun placeModel(hitResult: HitResult, motionEvent: MotionEvent) {
        val trackable = hitResult.trackable
        if (trackable is Plane && trackable.isPoseInPolygon(hitResult.hitPose).not()) return

        activityScope.launch {
            val selected = viewModel.uiState.value.selectedFurniture
                ?: viewModel.uiState.value.recommendations.firstOrNull()
                ?: return@launch
            if (!objectController.canAddObject()) {
                statusText.text = "Лимит: максимум 20 объектов на сцене"
                return@launch
            }

            val roomBounds = RoomBounds(widthM = 4.0f, depthM = 4.0f, freeAreaM2 = 8.0f)
            if (!placementPlanner.canFit(roomBounds, selected)) {
                statusText.text = "Объект не помещается в доступное пространство"
                return@launch
            }

            val assetPath = if (selected.assetPath.isNotBlank()) selected.assetPath else selectedAssetPath
            val model = modelManager.getOrLoad(assetPath)
            val modelNode = ModelNode(
                modelInstance = model,
                scaleToUnits = 1.0f,
                centerOrigin = Position(y = -0.5f)
            )
            val anchorNode = AnchorNode(engine = arSceneView.engine, anchor = hitResult.createAnchor())
            anchorNode.addChildNode(modelNode)
            arSceneView.addChildNode(anchorNode)
            objectController.register(anchorNode, modelNode, assetPath, motionEvent)
            statusText.text = "Добавлен объект: ${objectController.objectCount()}/20"
        }
    }

    override fun onStart() {
        super.onStart()
        if (!arInitialized) return
        roomColorSamplingJob?.cancel()
        roomColorSamplingJob = activityScope.launch {
            while (isActive) {
                val frameBitmap = frameBitmapExtractor.capture(arSceneView)
                if (frameBitmap != null) {
                    viewModel.updateRoomColor(frameBitmap)
                }
                delay(1500)
            }
        }
    }

    override fun onStop() {
        if (!arInitialized) return
        roomColorSamplingJob?.cancel()
        super.onStop()
        activityScope.launch {
            sceneRepository.save(SceneState(objects = objectController.snapshot()))
        }
    }

    override fun onDestroy() {
        activityScope.cancel()
        if (::modelManager.isInitialized) {
            modelManager.clear()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SELECTED_ASSET_PATH = "extra_selected_asset_path"
    }
}
