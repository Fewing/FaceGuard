package com.fecostudio.faceguard

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.fecostudio.faceguard.utils.YuvToRgbConverter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_CODE_PERMISSIONS = 10
    }

    private var surfaceView: SurfaceView? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var converter: YuvToRgbConverter
    private var bitmap: Bitmap? = null
    private val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE).enableTracking()
                    .build())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // YuvToRgb converter.
        converter = YuvToRgbConverter(this)

        // Init views.
        surfaceView = findViewById(R.id.preview_surface_view)
        surfaceHolder = surfaceView!!.holder
        // The activity is locked to portrait mode. We only need to correct for sensor rotation.
        //gpuImageView.rotation = 90F
        //gpuImageView.setScaleType(GPUImage.ScaleType.CENTER_CROP)

        // Camera permission needed for CameraX.
        requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS)

        // Init CameraX.
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(Runnable {
            cameraProvider = cameraProviderFuture.get()
            startCameraIfReady()
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun startCameraIfReady() {
        if (!isPermissionsGranted() || cameraProvider == null) {
            return;
        }
        val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setTargetResolution(
                Size(1080, 1920)
        ).build()
        imageAnalysis.setAnalyzer(executor, ImageAnalysis.Analyzer {
            val start = System.currentTimeMillis()
            val mediaImage = it.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, it.imageInfo.rotationDegrees)
                val result = detector.process(image)
                        .addOnSuccessListener { faces ->
                            var bitmap = allocateBitmapIfNecessary(it.width, it.height)
                            converter.yuvToRgb(it.image!!, bitmap)
                            it.close()
                            val paint = Paint()
                            var matrix = Matrix()
                            var offsetX = bitmap.height / 2;
                            var offsetY = bitmap.width / 2;
                            matrix.postTranslate(-offsetX.toFloat(), -offsetY.toFloat());
                            matrix.postRotate(90f);
                            matrix.postTranslate(-200 + offsetX.toFloat(), -200 + offsetY.toFloat());
                            val newCanvas = surfaceHolder!!.lockHardwareCanvas()
                            newCanvas.drawBitmap(bitmap, matrix,paint)
                            for (face in faces){
                                newCanvas.drawRect(face.boundingBox,paint)
                            }
                            newCanvas.save()
                            surfaceHolder!!.unlockCanvasAndPost(newCanvas)
                            val dur = System.currentTimeMillis() - start
                            Log.i("time", "startCameraIfReady: "+dur+"ms")
                        }
                        .addOnFailureListener { e ->
                            // Task failed with an exception
                            // ...
                        }
            }
        })
        cameraProvider!!.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis)
    }

    private fun allocateBitmapIfNecessary(width: Int, height: Int): Bitmap {
        if (bitmap == null || bitmap!!.width != width || bitmap!!.height != height) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        return bitmap!!
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            startCameraIfReady()
        }
    }

    private fun isPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
}
