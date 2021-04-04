package com.scan.sqbarcodescanner.barcode

import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import androidx.annotation.MainThread
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.scan.sqbarcodescanner.camera.FrameProcessorBase
import com.scan.sqbarcodescanner.camera.GraphicOverlay
import com.scan.sqbarcodescanner.utilities.Util
import com.scan.sqbarcodescanner.viewmodel.WorkflowModel
import java.io.IOException


/** A processor to run the barcode detector.  */
class BarcodeProcessor(graphicOverlay: GraphicOverlay, private val workflowModel: WorkflowModel) :
    FrameProcessorBase<List<Barcode>>() {

    // have confirmed with enough trial and errors we use code 128 in shiprocket
    val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                    Barcode.FORMAT_CODE_128
                )
            .build()

    private val detector = BarcodeScanning.getClient(options)
    private var barcodeScanningGraphic: BarcodeScanningGraphic? = null

    override fun detectInImage(image: InputImage): Task<List<Barcode>> =
        detector.process(image)

    @MainThread
    override fun onSuccess(
        image: InputImage,
        results: List<Barcode>,
        graphicOverlay: GraphicOverlay
    ) {

        if (!workflowModel.isCameraLive) return

        Log.d(TAG, "Barcode result size: ${results.size}")
        // Picks the barcode, if exists, that covers the center of graphic overlay.
        val graphicRectangel = Util.getBarcodeReticleBox(graphicOverlay)
        if(results.size>0){
            Log.e("barcode found", "barcode: "+results.get(0).boundingBox +"graphic overlay : width : ${graphicOverlay.width} height : ${graphicOverlay.height}")
        }
        val barcodeInCenter = results.firstOrNull { barcode ->
            val boundingBox = barcode.boundingBox ?: return@firstOrNull false
            val box = graphicOverlay.translateRect(boundingBox)
            val finalBoundingBox = RectF(box.left,box.right, box.right, box.bottom)
            Log.e("barcode found", "bounding box: ${finalBoundingBox}  overlay rect : $graphicRectangel  "+box.contains(graphicRectangel))
            graphicRectangel.contains(finalBoundingBox)
        }

        graphicOverlay.clear()
        if (barcodeInCenter == null) {
            //cameraReticleAnimator.start()
            graphicOverlay.add(BarcodeScanningGraphic(graphicOverlay))
            //graphicOverlay.add(BarcodeReticleGraphic(graphicOverlay, cameraReticleAnimator))
            workflowModel.setWorkflowState(WorkflowModel.WorkflowState.DETECTING)
            Log.d(TAG, "Barcode result size: ${results.size} Barcodeincenter is null" )
        } else {
            //cameraReticleAnimator.cancel()
            val sizeProgress = Util.getProgressToMeetBarcodeSizeRequirement(graphicOverlay, barcodeInCenter)
            if (sizeProgress < 1) {
                barcodeScanningGraphic = BarcodeScanningGraphic(graphicOverlay)
                barcodeScanningGraphic?.let {
                    graphicOverlay.add(it)
                }

                // Barcode in the camera view is too small, so prompt user to move camera closer.
                //graphicOverlay.add(BarcodeConfirmingGraphic(graphicOverlay, barcodeInCenter))
                workflowModel.setWorkflowState(WorkflowModel.WorkflowState.CONFIRMING)
                Log.d(TAG, "Barcode result size: ${results.size}  sizeprogress  = $sizeProgress" )
            } else {
                // Barcode size in the camera view is sufficient.
                // if (PreferenceUtils.shouldDelayLoadingBarcodeResult(graphicOverlay.context)) {
                //val loadingAnimator = createLoadingAnimator(graphicOverlay, barcodeInCenter)
                //loadingAnimator.start()
                barcodeScanningGraphic?.let {
                    graphicOverlay.add(it)
                }

                //graphicOverlay.add(BarcodeLoadingGraphic(graphicOverlay, loadingAnimator))
                workflowModel.setWorkflowState(WorkflowModel.WorkflowState.SEARCHING)
                workflowModel.setWorkflowState(WorkflowModel.WorkflowState.DETECTED)
                workflowModel.detectedBarcode.setValue(barcodeInCenter)
                /*} else {
                    workflowModel.setWorkflowState(WorkflowModel.WorkflowState.DETECTED)
                    workflowModel.detectedBarcode.setValue(barcodeInCenter)
                }*/
                Log.d(TAG, "Barcode result size: ${results.size}  sizeprogress  = $sizeProgress" )
            }
        }
        graphicOverlay.invalidate()
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Barcode detection failed!", e)
    }

    override fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close barcode detector!", e)
        }
    }

    companion object {
        private const val TAG = "BarcodeProcessor"
    }
}
