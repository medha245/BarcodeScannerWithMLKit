package com.scan.sqbarcodescanner.barcode


import android.graphics.*
import androidx.core.content.ContextCompat
import com.medha.barcodescannerwithmlkit.R
import com.scan.sqbarcodescanner.camera.GraphicOverlay
import com.scan.sqbarcodescanner.utilities.Util


internal class BarcodeScanningGraphic(graphicOverlay: GraphicOverlay) :
    GraphicOverlay.Graphic(graphicOverlay) {
    private var mDefaultLaserColor: Int? = Util.LASER_COLOR
    private var mDefaultBorderColor: Int? = Util.BORDER_COLOR
    private var scannerAlpha = 0
    private var graphicOverlay: GraphicOverlay? = null
    private var mBorderLineLength = 100
    private var overlayRect: Rect? = null

    private val mBorderPaint: Paint = Paint().apply {
        if (mDefaultBorderColor != null) {
            color = mDefaultBorderColor!!
        }
        style = Paint.Style.STROKE
        try {
            strokeWidth =
                context.resources.getDimensionPixelOffset(R.dimen.barcode_reticle_stroke_width)
                    .toFloat()
        } catch (ex: Throwable) {
            strokeWidth = context.resources.getDimension(R.dimen.barcode_reticle_stroke_width)
        }
        isAntiAlias = true

    }

    private val boxPaint: Paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.barcode_reticle_stroke)
        style = Paint.Style.STROKE
        strokeWidth =
            context.resources.getDimensionPixelOffset(R.dimen.barcode_reticle_stroke_width)
                .toFloat()
    }

    private val mLaserPaint: Paint? = Paint().apply {
        if (mDefaultLaserColor != null) {
            color = mDefaultLaserColor!!
        }
        style = Paint.Style.FILL
        strokeWidth =
            context.resources.getDimensionPixelOffset(R.dimen.laser_stroke_width).toFloat()
    }

    private val scrimPaint: Paint? = Paint().apply {
        color = ContextCompat.getColor(context, R.color.barcode_reticle_background)
    }

    private val eraserPaint: Paint = Paint().apply {
        strokeWidth = boxPaint.strokeWidth
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    init {
        this.overlayRect = Util.getBarcodeReticleBoxInt(graphicOverlay)
        this.graphicOverlay = graphicOverlay
    }


    fun drawViewFinderBorder(canvas: Canvas) {
        val framingRect = overlayRect
        // Top-left corner
        val path = Path()
        framingRect?.let {
            path.moveTo(framingRect.left.toFloat(), framingRect.top + mBorderLineLength.toFloat())
            path.lineTo(framingRect.left.toFloat(), framingRect.top.toFloat())
            path.lineTo(framingRect.left + mBorderLineLength.toFloat(), framingRect.top.toFloat())
            canvas.drawPath(path, mBorderPaint)
            // Top-right corner
            path.moveTo(framingRect.right.toFloat(), framingRect.top + mBorderLineLength.toFloat())
            path.lineTo(framingRect.right.toFloat(), framingRect.top.toFloat())
            path.lineTo(framingRect.right - mBorderLineLength.toFloat(), framingRect.top.toFloat())
            canvas.drawPath(path, mBorderPaint)
            // Bottom-right corner
            path.moveTo(
                framingRect.right.toFloat(),
                framingRect.bottom - mBorderLineLength.toFloat()
            )
            path.lineTo(framingRect.right.toFloat(), framingRect.bottom.toFloat())
            path.lineTo(
                framingRect.right - mBorderLineLength.toFloat(),
                framingRect.bottom.toFloat()
            )
            canvas.drawPath(path, mBorderPaint)
            // Bottom-left corner
            path.moveTo(
                framingRect.left.toFloat(),
                framingRect.bottom - mBorderLineLength.toFloat()
            )
            path.lineTo(framingRect.left.toFloat(), framingRect.bottom.toFloat())
            path.lineTo(
                framingRect.left + mBorderLineLength.toFloat(),
                framingRect.bottom.toFloat()
            )
            canvas.drawPath(path, mBorderPaint)
        }

    }

    override fun draw(canvas: Canvas) {
        graphicOverlay?.invalidate()
        drawLaser(canvas)
        drawViewFinderBorder(canvas)

    }

    fun drawLaser(canvas: Canvas) {
        val framingRect = overlayRect
        // Draws the dark background scrim and leaves the box area clear.
        scrimPaint?.let {
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), it)
        }
        eraserPaint.style = Paint.Style.FILL
        framingRect?.let {
            canvas.drawRect(framingRect, eraserPaint)
            eraserPaint.style = Paint.Style.STROKE
        }

        // Draw a red "laser scanner" line through the middle to show decoding is active
        mLaserPaint?.apply {
            alpha = SCANNER_ALPHA[scannerAlpha]
        }
        scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.size
        val middle = framingRect!!.height() / 2 + framingRect.top
        canvas.drawRect(
            framingRect.left + 2.toFloat(),
            middle - 1.toFloat(),
            framingRect.right - 1.toFloat(),
            middle + 2.toFloat(),
            mLaserPaint!!
        )
        graphicOverlay?.postInvalidateDelayed(
            ANIMATION_DELAY,
            framingRect.left - POINT_SIZE,
            framingRect.top - POINT_SIZE,
            framingRect.right + POINT_SIZE,
            framingRect.bottom + POINT_SIZE
        )
    }

    companion object {
        private val SCANNER_ALPHA = intArrayOf(0, 64, 128, 192, 255, 192, 128, 64)
        private const val ANIMATION_DELAY = 80L
        private const val POINT_SIZE = 10
        var TAG = "OverlayView"
    }


}

