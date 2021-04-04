package com.medha.barcodescannerwithmlkit.activity

import android.Manifest
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.gms.common.internal.Objects
import com.google.android.material.chip.Chip
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.medha.barcodescannerwithmlkit.R
import com.scan.sqbarcodescanner.barcode.BarcodeProcessor
import com.scan.sqbarcodescanner.camera.CameraSource
import com.scan.sqbarcodescanner.camera.CameraSourcePreview
import com.scan.sqbarcodescanner.camera.GraphicOverlay
import com.scan.sqbarcodescanner.utilities.Util
import com.scan.sqbarcodescanner.viewmodel.WorkflowModel
import kotlinx.android.synthetic.main.activity_awb_scanner.*
import java.io.IOException
import java.util.*

class AWBScannerActivity2 : AppCompatActivity(), View.OnClickListener {
    private var isPermissionRequested: Boolean = false
    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var flashButton: View? = null
    private var promptChipAnimator: AnimatorSet? = null
    private var workflowModel: WorkflowModel? = null
    private var currentWorkflowState: WorkflowModel.WorkflowState? = null
    private var promptChip: Chip? = null
    private var rescanButton: View? = null
    private var toolbar: Toolbar? = null
    private val TAG = javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_awb_scanner)
        toolbar = findViewById(R.id.toolbarScanner)
        setSupportActionBar(toolbar)
        supportActionBar?.setTitle("Barcode Scanner")
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        //to set white arrow drawable
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_action_back)
        preview = findViewById(R.id.camera_preview)
        Log.e(
            "layout_dimension",
            "," + preview?.width?.toFloat() + "," + preview?.height?.toFloat()
        )
        graphicOverlay = findViewById<GraphicOverlay>(R.id.camera_preview_graphic_overlay).apply {
            setOnClickListener(this@AWBScannerActivity2)
            cameraSource = CameraSource(this)
        }
        promptChip = findViewById(R.id.bottom_prompt_chip)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            promptChipAnimator =
                (AnimatorInflater.loadAnimator(
                    this,
                    R.animator.bottom_prompt_chip_enter
                ) as AnimatorSet).apply {
                    setTarget(promptChip)
                }
        }

        rescanButton = findViewById<View>(R.id.close_button).apply {
            setOnClickListener(this@AWBScannerActivity2)
        }
        flashButton = findViewById<View>(R.id.flash_button).apply {
            setOnClickListener(this@AWBScannerActivity2)
        }
        proceed_scanner.setSafeOnClickListener {
            awb_number_order_id.error = null
            val awb = awb_number_order_id_et.text.toString()

            if (awb.isEmpty()) {
                awb_number_order_id.setError("Enter Valid AWB")
                awb_number_order_id.requestFocus()
                return@setSafeOnClickListener
            } else {
                getOrderId(awb, false);
            }


        }
        awb_number_order_id_et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {

            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                awb_number_order_id.error = null
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }
        })

        setUpWorkflowModel()

    }

    private fun getOrderId(awbNumber: String, isFromScanner: Boolean) {
        getOrders(
            1,
            1,
            "",
            "",
            null, null,
            null,
            null,
            null,
            null,
            awbNumber,
            null, isFromScanner
        )
    }

    private fun getOrders(
        page: Int,
        perPage: Int,
        sort: String,
        sortBy: String,
        to: String?,
        from: String?,
        filter: String?,
        filterBy: String?,
        channel_id: String?,
        courierId: String?,
        search: String,
        paymentMethod: String?,
        isFromScanner: Boolean
    ) {
       // call an api / any suitable action
    }

    override fun onClick(p0: View?) {
        when (p0?.id) {
            R.id.close_button -> {
                //rescan button
//                startCameraPreview()
                if (!Util.allPermissionsGranted(this)) {
                    Util.requestRuntimePermissions(this)
                } else
                    workflowModel?.setWorkflowState(WorkflowModel.WorkflowState.DETECTING)

                awb_number_order_id.error = null

            }
            R.id.flash_button -> {
                flashButton?.let {
                    if (it.isSelected) {
                        it.isSelected = false
                        cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_OFF)
                    } else {
                        it.isSelected = true
                        cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
                    }
                }
            }
        }
    }

    private fun setUpWorkflowModel() {
        workflowModel = ViewModelProviders.of(this).get(WorkflowModel::class.java)

        // Observes the workflow state changes, if happens, update the overlay view indicators and
        // camera preview state.
        workflowModel?.workflowState?.observe(this, Observer { workflowState ->
            if (workflowState == null || Objects.equal(currentWorkflowState, workflowState)) {
                return@Observer
            }

            currentWorkflowState = workflowState
            Log.d(TAG, "Current workflow state: ${currentWorkflowState!!.name}")

            val wasPromptChipGone = promptChip?.visibility == View.GONE


            when (workflowState) {
                WorkflowModel.WorkflowState.DETECTING -> {
                    startCameraPreview()
                    promptChip?.visibility = View.VISIBLE
                    rescanButton?.visibility = View.GONE
                    promptChip?.setText(resources.getString(R.string.prompt_point_at_a_barcode))
                }
                WorkflowModel.WorkflowState.CONFIRMING -> {
                    promptChip?.visibility = View.VISIBLE
                    rescanButton?.visibility = View.GONE
                    promptChip?.setText(resources.getString(R.string.prompt_move_camera_closer))
                    startCameraPreview()
                }
                WorkflowModel.WorkflowState.SEARCHING -> {
                    promptChip?.visibility = View.VISIBLE
                    rescanButton?.visibility = View.GONE
                    promptChip?.setText(resources.getString(R.string.prompt_searching))
                    stopCameraPreview()
                }
                WorkflowModel.WorkflowState.DETECTED, WorkflowModel.WorkflowState.SEARCHED -> {
                    promptChip?.visibility = View.GONE
                    rescanButton?.visibility = View.VISIBLE
                    stopCameraPreview()
                }
                else -> {
                    promptChip?.visibility = View.GONE
                    rescanButton?.visibility = View.GONE
                }
            }

            val shouldPlayPromptChipEnteringAnimation =
                wasPromptChipGone && promptChip?.visibility == View.VISIBLE
            promptChipAnimator?.let {
                if (shouldPlayPromptChipEnteringAnimation && !it.isRunning) it.start()
            }
        })

        workflowModel?.detectedBarcode?.observe(this, Observer { barcode ->
            if (barcode != null) {
//                Toast.makeText(this, "Barcode detected successfully", Toast.LENGTH_SHORT).show()
                awb_number_order_id_et.setText(barcode.rawValue.toString())
                barcode.rawValue?.let {
                    getOrderId(it, true)
                }
            }
        })
    }

    private fun startCameraPreview() {

        val workflowModel = this.workflowModel ?: return
        val cameraSource = this.cameraSource ?: return
        if (!workflowModel.isCameraLive) {
            try {
                workflowModel.markCameraLive()
                preview?.start(cameraSource)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start camera preview!", e)
                cameraSource.release()
                this.cameraSource = null
            }
        }

    }

    private fun stopCameraPreview() {
        val workflowModel = this.workflowModel ?: return
        if (workflowModel.isCameraLive) {
            workflowModel.markCameraFrozen()
            flashButton?.isSelected = false
            preview?.stop()
        }
    }

    override fun onResume() {
        super.onResume()
        workflowModel?.markCameraFrozen()
        currentWorkflowState = WorkflowModel.WorkflowState.NOT_STARTED
        cameraSource?.setFrameProcessor(BarcodeProcessor(graphicOverlay!!, workflowModel!!))
        if (!Util.allPermissionsGranted(this)) {
            if (!isPermissionRequested)
                Util.requestRuntimePermissions(this)
            isPermissionRequested = true
        } else
            workflowModel?.setWorkflowState(WorkflowModel.WorkflowState.DETECTING)
    }

    override fun onPostResume() {
        super.onPostResume()
        //BarcodeResultFragment.dismiss(supportFragmentManager)
    }

    override fun onPause() {
        super.onPause()
        currentWorkflowState = WorkflowModel.WorkflowState.NOT_STARTED
        stopCameraPreview()
    }

    override fun onDestroy() {
        cameraSource?.release()
        cameraSource = null
        super.onDestroy()
    }


    private fun showOrderNotFoundErr() {
        //Toast.makeText(this@AWBScannerActivity2, "No Order Found", Toast.LENGTH_LONG).show()
        promptChip?.visibility = View.VISIBLE
        promptChip?.setText(resources.getString(R.string.err_order_not_found))
        awb_number_order_id.setError(resources.getString(R.string.err_order_not_found))
        awb_number_order_id.requestFocus()
    }

    private fun showOrderErr() {
        //Toast.makeText(this@AWBScannerActivity2, "Unable to get the order", Toast.LENGTH_LONG).show()
        promptChip?.visibility = View.VISIBLE
        promptChip?.setText(resources.getString(R.string.err_api_order_scanner))
        awb_number_order_id.setError(resources.getString(R.string.err_order_not_found))
        awb_number_order_id.requestFocus()
    }


    fun openOrderDetail(orderId: String?) {
        awb_number_order_id.error = null
        awb_number_order_id_et.setText("")
        /*val mIntent = Intent(this@AWBScannerActivity2, OrderDetailsActivity::class.java)
        mIntent.putExtra("order_id", orderId)
        startActivity(mIntent)*/
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0 && (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_DENIED)
        ) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                Toast.makeText(
                    this,
                    "You have denied Camera permission. Please enter ORDER ID/AWB manually to proceed",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Util.promptPermissionDeniedDialog(
                    this,
                    "You have denied Camera permission . Barcode can not work without camera permission . Go to settings or enter ORDER ID/ AWB manually"
                )
            }
        } else if (requestCode == 0 && (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED)
        ) {
            isPermissionRequested = false
            workflowModel?.setWorkflowState(WorkflowModel.WorkflowState.DETECTING)
        }

    }

    override fun onStop() {
        isPermissionRequested = false
        super.onStop()
    }

    fun View.setSafeOnClickListener(onSafeClick: (View) -> Unit) {
        val safeClickListener = SafeClickListener {
            onSafeClick(it)
        }
        setOnClickListener(safeClickListener)
    }

    internal class SafeClickListener(
        private var defaultInterval: Int = 1000,
        private val onSafeCLick: (View) -> Unit
    ) : View.OnClickListener {
        private var lastTimeClicked: Long = 0
        override fun onClick(v: View) {
            if (SystemClock.elapsedRealtime() - lastTimeClicked < defaultInterval) {
                return
            }
            lastTimeClicked = SystemClock.elapsedRealtime()
            onSafeCLick(v)
        }
    }



}
