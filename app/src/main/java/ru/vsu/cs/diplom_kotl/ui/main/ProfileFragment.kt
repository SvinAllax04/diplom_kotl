package ru.vsu.cs.diplom_kotl.ui.main

import android.content.Intent
import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import ru.vsu.cs.diplom_kotl.R
import ru.vsu.cs.diplom_kotl.data.auth.AuthManager
import ru.vsu.cs.diplom_kotl.data.catalog.InteriorStyle
import ru.vsu.cs.diplom_kotl.data.preferences.UserPreferencesRepository
import ru.vsu.cs.diplom_kotl.domain.recommendation.RoomAnalysisService
import ru.vsu.cs.diplom_kotl.presentation.ArViewModel
import ru.vsu.cs.diplom_kotl.ui.auth.AuthActivity
import ru.vsu.cs.diplom_kotl.ui.roomcapture.RoomCaptureActivity

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val prefs by lazy { UserPreferencesRepository(requireContext()) }
    private val authManager by lazy { AuthManager(requireContext()) }

    private val arViewModel: ArViewModel by activityViewModels(
        factoryProducer = { (requireActivity() as MainShellActivity).arViewModelFactory },
    )

    private val roomCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (!isAdded || result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val path = result.data?.getStringExtra(RoomCaptureActivity.EXTRA_IMAGE_PATH)
            ?: return@registerForActivityResult
        val bitmap = BitmapFactory.decodeFile(path)
        runCatching { java.io.File(path).delete() }
        if (bitmap != null) {
            runCatching { processScannedBitmap(bitmap) }.onFailure {
                Toast.makeText(requireContext(), R.string.camera_scan_failed, Toast.LENGTH_SHORT).show()
            }
        } else if (isAdded) {
            Toast.makeText(requireContext(), R.string.camera_scan_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchRoomScanSafely()
        } else if (isAdded) {
            Toast.makeText(requireContext(), R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val emailView = view.findViewById<TextView>(R.id.profileUserEmail)
        emailView.text = authManager.currentUser()?.email.orEmpty()

        val stylePairs = listOf(
            InteriorStyle.MODERN to getString(R.string.style_modern),
            InteriorStyle.SCANDI to getString(R.string.style_scandi),
            InteriorStyle.LOFT to getString(R.string.style_loft),
            InteriorStyle.CLASSIC to getString(R.string.style_classic),
        )
        val labels = stylePairs.map { it.second }
        val dropdown = view.findViewById<AutoCompleteTextView>(R.id.profileStyleDropdown)
        dropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels),
        )
        dropdown.threshold = 1
        val currentStyle = prefs.getPreferredStyle() ?: InteriorStyle.SCANDI
        dropdown.setText(stylePairs.first { it.first == currentStyle }.second, false)
        dropdown.setOnClickListener { dropdown.showDropDown() }
        dropdown.setOnItemClickListener { parent, _, position, _ ->
            val label = parent.getItemAtPosition(position) as String
            val style = stylePairs.first { it.second == label }.first
            arViewModel.setStyle(style)
        }

        val recSwitch = view.findViewById<SwitchMaterial>(R.id.profileRecSwitch)
        recSwitch.isChecked = prefs.isRecommendationsEnabled()
        recSwitch.setOnCheckedChangeListener { _, checked ->
            arViewModel.setRecommendationsEnabled(checked)
        }

        view.findViewById<MaterialButton>(R.id.profileScanRoomButton).setOnClickListener {
            if (hasCameraPermission()) {
                launchRoomScanSafely()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        renderPalette(view.findViewById(R.id.profilePaletteContainer))

        view.findViewById<MaterialButton>(R.id.profileLogoutButton).setOnClickListener {
            authManager.logout()
            startActivity(Intent(requireContext(), AuthActivity::class.java))
            requireActivity().finish()
        }
    }

    override fun onResume() {
        super.onResume()
        view?.findViewById<LinearLayout>(R.id.profilePaletteContainer)?.let { renderPalette(it) }
    }

    private fun renderPalette(container: LinearLayout) {
        container.removeAllViews()
        val colors = prefs.getRoomDominantColorsHex()
        if (colors.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = getString(R.string.profile_palette_empty)
                textSize = 13f
            }
            container.addView(empty)
            return
        }
        colors.forEach { hex ->
            val chip = TextView(requireContext()).apply {
                text = hex
                textSize = 12f
                setTextColor(android.graphics.Color.WHITE)
                setPadding(20, 10, 20, 10)
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 999f
                    setColor(runCatching { hex.toColorInt() }.getOrElse { android.graphics.Color.DKGRAY })
                }
                background = bg
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = 10 }
            container.addView(chip, lp)
        }
    }

    private fun launchRoomScanSafely() {
        roomCaptureLauncher.launch(Intent(requireContext(), RoomCaptureActivity::class.java))
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun processScannedBitmap(bitmap: Bitmap) {
        val analysis = RoomAnalysisService().analyze(bitmap)
        prefs.saveRoomAnalysis(analysis)
        arViewModel.applySavedRoomFromPrefs()
        view?.findViewById<LinearLayout>(R.id.profilePaletteContainer)?.let { renderPalette(it) }
        Toast.makeText(requireContext(), R.string.profile_scan_done, Toast.LENGTH_SHORT).show()
    }

}
