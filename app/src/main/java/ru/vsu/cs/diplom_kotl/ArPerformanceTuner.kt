package ru.vsu.cs.diplom_kotl.ar

import com.google.ar.core.Config
import io.github.sceneview.ar.ARSceneView

class ArPerformanceTuner {
    fun configure(arSceneView: ARSceneView) {
        arSceneView.configureSession { session, config ->
            config.depthMode = Config.DepthMode.AUTOMATIC
            config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            session.configure(config)
        }
    }
}
