package ru.vsu.cs.diplom_kotl.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.ar.core.ArCoreApk
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import coil.load
import ru.vsu.cs.diplom_kotl.R
import ru.vsu.cs.diplom_kotl.ar.ArObjectController
import ru.vsu.cs.diplom_kotl.ar.ArPerformanceTuner
import ru.vsu.cs.diplom_kotl.ar.FrameBitmapExtractor
import ru.vsu.cs.diplom_kotl.data.catalog.FurnitureCatalog
import ru.vsu.cs.diplom_kotl.data.diagnostics.ArCameraDiagnosticsLog
import ru.vsu.cs.diplom_kotl.data.catalog.InteriorStyle
import ru.vsu.cs.diplom_kotl.data.model.ModelManager
import ru.vsu.cs.diplom_kotl.data.scene.SceneRepository
import ru.vsu.cs.diplom_kotl.data.scene.SceneState
import ru.vsu.cs.diplom_kotl.domain.placement.PlacementPlanner
import ru.vsu.cs.diplom_kotl.domain.placement.RoomBounds
import ru.vsu.cs.diplom_kotl.presentation.ArViewModel
import ru.vsu.cs.diplom_kotl.ui.catalog.FurnitureCatalogAdapter
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ArFragment : Fragment(R.layout.fragment_ar) {

    private val viewModel: ArViewModel by activityViewModels(
        factoryProducer = { (requireActivity() as MainShellActivity).arViewModelFactory },
    )

    private var arSceneView: ARSceneView? = null
    private var modelManager: ModelManager? = null
    private var sceneRepository: SceneRepository? = null
    private var objectController: ArObjectController? = null
    private var frameBitmapExtractor: FrameBitmapExtractor? = null
    private var performanceTuner: ArPerformanceTuner? = null
    private val placementPlanner = PlacementPlanner()

    private var roomColorSamplingJob: Job? = null
    private var uiStateCollectJob: Job? = null
    private var arInitialized = false
    private var arFullSetupDone = false
    /** Защита от повторного входа в initializeArOrShowFallback (onResume + launcher могут вызвать одновременно) */
    private var arInitializing = false
    /** Защита от двойного запуска системного диалога разрешений */
    private var cameraPermissionPending = false
    private var statusText: TextView? = null
    private var selectedAssetPath: String = "catalog/models/chair.glb"
    private var pendingSelectItemId: String? = null
    private var retryArInitAfterResume = false

    private val catalogLoader by lazy { FurnitureCatalog(requireContext()) }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionPending = false
        view?.post {
            if (!isAdded || isHidden) return@post
            if (granted) {
                arLog("Разрешение CAMERA: предоставлено → initializeArOrShowFallback")
                runCatching { initializeArOrShowFallback() }
                    .onFailure { e ->
                        arLog("Ошибка инициализации после разрешения: ${e.javaClass.simpleName}: ${e.message}")
                        showFallback(getString(R.string.ar_init_failed, e.message ?: "unknown"))
                    }
            } else {
                arLog("Разрешение CAMERA: отклонено пользователем")
                showFallback(getString(R.string.ar_need_camera))
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pendingSelectItemId = requireActivity().intent.getStringExtra(MainShellActivity.EXTRA_AR_ITEM_ID)

        view.findViewById<MaterialButton>(R.id.arSavePngButton).setOnClickListener {
            savePngToCache()
        }
        view.findViewById<MaterialButton>(R.id.arShareButton).setOnClickListener {
            shareScenePng()
        }

        if (!isHidden) {
            scheduleArEntry()
        }
        arLog("onViewCreated, EXTRA_ITEM_ID=${pendingSelectItemId ?: "—"}")
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            scheduleArEntry()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            scheduleArEntry()
        }
        if (retryArInitAfterResume && hasCameraPermission()) {
            retryArInitAfterResume = false
            scheduleArEntry()
        }
    }

    private fun scheduleArEntry() {
        view?.post {
            if (!isAdded || isHidden) return@post
            tryStartArAfterPermission()
        }
    }

    private fun tryStartArAfterPermission() {
        arLog("tryStartArAfterPermission: hasCameraPermission=${hasCameraPermission()}, pending=$cameraPermissionPending, initializing=$arInitializing, fullSetupDone=$arFullSetupDone")
        if (!hasCameraPermission()) {
            if (cameraPermissionPending) {
                arLog("Диалог разрешения CAMERA уже открыт, повторный запуск отклонён")
                return
            }
            arLog("Запрос разрешения CAMERA (вкладка AR видна)")
            cameraPermissionPending = true
            runCatching { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                .onFailure { e ->
                    cameraPermissionPending = false
                    arLog("cameraPermissionLauncher.launch: ИСКЛЮЧЕНИЕ ${e.javaClass.simpleName}: ${e.message}")
                    showFallback(getString(R.string.ar_init_failed, e.message ?: e.javaClass.simpleName))
                }
            return
        }
        arLog("CAMERA уже есть, запуск initializeArOrShowFallback")
        runCatching { initializeArOrShowFallback() }
            .onFailure { e ->
                arLog("initializeArOrShowFallback исключение: ${e.javaClass.simpleName}: ${e.message}")
                showFallback(getString(R.string.ar_init_failed, e.message ?: "unknown"))
            }
    }

    private fun initializeArOrShowFallback() {
        if (!isAdded || view == null) {
            arLog("initializeArOrShowFallback: фрагмент не готов (isAdded=${isAdded}), выход")
            return
        }
        if (arInitializing) {
            arLog("initializeArOrShowFallback: инициализация уже идёт, повторный вызов отклонён")
            return
        }
        if (arFullSetupDone) {
            val root = view ?: return
            arLog("AR уже инициализирован (arFullSetupDone), только показ overlay")
            root.findViewById<ScrollView>(R.id.arFallbackScroll).visibility = View.GONE
            root.findViewById<View>(R.id.arOverlayContent).visibility = View.VISIBLE
            return
        }
        arInitializing = true
        arLog("initializeArOrShowFallback: старт")

        val ctx = requireContext()
        val arAvailability = try {
            ArCoreApk.getInstance().checkAvailability(ctx)
        } catch (e: Throwable) {
            arLog("checkAvailability: ИСКЛЮЧЕНИЕ ${e.javaClass.simpleName}: ${e.message}")
            arInitializing = false
            showFallback(getString(R.string.ar_arcore_error, e.message ?: e.javaClass.simpleName))
            return
        }
        arLog("ARCore availability: isSupported=${arAvailability.isSupported}, value=$arAvailability")
        if (!arAvailability.isSupported) {
            arLog("Устройство не поддерживает ARCore (fallback UI)")
            arInitializing = false
            showFallback(getString(R.string.ar_message_device_no_ar))
            return
        }

        try {
            val installStatus = ArCoreApk.getInstance().requestInstall(requireActivity(), true)
            arLog("ARCore requestInstall: status=$installStatus")
            when (installStatus) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    arLog("ARCore: запрошена установка, ожидание onResume")
                    retryArInitAfterResume = true
                    arInitializing = false
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                    arLog("ARCore: INSTALLED — продолжаем")
                }
            }
        } catch (_: UnavailableUserDeclinedInstallationException) {
            arLog("ARCore: пользователь отклонил установку")
            arInitializing = false
            showFallback(getString(R.string.ar_install_arcore))
            return
        } catch (_: UnavailableDeviceNotCompatibleException) {
            arLog("ARCore: устройство несовместимо с AR")
            arInitializing = false
            showFallback(getString(R.string.ar_device_not_compatible))
            return
        } catch (e: Throwable) {
            arLog("ARCore requestInstall: ИСКЛЮЧЕНИЕ ${e.javaClass.simpleName}: ${e.message}")
            arInitializing = false
            showFallback(getString(R.string.ar_arcore_error, e.message ?: e.javaClass.simpleName))
            return
        }

        val root = view ?: run {
            arLog("initializeArOrShowFallback: view стал null после requestInstall, выход")
            arInitializing = false
            return
        }
        val container = root.findViewById<FrameLayout>(R.id.arOuterContainer)
        val overlay = root.findViewById<View>(R.id.arOverlayContent)

        arLog("Создание ARSceneView — начало конструктора")
        runCatching {
            if (arSceneView == null) {
                arLog("ARSceneView: вызов конструктора (context, sharedActivity, sharedLifecycle)")
                val sv = try {
                    ARSceneView(
                        context = ctx,
                        sharedActivity = requireActivity(),
                        sharedLifecycle = viewLifecycleOwner.lifecycle,
                    )
                } catch (e: Throwable) {
                    arLog(
                        "ARSceneView: ИСКЛЮЧЕНИЕ в конструкторе — ${e.javaClass.name}: ${e.message}\n" +
                            e.stackTrace.take(6).joinToString("\n") { "  at $it" },
                    )
                    throw e
                }
                arLog("ARSceneView: конструктор завершён, добавление в контейнер")
                container.addView(
                    sv,
                    0,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                arSceneView = sv
                arLog("ARSceneView: добавлен в View-иерархию")
            }

            val sv = arSceneView ?: error("ARSceneView неожиданно null после создания")

            statusText = root.findViewById(R.id.arStatusText)
            root.findViewById<ScrollView>(R.id.arFallbackScroll).visibility = View.GONE
            overlay.visibility = View.VISIBLE

            modelManager = ModelManager(context = ctx, engine = sv.engine).also {
                arLog("ModelManager создан (Filament engine привязан к ARSceneView)")
            }
            sceneRepository = SceneRepository(context = ctx)
            objectController = ArObjectController(arSceneView = sv)
            frameBitmapExtractor = FrameBitmapExtractor(activity = requireActivity())
            performanceTuner = ArPerformanceTuner()
            objectController!!.bindGestures()
            performanceTuner!!.configure(sv)

            sv.onTouchEvent = { motionEvent, _ ->
                if (motionEvent.actionMasked == MotionEvent.ACTION_UP) {
                    val hitResult = sv.hitTestAR(
                        xPx = motionEvent.x,
                        yPx = motionEvent.y,
                        planeTypes = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING),
                    )
                    if (hitResult != null) {
                        val pose = hitResult.hitPose
                        arLog(
                            "hitTest OK: горизонтальная плоскость под точкой касания, " +
                                "якорь t=(${fmt3(pose.tx())}, ${fmt3(pose.ty())}, ${fmt3(pose.tz())}) → размещение модели",
                        )
                        placeModel(hitResult = hitResult, motionEvent = motionEvent)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }

            val catalogRecycler = root.findViewById<RecyclerView>(R.id.arCatalogRecycler)
            catalogRecycler.layoutManager = LinearLayoutManager(ctx, RecyclerView.HORIZONTAL, false)
            val adapter = FurnitureCatalogAdapter { selected ->
                selectedAssetPath = selected.assetPath
                viewModel.selectFurniture(selected)
            }
            catalogRecycler.adapter = adapter
            val allItems = catalogLoader.all()
            adapter.submit(allItems)
            pendingSelectItemId?.let { selectedId ->
                allItems.firstOrNull { it.id == selectedId }?.let { selected ->
                    selectedAssetPath = selected.assetPath
                    viewModel.selectFurniture(selected)
                }
                pendingSelectItemId = null
                runCatching { requireActivity().intent.removeExtra(MainShellActivity.EXTRA_AR_ITEM_ID) }
            }

            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    arLog("Чтение сохранённой AR-сцены с диска…")
                    val state = sceneRepository!!.load()
                    arLog("Файл сцены: ${state.objects.size} объект(ов) в данных")
                    objectController!!.restore(
                        state = state,
                        modelManager = modelManager!!,
                    )
                    statusText?.text = getString(R.string.ar_scene_loaded, objectController!!.objectCount())
                }.onFailure { e ->
                    arLog("Ошибка при восстановлении сцены: ${e.javaClass.simpleName}: ${e.message}")
                }
            }

            uiStateCollectJob?.cancel()
            uiStateCollectJob = viewLifecycleOwner.lifecycleScope.launch {
                viewModel.uiState.collect { state ->
                    val mm = modelManager ?: return@collect
                    val oc = objectController ?: return@collect
                    val st = statusText ?: return@collect
                    val preload = state.recommendations.take(2)
                    preload.forEach { item ->
                        launch(Dispatchers.IO) {
                            runCatching {
                                val inst = mm.getOrLoad(item.assetPath)
                                if (inst != null) {
                                    arLog("Предзагрузка рекомендации OK: id=${item.id}, ${item.assetPath}")
                                } else {
                                    arLog("Предзагрузка: модель недоступна id=${item.id}, path=${item.assetPath}")
                                }
                            }.onFailure { e ->
                                arLog("Предзагрузка id=${item.id}: ${e.javaClass.simpleName} ${e.message}")
                            }
                        }
                    }
                    st.text = buildString {
                        append(getString(R.string.ar_status_line, state.recommendations.size, oc.objectCount()))
                        if (state.lowLightWarning) append(" • ").append(getString(R.string.ar_low_light))
                        val palette = state.roomDominantColors
                            .take(4)
                            .joinToString(", ") { color -> String.format("#%06X", 0xFFFFFF and color) }
                        if (palette.isNotBlank()) {
                            append("\n")
                            append(getString(R.string.ar_detected_palette, palette))
                        }
                    }
                }
            }

            arInitialized = true
            arFullSetupDone = true
            arInitializing = false
            retryArInitAfterResume = false
            arLog("AR сцена готова (arFullSetupDone=true, arInitializing=false)")
        }.onFailure { e ->
            arInitializing = false
            arLog(
                "Сбой полной инициализации AR: ${e.javaClass.name}: ${e.message}\n" +
                    e.stackTrace.take(8).joinToString("\n") { "  at $it" },
            )
            showFallback(
                getString(R.string.ar_message_module_wip) + "\n\n" +
                    getString(R.string.ar_init_failed, e.message ?: e.javaClass.simpleName),
            )
        }
    }

    private fun savePngToCache() {
        val sv = arSceneView
        if (!arInitialized || sv == null) {
            arLog("savePng: AR не готов")
            Toast.makeText(requireContext(), R.string.ar_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val extractor = frameBitmapExtractor ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val bmp = extractor.capture(sv)
            if (bmp == null || !writePng(bmp, sharedFile())) {
                arLog("savePng: не удалось сохранить кадр")
                Toast.makeText(requireContext(), R.string.png_save_failed, Toast.LENGTH_SHORT).show()
            } else {
                arLog("savePng: OK")
                Toast.makeText(requireContext(), R.string.png_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareScenePng() {
        val sv = arSceneView
        if (!arInitialized || sv == null) {
            arLog("share: AR не готов")
            Toast.makeText(requireContext(), R.string.ar_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val extractor = frameBitmapExtractor ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val bmp = extractor.capture(sv)
            val file = sharedFile()
            if (bmp == null || !writePng(bmp, file)) {
                arLog("share: сбой записи PNG")
                Toast.makeText(requireContext(), R.string.share_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            arLog("share: отправка PNG")
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_scene)))
        }
    }

    private fun sharedFile(): File {
        val dir = File(requireContext().cacheDir, "share").apply { mkdirs() }
        return File(dir, "ar_scene.png")
    }

    private fun writePng(bitmap: Bitmap, file: File): Boolean {
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun onStart() {
        super.onStart()
        if (!arInitialized) return
        val sv = arSceneView ?: return
        val extractor = frameBitmapExtractor ?: return
        arLog("onStart: запуск периодического сэмплинга кадра для палитры")
        roomColorSamplingJob?.cancel()
        roomColorSamplingJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val frameBitmap = extractor.capture(sv)
                if (frameBitmap != null) {
                    viewModel.updateRoomColor(frameBitmap)
                }
                delay(1500)
            }
        }
    }

    override fun onStop() {
        val oc = objectController
        val repo = sceneRepository
        if (arInitialized && oc != null && repo != null) {
            roomColorSamplingJob?.cancel()
            arLog("onStop: сохранение сцены, объектов=${oc.objectCount()}")
            viewLifecycleOwner.lifecycleScope.launch {
                repo.save(SceneState(objects = oc.snapshot()))
            }
        }
        super.onStop()
    }

    override fun onDestroyView() {
        roomColorSamplingJob?.cancel()
        uiStateCollectJob?.cancel()
        modelManager?.clear()
        modelManager = null
        arSceneView?.let { sv ->
            (sv.parent as? ViewGroup)?.removeView(sv)
        }
        arSceneView = null
        objectController = null
        sceneRepository = null
        frameBitmapExtractor = null
        performanceTuner = null
        statusText = null
        arInitialized = false
        arFullSetupDone = false
        arInitializing = false
        cameraPermissionPending = false
        arLog("onDestroyView: AR view уничтожен, все флаги сброшены")
        super.onDestroyView()
    }

    private fun styleLabel(style: InteriorStyle): String {
        val res = when (style) {
            InteriorStyle.MODERN -> R.string.style_modern
            InteriorStyle.SCANDI -> R.string.style_scandi
            InteriorStyle.LOFT -> R.string.style_loft
            InteriorStyle.CLASSIC -> R.string.style_classic
        }
        return getString(res)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showFallback(message: String) {
        arInitialized = false
        val root = view ?: return
        val scroll = root.findViewById<ScrollView>(R.id.arFallbackScroll)
        val fallback = root.findViewById<TextView>(R.id.arFallbackMessage)
        val overlay = root.findViewById<View>(R.id.arOverlayContent)
        val demoSection = root.findViewById<LinearLayout>(R.id.arDemoProductSection)
        val demoImg = root.findViewById<ImageView>(R.id.arDemoProductImage)
        val demoTitle = root.findViewById<TextView>(R.id.arDemoProductTitle)
        val demoDetails = root.findViewById<TextView>(R.id.arDemoProductDetails)

        scroll.visibility = View.VISIBLE
        fallback.text = message
        overlay.visibility = View.GONE
        arLog("Fallback UI: ${message.take(120).replace('\n', ' ')}")

        val itemId = pendingSelectItemId
            ?: requireActivity().intent.getStringExtra(MainShellActivity.EXTRA_AR_ITEM_ID)
        val item = itemId?.let { id -> catalogLoader.all().firstOrNull { it.id == id } }
        if (item != null) {
            demoSection.visibility = View.VISIBLE
            demoTitle.text = item.title
            val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("ru-RU"))
            demoDetails.text = buildString {
                append(getString(R.string.card_style_label, styleLabel(item.style)))
                append("\n")
                val hex = String.format("#%06X", 0xFFFFFF and item.previewColor)
                append(getString(R.string.card_color_label, hex))
                append("\n")
                append(
                    getString(
                        R.string.card_dimensions_m,
                        item.widthM,
                        item.depthM,
                        item.heightM,
                    ),
                )
                append("\n")
                append(getString(R.string.card_price_value, nf.format(item.priceRub.toLong())))
            }
            val path = item.thumbnailAssetPath ?: item.galleryAssetPaths.firstOrNull()
            if (!path.isNullOrBlank()) {
                demoImg.load("file:///android_asset/$path") {
                    crossfade(true)
                    placeholder(R.drawable.ic_launcher_foreground)
                }
            } else {
                demoImg.setImageDrawable(null)
                demoImg.setBackgroundColor(item.previewColor)
            }
        } else {
            demoSection.visibility = View.GONE
        }

        root.findViewById<MaterialButton>(R.id.arFallbackBackCatalog).setOnClickListener {
            (activity as? MainShellActivity)?.openRecommendationsTab()
        }
    }

    private fun placeModel(hitResult: HitResult, motionEvent: MotionEvent) {
        val sv = arSceneView ?: return
        val mm = modelManager ?: return
        val oc = objectController ?: return
        val st = statusText ?: return

        val trackable = hitResult.trackable
        if (trackable is Plane && trackable.isPoseInPolygon(hitResult.hitPose).not()) return

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val selected = viewModel.uiState.value.selectedFurniture
                    ?: viewModel.uiState.value.recommendations.firstOrNull()
                    ?: run {
                        arLog("placeModel: нет выбранного товара и рекомендаций — отмена")
                        return@launch
                    }
                if (!oc.canAddObject()) {
                    st.text = getString(R.string.ar_limit_objects)
                    arLog("placeModel: лимит объектов на сцене")
                    return@launch
                }

                val roomBounds = RoomBounds(widthM = 4.0f, depthM = 4.0f, freeAreaM2 = 8.0f)
                if (!placementPlanner.canFit(roomBounds, selected)) {
                    st.text = getString(R.string.ar_no_fit)
                    arLog("placeModel: товар «${selected.title}» не проходит проверку размещения (canFit)")
                    return@launch
                }

                val rawAssetPath = if (selected.assetPath.isNotBlank()) selected.assetPath else selectedAssetPath
                val assetPath = ModelManager.normalizeAssetPath(rawAssetPath)
                arLog(
                    "placeModel: загрузка «${selected.title}» (id=${selected.id}), asset=$assetPath",
                )
                val model = mm.getOrLoad(assetPath) ?: run {
                    arLog("placeModel: getOrLoad вернул null для $assetPath")
                    st.text = getString(R.string.ar_model_missing)
                    Toast.makeText(requireContext(), R.string.ar_model_missing, Toast.LENGTH_LONG).show()
                    return@launch
                }
                arLog("placeModel: ModelInstance готов, создание ModelNode и AnchorNode…")
                val modelNode = ModelNode(
                    modelInstance = model,
                    scaleToUnits = 1.0f,
                    centerOrigin = Position(y = -0.5f),
                )
                val anchorNode = AnchorNode(engine = sv.engine, anchor = hitResult.createAnchor())
                anchorNode.addChildNode(modelNode)
                sv.addChildNode(anchorNode)
                arLog(
                    "placeModel: узлы добавлены в ARSceneView (дочерний ModelNode у AnchorNode), " +
                        "модель должна быть видна в камере",
                )
                oc.register(anchorNode, modelNode, assetPath, motionEvent)
                st.text = getString(R.string.ar_object_added, oc.objectCount())
                arLog("placeModel: готово, всего объектов на сцене=${oc.objectCount()}, asset=$assetPath")
            }.onFailure { e ->
                arLog("placeModel: сбой ${e.javaClass.simpleName}: ${e.message}")
                st.text = getString(R.string.ar_init_failed, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun fmt3(v: Float) = String.format(Locale.US, "%.3f", v)

    private fun arLog(message: String) {
        ArCameraDiagnosticsLog.append(ArCameraDiagnosticsLog.SOURCE_AR, message)
        Log.d("AR_DIPLOM", message)
    }
}
