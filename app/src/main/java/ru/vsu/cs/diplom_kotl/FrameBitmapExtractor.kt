package ru.vsu.cs.diplom_kotl.ar

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FrameBitmapExtractor(
    private val activity: Activity
) {
    suspend fun capture(view: View): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null

        val location = IntArray(2)
        view.getLocationInWindow(location)
        val rect = Rect(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height
        )
        if (rect.width() <= 0 || rect.height() <= 0) return null

        val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        return suspendCancellableCoroutine { continuation ->
            PixelCopy.request(
                activity.window,
                rect,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        continuation.resume(bitmap)
                    } else {
                        continuation.resume(null)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        }
    }
}
