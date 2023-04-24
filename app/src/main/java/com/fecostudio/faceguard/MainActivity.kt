package com.fecostudio.faceguard

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.animation.doOnCancel
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.fecostudio.faceguard.utils.FaceDrawer
import com.fecostudio.faceguard.utils.YuvToRgbConverter
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity(), View.OnTouchListener,
    ChooseStyleFragment.ChooseStyleListener, PrivacyFragment.PrivacyDialogListener {

    companion object {
        const val REQUEST_CODE_PERMISSIONS = 10
        private const val RECORDER_VIDEO_BITRATE: Int = 10_000_000
        private const val videoWidth = 720
        private const val videoHeight = 1280

        /** Creates a [File] named with the current date and time */
        private fun createFile(context: Context): File {
            val sdf = SimpleDateFormat("yyyyMMdd_HH_mm_ss", Locale.CHINA)
            val appContext = context.applicationContext
            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() }
            }
            return File(mediaDir, "VID_${sdf.format(Date())}.mp4")
        }
    }

    private lateinit var surfaceView: SurfaceView
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
    private var rotationDegrees = 90
    private val executor = Executors.newSingleThreadExecutor()

    /** File where the recording will be saved */
    private val outputFile: File by lazy { createFile(this) }
    private lateinit var converter: YuvToRgbConverter
    private var bitmap: Bitmap? = null
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE).enableTracking()
            .build()
    )
    private var faceList: List<Face> = emptyList()
    private lateinit var faceDrawer: FaceDrawer
    private lateinit var recorderSurface: Surface
    private lateinit var recorder: MediaRecorder
    private var isRecording = false
    private var lastRecordClickTime = 0L
    private val captureButton: ImageButton by lazy { findViewById(R.id.capture_button) }
    private val animateRecord by lazy {
        ObjectAnimator.ofFloat(captureButton, View.ALPHA, 1f, 0.5f).apply {
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            doOnCancel { captureButton.alpha = 1f }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   //保持屏幕常亮
        setContentView(R.layout.activity_main)
        // YuvToRgb converter.
        converter = YuvToRgbConverter()
        faceDrawer = FaceDrawer(this)
        // Init views.
        surfaceView = findViewById(R.id.preview_surface_view)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {

                // Selects appropriate preview size and configures view finder
                Log.d("size", "Surface View size: ${surfaceView.width} x ${surfaceView.height}")
                surfaceView.layoutParams.height = surfaceView.width * 16 / 9
                holder.setFixedSize(videoWidth, videoWidth * 16 / 9)

                // To ensure that size is set, initialize camera in the view's thread
                //surfaceView.post { initializeCamera() }
            }
        })
        if (!isPermissionsGranted()) {
            PrivacyFragment().show(supportFragmentManager, "PrivacyFragment")
        }
        surfaceView.setOnTouchListener(this)
        // Camera permission needed for CameraX.
        Snackbar.make(surfaceView, R.string.on_create_tip, 3000).show()
        // Init CameraX.
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
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
        setVideoFrameRate(30)
        setVideoSize(videoWidth, videoHeight)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun startCameraIfReady() {
        if (!isPermissionsGranted() || cameraProvider == null) {
            return
        }
        val imageAnalysis =
            ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(
                    Size(videoWidth, videoHeight)
                ).build()
        imageAnalysis.setAnalyzer(executor) {
            val start = System.currentTimeMillis()
            val mediaImage = it.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, it.imageInfo.rotationDegrees)
                rotationDegrees = it.imageInfo.rotationDegrees
                val task = detector.process(image)
                    .addOnSuccessListener { faces ->
                        faceList = faces
                    }
                    .addOnFailureListener { e ->
                        Log.e("MLKit", "MLKit: " + e.localizedMessage)
                    }
                bitmap = converter.yuvToRgb(mediaImage)//获取bitmap
                if (isRecording) {
                    val recordCanvas = recorderSurface.lockHardwareCanvas()//录制的画布
                    faceDrawer.drawFace(
                        faceList,
                        bitmap!!,
                        recordCanvas,
                        lensFacing,
                        it.imageInfo.rotationDegrees
                    )//绘制人脸
                    recorderSurface.unlockCanvasAndPost(recordCanvas)
                }
                val previewCanvas =
                    surfaceView.holder.lockHardwareCanvas()
                //预览的画布
                if (previewCanvas != null) {
                    faceDrawer.drawFace(
                        faceList,
                        bitmap!!,
                        previewCanvas,
                        lensFacing,
                        it.imageInfo.rotationDegrees
                    )//绘制人脸
                    surfaceView.holder.unlockCanvasAndPost(previewCanvas)
                }
                while (!task.isComplete) {
                }
                it.close()
                Log.v("MainActivity", "draw latency: ${System.currentTimeMillis() - start} ms")
            }
        }
        cameraProvider!!.bindToLifecycle(this, lensFacing, imageAnalysis)
    }

    fun startRecord(view: View) {
        if (System.currentTimeMillis() > lastRecordClickTime + 1000) {
            lastRecordClickTime = System.currentTimeMillis()
            if (isRecording) {
                recorder.stop()
                recorder.release()
                animateRecord.cancel()
                MediaScannerConnection.scanFile(
                    view.context, arrayOf(outputFile.absolutePath), null, null
                )
                Snackbar.make(surfaceView, "视频已保存至" + outputFile.parent, 3000).show()
                isRecording = false
            } else {
                recorder = createRecorder()
                recorder.prepare()
                recorder.start()
                animateRecord.start()
                recorderSurface = recorder.surface
                Log.d("record", "Recording started")
                isRecording = true
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            startCameraIfReady()
        }
    }

    private fun isPermissionsGranted(): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }

    fun switchCamera(view: View) {
        lensFacing = if (lensFacing == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        cameraProvider!!.unbindAll()
        startCameraIfReady()
    }

    fun showPopup(v: View) {
        val popup = PopupMenu(this, v)
        popup.inflate(R.menu.popup_menu)
        popup.show()
    }

    fun toAbout(item: MenuItem) {
        AboutFragment().show(supportFragmentManager, "AboutFragment")
    }

    fun toFaceManage(item: MenuItem) {
        FaceManageFragment(this, faceDrawer).show(supportFragmentManager, "FaceManageFragment")
    }

    fun toStickerManage(item: MenuItem) {
        StickerManageFragment(this, faceDrawer).show(
            supportFragmentManager,
            "StickerManageFragment"
        )
    }

    //用户同意隐私
    override fun onDialogPositiveClick(dialog: DialogFragment) {
        requestPermissions(
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            REQUEST_CODE_PERMISSIONS
        )
        cameraProvider!!.unbindAll()
        startCameraIfReady()
    }

    //监听点击操作
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (event != null) {
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.x * videoWidth / surfaceView.width
                val y = event.y * videoHeight / surfaceView.height
                Log.d("touch", "onTouch: $x ,$y")
                for (face in faceList) {
                    val faceRect = if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
                        Rect(
                            videoWidth - face.boundingBox.right,
                            face.boundingBox.top,
                            videoWidth - face.boundingBox.left,
                            face.boundingBox.bottom
                        )
                    } else {
                        Rect(face.boundingBox)
                    }
                    if (faceRect.contains(x.toInt(), y.toInt())) {
                        val rotateFaceRect =
                            FaceDrawer.getRotateRect(
                                rotationDegrees,
                                FaceDrawer.getFaceRect(face, lensFacing),
                                lensFacing
                            )
                        if (Rect(
                                0,
                                0,
                                bitmap!!.width,
                                bitmap!!.height
                            ).contains(rotateFaceRect)//判断是否在画面边缘
                        ) {
                            val matrix = Matrix()
                            matrix.setRotate(rotationDegrees.toFloat())
                            if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
                                matrix.postScale(-1f, 1f)
                            }
                            val faceBitmap = Bitmap.createBitmap(
                                bitmap!!,
                                rotateFaceRect.left,
                                rotateFaceRect.top,
                                rotateFaceRect.width(),
                                rotateFaceRect.height(),
                                matrix,
                                false
                            )
                            val dialog = ChooseStyleFragment(faceBitmap, face)
                            dialog.show(supportFragmentManager, "ChooseStyleFragment")
                        } else {
                            Snackbar.make(surfaceView, R.string.failed_to_register, 3000).show()
                        }
                    }
                }
            }
        }
        return true
    }

    //接受样式选择结果
    override fun onStyleDialogClick(bitmap: Bitmap, face: Face, which: Int) {
        if (which == FaceDrawer.DrawStyles.Sticker.style) {//进入贴图选择
            StickerManageFragment(this, faceDrawer, true, face = face, faceBitmap = bitmap).show(
                supportFragmentManager,
                "StickerManageFragment"
            )
        } else {
            faceDrawer.setFaceStyle(face, which, bitmap, 0)
        }
    }
}
