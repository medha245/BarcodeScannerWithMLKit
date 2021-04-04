package com.scan.sqbarcodescanner.utilities

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.Camera
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.common.images.Size
import com.google.mlkit.vision.barcode.Barcode
import com.medha.barcodescannerwithmlkit.R
import com.scan.sqbarcodescanner.camera.CameraSizePair
import com.scan.sqbarcodescanner.camera.GraphicOverlay
import java.util.*
import kotlin.math.abs

object Util {
    private val MIN_BARCODE_WIDTH = 50
    var BARCODE_RECT_WIDTH = 75
    var BARCODE_RECT_HEIGHT = 45
    var BORDER_COLOR:Int? = R.color.white
    var LASER_COLOR:Int? = R.color.white
    const val ASPECT_RATIO_TOLERANCE = 0.01f
    private val TAG = javaClass.simpleName
    private val settings = 1000

    @JvmStatic
     fun requestRuntimePermissions(activity: Activity) {

        val allNeededPermissions = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        if (allNeededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity, allNeededPermissions.toTypedArray(), /* requestCode= */ 0)
        }
    }

    @JvmStatic
    fun promptPermissionDeniedDialog(context: Activity?, msg: String) {

        if (context != null) {
            val builder = AlertDialog.Builder(context)
            builder.setTitle("Permission Denied")
            builder.setMessage(msg)
            builder.setPositiveButton("Settings") { dialogInterface, i ->  dialogInterface.dismiss()
                openSettings(context)}
            builder.show()
        }

    }

    private fun openSettings(context: Activity){
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M) {
            // pre marshmallow devices open generic settings
            val intent = Intent(Intent(android.provider.Settings.ACTION_SETTINGS))
            startActivityForResult(context, intent, 1000, null)
        }else{
            //application specific settings
            val intent = Intent()
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS

            val uri = Uri.fromParts("package", context.application.packageName, null)
            intent.setData(uri)
            startActivityForResult(context,intent,1000,null)

        }
    }

    @JvmStatic
    fun allPermissionsGranted(context: Context): Boolean = getRequiredPermissions()
        .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    private fun getRequiredPermissions(): Array<String> {
        return try {
            arrayOf(android.Manifest.permission.CAMERA)
        } catch (e: Exception) {
            arrayOf()
        }
    }

    fun isPortraitMode(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    fun getProgressToMeetBarcodeSizeRequirement(
        overlay: GraphicOverlay,
        barcode: Barcode
    ): Float {
        val reticleBoxWidth = getBarcodeReticleBox(overlay).width()
        val barcodeWidth = overlay.translateX(barcode.boundingBox?.width()?.toFloat() ?: 0f)
        val requiredWidth = reticleBoxWidth * (MIN_BARCODE_WIDTH / 100)
        return Math.min(barcodeWidth / requiredWidth,1f)
    }

    fun getBarcodeReticleBox(overlay: GraphicOverlay): RectF {
        val overlayWidth = overlay.width.toFloat()
        val overlayHeight = overlay.height.toFloat()
        val boxWidth = overlayWidth * BARCODE_RECT_WIDTH / 100
        val boxHeight = overlayHeight * BARCODE_RECT_HEIGHT / 100
        val cx = overlayWidth / 2
        val cy = overlayHeight / 2
        return RectF(cx - boxWidth / 2, cy - boxHeight / 2, cx + boxWidth / 2, cy + boxHeight / 2)
    }

    fun getBarcodeReticleBoxInt(overlay: GraphicOverlay): Rect {
        val context = overlay.context
        val overlayWidth = overlay.width.toFloat()
        val overlayHeight = overlay.height.toFloat()
        val boxWidth = overlayWidth * BARCODE_RECT_WIDTH / 100
        val boxHeight = overlayHeight * BARCODE_RECT_HEIGHT / 100
        val cx = overlayWidth / 2
        val cy = overlayHeight / 2
        return Rect((cx - boxWidth / 2).toInt(), (cy - boxHeight / 2).toInt(), (cx + boxWidth / 2).toInt(), (cy + boxHeight / 2).toInt())
    }

    /**
     * Generates a list of acceptable preview sizes. Preview sizes are not acceptable if there is not
     * a corresponding picture size of the same aspect ratio. If there is a corresponding picture size
     * of the same aspect ratio, the picture size is paired up with the preview size.
     *
     *
     * This is necessary because even if we don't use still pictures, the still picture size must
     * be set to a size that is the same aspect ratio as the preview size we choose. Otherwise, the
     * preview images may be distorted on some devices.
     */
    fun generateValidPreviewSizeList(camera: Camera): List<CameraSizePair> {
        val parameters = camera.parameters
        val supportedPreviewSizes = parameters.supportedPreviewSizes
        val supportedPictureSizes = parameters.supportedPictureSizes
        val validPreviewSizes = ArrayList<CameraSizePair>()
        for (previewSize in supportedPreviewSizes) {
            val previewAspectRatio = previewSize.width.toFloat() / previewSize.height.toFloat()

            // By looping through the picture sizes in order, we favor the higher resolutions.
            // We choose the highest resolution in order to support taking the full resolution
            // picture later.
            for (pictureSize in supportedPictureSizes) {
                val pictureAspectRatio = pictureSize.width.toFloat() / pictureSize.height.toFloat()
                if (abs(previewAspectRatio - pictureAspectRatio) < ASPECT_RATIO_TOLERANCE) {
                    validPreviewSizes.add(CameraSizePair(previewSize, pictureSize))
                    break
                }
            }
        }

        // If there are no picture sizes with the same aspect ratio as any preview sizes, allow all of
        // the preview sizes and hope that the camera can handle it.  Probably unlikely, but we still
        // account for it.
        if (validPreviewSizes.isEmpty()) {
            Log.w(TAG, "No preview sizes have a corresponding same-aspect-ratio picture size.")
            for (previewSize in supportedPreviewSizes) {
                // The null picture size will let us know that we shouldn't set a picture size.
                validPreviewSizes.add(CameraSizePair(previewSize, null))
            }
        }

        return validPreviewSizes
    }

    fun saveStringPreference(context: Context, @StringRes prefKeyId: Int, value: String?) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(context.getString(prefKeyId), value)
            .apply()
    }

    fun getCustomRectCalculatedPreviewSize(context: Context): CameraSizePair? {
        return try {
            val previewSizePrefKey = context.getString(R.string.pref_key_rear_camera_preview_size)
            val pictureSizePrefKey = context.getString(R.string.pref_key_rear_camera_picture_size)
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            CameraSizePair(
                Size.parseSize(sharedPreferences.getString(previewSizePrefKey, null)),
                Size.parseSize(sharedPreferences.getString(pictureSizePrefKey, null)))
        } catch (e: Exception) {
            null
        }
    }
}

