package gr.impatron.xr.ar

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import gr.impatron.xr.ARPack
import gr.impatron.xr.ARSceneData
import gr.impatron.xr.PB
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Minimal AR controller: registers card markers with ARCore's Augmented
 * Image database, then fires [onCardDetected] every time a card enters
 * full tracking. We re-arm after the user dismisses the content sheet so
 * the same card can re-trigger if the user re-aims the camera at it.
 */
class ARSceneController(
    private val sceneView: ARSceneView,
    private val context: Context,
    private val pack: ARPack,
    private val onStatusChanged: (String) -> Unit,
    private val onCardDetected: (ARSceneData) -> Unit,
) {
    companion object {
        private const val TAG = "ARSceneController"
        // 20cm wide reference card — gives ARCore a reasonable scale prior.
        private const val CARD_PHYSICAL_WIDTH = 0.2f
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastDetectedCardId: String? = null

    fun attach() {
        sceneView.planeRenderer.isEnabled = false
        sceneView.configureSession { _, config ->
            config.focusMode = Config.FocusMode.AUTO
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        }

        sceneView.onSessionUpdated = { _, frame ->
            val updated = frame.getUpdatedTrackables(AugmentedImage::class.java)
            for (img in updated) onImageUpdate(img)
        }

        onStatusChanged("Κατέβασμα markers…")
        scope.launch { setupTracking() }
    }

    private suspend fun setupTracking() {
        try {
            val session = sceneView.session ?: run {
                onStatusChanged("AR session δεν διαθέσιμη")
                return
            }
            val database = AugmentedImageDatabase(session)
            var added = 0
            for (scene in pack.scenes) {
                val url = scene.card.markerUrl ?: continue
                try {
                    val bytes = PB.download(url)
                    val bitmap = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    if (bitmap != null) {
                        database.addImage(scene.card.id, bitmap, CARD_PHYSICAL_WIDTH)
                        added++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "marker download failed for ${scene.card.id}", e)
                }
            }
            val config = session.config
            config.augmentedImageDatabase = database
            session.configure(config)
            onStatusChanged(if (added > 0) "Σάρωσε μια κάρτα" else "Δεν φορτώθηκαν markers")
        } catch (e: Exception) {
            Log.e(TAG, "setupTracking failed", e)
            onStatusChanged("Σφάλμα: ${e.message}")
        }
    }

    private fun onImageUpdate(img: AugmentedImage) {
        if (img.trackingState != TrackingState.TRACKING) return
        if (img.trackingMethod != AugmentedImage.TrackingMethod.FULL_TRACKING) return
        val scene = pack.scenes.firstOrNull { it.card.id == img.name } ?: return
        // Fire once per scan: if the user dismissed the sheet (resetDetection
        // was called), we accept the same card again.
        if (lastDetectedCardId == scene.card.id) return
        lastDetectedCardId = scene.card.id
        onCardDetected(scene)
    }

    /** Called from the UI when the user dismisses the content sheet. */
    fun resetDetection() {
        lastDetectedCardId = null
    }

    fun detach() {
        scope.coroutineContext[Job]?.cancel()
    }
}
