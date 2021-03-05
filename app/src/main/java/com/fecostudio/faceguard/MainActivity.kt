package com.fecostudio.faceguard

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.fecostudio.faceguard.utils.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_CODE_PERMISSIONS = 10
        private const val RECORDER_VIDEO_BITRATE: Int = 10_000_000
        private const val MIN_REQUIRED_RECORDING_TIME_MILLIS: Long = 1000L

        /** Creates a [File] named with the current date and time */
        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.CHINA)
            val appContext = context.applicationContext
            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() } }
            return File(mediaDir, "VID_${sdf.format(Date())}.$extension")
        }
    }
    private lateinit var surfaceView: SurfaceView
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
    private val executor = Executors.newSingleThreadExecutor()
    /** File where the recording will be saved */
    private val outputFile: File by lazy { createFile(this, "mp4") }
    private lateinit var converter: YuvToRgbConverter
    private var bitmap: Bitmap? = null
    private val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE).enableTracking()
                    .build())
    private lateinit var recorderSurface: Surface
    private val recorder: MediaRecorder by lazy { createRecorder() }
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   //保持屏幕常亮
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)//设置全屏
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
                surfaceView.layoutParams.height = surfaceView.width * 16 / 9
                holder.setFixedSize(1080, 1080 * 16 / 9)

                // To ensure that size is set, initialize camera in the view's thread
                //surfaceView.post { initializeCamera() }
            }
        })
        // Camera permission needed for CameraX.
        requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), REQUEST_CODE_PERMISSIONS)
        // Init CameraX.
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            cameraProvider = cameraProviderFuture.get()
            startCameraIfReady()
        }, ContextCompat.getMainExecutor(this))
    }

    /** Creates a [MediaRecorder] instance using the provided [Surface] as input */
    private fun createRecorder() = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setOutputFile(outputFile.absolutePath)
        setVideoEncodingBitRate(RECORDER_VIDEO_BITRATE)
        setVideoFrameRate(25)
        setVideoSize(1080, 1920)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun startCameraIfReady() {
        if (!isPermissionsGranted() || cameraProvider == null) {
            return
        }
        val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setTargetResolution(
                Size(1080, 1920)
        ).build()
        imageAnalysis.setAnalyzer(executor, {
            val start = System.currentTimeMillis()
            val mediaImage = it.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, it.imageInfo.rotationDegrees)
                detector.process(image)
                        .addOnSuccessListener { faces ->
                            val bitmap = allocateBitmapIfNecessary(it.width, it.height)
                            converter.yuvToRgb(it.image!!, bitmap)
                            it.close()
                            val paint = Paint()
                            val matrix = Matrix()
                            matrix.postRotate(90f)
                            matrix.postTranslate(bitmap.height.toFloat(), 0f)
                            val previewCanvas = surfaceView.holder.lockHardwareCanvas()//预览的画布
                            if (isRecording) {
                                val recordCanvas = recorderSurface.lockHardwareCanvas()//录制的画布
                                recordCanvas.drawBitmap(bitmap, matrix, paint)
                                for (face in faces) {
                                    recordCanvas.drawRect(face.boundingBox, paint)
                                }
                                recordCanvas.save()
                                recorderSurface.unlockCanvasAndPost(recordCanvas)
                            }
                            previewCanvas.drawBitmap(bitmap, matrix, paint)
                            for (face in faces) {
                                previewCanvas.drawRect(face.boundingBox, paint)
                            }
                            previewCanvas.save()
                            surfaceView.holder.unlockCanvasAndPost(previewCanvas)
                            val delayTime = System.currentTimeMillis() - start
                            //Log.d("time", "startCameraIfReady: "+delayTime+"ms")
                        }
                        .addOnFailureListener { e ->
                            Log.e("MLKit", "startCameraIfReady: "+e.localizedMessage, )
                        }
            }
        })
        cameraProvider!!.bindToLifecycle(this, lensFacing, imageAnalysis)
    }

    fun startRecord(view: View) {
        if (isRecording) {
            recorder.stop()
            MediaScannerConnection.scanFile(
                    view.context, arrayOf(outputFile.absolutePath), null, null)
            isRecording = false
        } else {
            recorder.prepare()
            recorder.start()
            recorderSurface = recorder.surface
            Log.d("record", "Recording started")
            isRecording = true
        }
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
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED){
            return  true
        }
        return false
    }

    fun switchCamera(view: View) {

    }
}
