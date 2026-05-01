package ru.vsu.cs.diplom_kotl.ui.onboarding

import android.content.Intent
import android.content.ActivityNotFoundException
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import ru.vsu.cs.diplom_kotl.R
import ru.vsu.cs.diplom_kotl.data.catalog.InteriorStyle
import ru.vsu.cs.diplom_kotl.data.preferences.UserPreferencesRepository
import ru.vsu.cs.diplom_kotl.domain.recommendation.RoomAnalysisService
import ru.vsu.cs.diplom_kotl.ui.main.MainShellActivity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var prefs: UserPreferencesRepository

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap != null) {
            processScannedBitmap(bitmap)
            finishOnboarding()
            return@registerForActivityResult
        }
        Toast.makeText(this, R.string.camera_scan_failed, Toast.LENGTH_SHORT).show()
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) {
            Toast.makeText(this, R.string.gallery_pick_cancelled, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        runCatching {
            decodeBitmapFromUri(uri)?.let { bmp ->
                processScannedBitmap(bmp)
                finishOnboarding()
            } ?: run {
                Toast.makeText(this, R.string.gallery_decode_failed, Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(this, R.string.camera_scan_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchRoomScanSafely()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        prefs = UserPreferencesRepository(this)

        val stepStyle = findViewById<LinearLayout>(R.id.onboardingStepStyle)
        val stepRoom = findViewById<LinearLayout>(R.id.onboardingStepRoom)

        val stylePairs = listOf(
            InteriorStyle.MODERN to getString(R.string.style_modern),
            InteriorStyle.SCANDI to getString(R.string.style_scandi),
            InteriorStyle.LOFT to getString(R.string.style_loft),
            InteriorStyle.CLASSIC to getString(R.string.style_classic),
        )
        val labels = stylePairs.map { it.second }
        val dropdown = findViewById<AutoCompleteTextView>(R.id.onboardingStyleDropdown)
        dropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))
        dropdown.threshold = 1
        dropdown.setText(labels.first(), false)
        dropdown.setOnClickListener { dropdown.showDropDown() }

        findViewById<MaterialButton>(R.id.onboardingStyleNextButton).setOnClickListener {
            val text = dropdown.text?.toString()?.trim().orEmpty()
            val pair = stylePairs.find { it.second == text }
            if (pair == null) {
                Toast.makeText(this, R.string.onboarding_pick_style, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.setPreferredStyle(pair.first)
            stepStyle.visibility = View.GONE
            stepRoom.visibility = View.VISIBLE
        }

        findViewById<MaterialButton>(R.id.onboardingLaterButton).setOnClickListener {
            finishOnboarding()
        }

        findViewById<MaterialButton>(R.id.onboardingScanButton).setOnClickListener {
            if (hasCameraPermission()) {
                launchRoomScanSafely()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchRoomScanSafely() {
        try {
            scanLauncher.launch(null)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.camera_app_not_found, Toast.LENGTH_SHORT).show()
            galleryLauncher.launch("image/*")
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.camera_launch_failed, Toast.LENGTH_SHORT).show()
            galleryLauncher.launch("image/*")
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun processScannedBitmap(bitmap: Bitmap) {
        val analysis = RoomAnalysisService().analyze(bitmap)
        prefs.saveRoomAnalysis(analysis)
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(src)
        } else {
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
    }

    private fun finishOnboarding() {
        prefs.setOnboardingComplete(true)
        startActivity(Intent(this, MainShellActivity::class.java))
        finish()
    }
}
