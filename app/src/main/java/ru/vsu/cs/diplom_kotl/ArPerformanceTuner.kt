package ru.vsu.cs.diplom_kotl.ar

import com.google.ar.core.Config
import io.github.sceneview.ar.ARSceneView

class ArPerformanceTuner {
    fun configure(arSceneView: ARSceneView) {
        arSceneView.configureSession { session, config ->
            // AUTOMATIC depth часто даёт native crash на части устройств без стабильного depth.
            config.depthMode = Config.DepthMode.DISABLED
            config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
            config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            session.configure(config)
        }
    }
}
