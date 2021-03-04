package com.fecostudio.faceguard

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.fecostudio.faceguard.utils.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_CODE_PERMISSIONS = 10
    }
    private lateinit var surfaceView: AutoFitSurfaceView
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
        window.addFlags(WindowManager.LayoutParams.
        FLAG_KEEP_SCREEN_ON);   //保持屏幕常亮
        setContentView(R.layout.activity_main)
        // YuvToRgb converter.
        converter = YuvToRgbConverter(this)

        // Init views.
        surfaceView = findViewById(R.id.preview_surface_view)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {

                // Selects appropriate preview size and configures view finder
                Log.d("size", "Surface View size: ${surfaceView.width} x ${surfaceView.height}")
                holder.setFixedSize(1080, 1080*surfaceView.height/surfaceView.width)

                // To ensure that size is set, initialize camera in the view's thread
                //surfaceView.post { initializeCamera() }
            }
        })
        // Camera permission needed for CameraX.
        requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS)

        // Init CameraX.
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            cameraProvider = cameraProviderFuture.get()
            startCameraIfReady()
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun startCameraIfReady() {
        if (!isPermissionsGranted() || cameraProvider == null) {
            return
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
                            matrix.postRotate(90f)
                            matrix.postTranslate(bitmap.height.toFloat(),0f)
                            val newCanvas = surfaceView.holder.lockHardwareCanvas()
                            newCanvas.drawBitmap(bitmap, matrix,paint)
                            for (face in faces){
                                newCanvas.drawRect(face.boundingBox,paint)
                            }
                            newCanvas.save()
                            surfaceView.holder.unlockCanvasAndPost(newCanvas)
                            val delayTime = System.currentTimeMillis() - start
                            //Log.d("time", "startCameraIfReady: "+delayTime+"ms")
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
