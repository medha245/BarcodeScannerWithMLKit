package com.scan.sqbarcodescanner.camera

import java.nio.ByteBuffer

/** Metadata info of a camera frame.  */
class FrameMetadata(val width: Int, val height: Int, val rotation: Int)

interface FrameProcessor {

    /** Processes the input frame with the underlying detector.  */
    fun process(data: ByteBuffer, frameMetadata: FrameMetadata, graphicOverlay: GraphicOverlay)

    /** Stops the underlying detector and release resources.  */
    fun stop()
}