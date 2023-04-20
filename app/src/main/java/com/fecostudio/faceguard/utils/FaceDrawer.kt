package com.fecostudio.faceguard.utils

import android.content.Context
import android.content.res.AssetManager
import android.graphics.*
import android.util.Log
import androidx.camera.core.CameraSelector
import com.google.android.renderscript.Toolkit
import com.google.mlkit.vision.face.Face
import java.io.InputStream


class FaceDrawer(context: Context) {
    private val assetManager: AssetManager = context.assets

    enum class DrawStyles(val style: Int, var bitmap: Bitmap?) {
        BlUR(1, null),
        BlACK(2, null),
        DOGE(3, null),
        LaughingMan(4, null),
        Customize(5, null),
    }

    //    用于保存每个人脸对应的特效
    private val faceStyle =
        context.getSharedPreferences("faceStyle", Context.MODE_PRIVATE)
    private val idHashMap: HashMap<Int?, Int> = HashMap() //trackingID对应的真实人脸id


    private val ratio = 10

    private val faceRecognizer = FaceRecognizer(context)


    init {
        var inputStream: InputStream = assetManager.open("picture/doge.png")
        DrawStyles.DOGE.bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream = assetManager.open("picture/laughing_man.png")
        DrawStyles.LaughingMan.bitmap = BitmapFactory.decodeStream(inputStream)
    }

    fun drawFace(
        faces: List<Face>,
        bitmap: Bitmap,
        canvas: Canvas,
        lensFacing: CameraSelector,
        degrees: Int
    ) {
        val matrix = getRotateMatrix(degrees, bitmap)//用于绘制原图的matrix
        val scaleMatrix = Matrix()//用于绘制马赛克的matrix
        val paint = Paint()
        scaleMatrix.postRotate(degrees.toFloat())
        if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
            matrix.postScale(-1f, 1f)
            matrix.postTranslate(bitmap.height.toFloat(), 0f)
            scaleMatrix.postScale(-1f, 1f)
        }
        canvas.drawBitmap(bitmap, matrix, paint)

        var scaledBitmap =
            Bitmap.createScaledBitmap(bitmap, bitmap.width / ratio, bitmap.height / ratio, false)
        scaledBitmap = Bitmap.createBitmap(
            scaledBitmap,
            0,
            0,
            scaledBitmap.width,
            scaledBitmap.height,
            scaleMatrix,
            false
        )
        scaledBitmap = Toolkit.blur(scaledBitmap)

        val unknownFaces: ArrayList<Face> = arrayListOf()
        for (face in faces) {
            val faceRect = getFaceRect(face, lensFacing)
            val scaleFaceRect = Rect(
                faceRect.left / ratio,
                faceRect.top / ratio,
                faceRect.right / ratio,
                faceRect.bottom / ratio
            )
            if (idHashMap.containsKey(face.trackingId) && idHashMap[face.trackingId] != -1) {
                //已注册的tracking id
                when (faceStyle.getInt(idHashMap[face.trackingId].toString(), -1)) {
                    DrawStyles.BlUR.style -> {
                        canvas.drawBitmap(scaledBitmap, scaleFaceRect, faceRect, paint)
                    }

                    DrawStyles.BlACK.style -> {
                        paint.setARGB(255, 0, 0, 0)
                        canvas.drawRect(faceRect, paint)
                    }

                    DrawStyles.DOGE.style -> {
                        canvas.drawBitmap(DrawStyles.DOGE.bitmap!!, null, faceRect, paint)
                    }

                    DrawStyles.LaughingMan.style -> {
                        canvas.drawBitmap(
                            DrawStyles.LaughingMan.bitmap!!,
                            null,
                            faceRect,
                            paint
                        )
                    }

                    DrawStyles.Customize.style -> {
                        if (DrawStyles.Customize.bitmap != null) {
                            canvas.drawBitmap(
                                DrawStyles.Customize.bitmap!!,
                                null,
                                faceRect,
                                paint
                            )
                        }
                    }
                }
            } else {
                //未注册的id
                unknownFaces.add(face)
                canvas.drawBitmap(scaledBitmap, scaleFaceRect, faceRect, paint)
            }
        }
        //选取两个未确定的人脸
        unknownFaces.shuffled().take(2).forEach { face ->
            val faceRect = getFaceRect(face, lensFacing)
            val rotateFaceRect = getRotateRect(degrees, faceRect, lensFacing)
            matrix.setRotate(degrees.toFloat())
            if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
                matrix.postScale(-1f, 1f)
            }
            if (Rect(
                    0,
                    0,
                    bitmap.width,
                    bitmap.height
                ).contains(rotateFaceRect)
            ) {
                val faceBitmap = Bitmap.createBitmap(
                    bitmap,
                    rotateFaceRect.left,
                    rotateFaceRect.top,
                    rotateFaceRect.width(),
                    rotateFaceRect.height(),
                    matrix,
                    false
                )
                val realFaceID = faceRecognizer.getNearestFace(faceBitmap)
                idHashMap[face.trackingId] = realFaceID
            }
        }
        canvas.save()
    }

    /** 注册人脸，并设定绘制风格 */
    fun setFaceStyle(face: Face, style: Int, faceBitmap: Bitmap) {
        val realFaceID = faceRecognizer.getNearestFace(faceBitmap)
        Log.d("FaceDrawer", "realFaceID: $realFaceID")
        if (realFaceID != -1) {
            idHashMap[face.trackingId] = realFaceID //有匹配的人脸
            with(faceStyle.edit()) {
                putInt(realFaceID.toString(), style)
                apply()
            }
        } else {
            idHashMap[face.trackingId] = faceRecognizer.registerFace(faceBitmap)//无匹配的人脸
            with(faceStyle.edit()) {
                putInt(idHashMap[face.trackingId].toString(), style)
                apply()
            }
        }
    }

    companion object {
        private fun getRotateMatrix(degrees: Int, bitmap: Bitmap): Matrix {
            val matrix = Matrix()
            when (degrees) {
                90 -> {
                    matrix.postRotate(90f)
                    matrix.postTranslate(bitmap.height.toFloat(), 0f)
                }

                180 -> {
                    matrix.postRotate(180f)
                    matrix.postTranslate(0f, bitmap.height.toFloat())
                }

                270 -> {
                    matrix.postRotate(270f)
                    matrix.postTranslate(0f, bitmap.width.toFloat())
                }
            }
            return matrix
        }

        fun getFaceRect(face: Face, lensFacing: CameraSelector): Rect {
            return if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
                Rect(
                    720 - face.boundingBox.right,
                    face.boundingBox.top,
                    720 - face.boundingBox.left,
                    face.boundingBox.bottom
                )
            } else {
                Rect(face.boundingBox)
            }
        }

        /** 获取从bitmap原图中取人脸子集的Rect */
        fun getRotateRect(degrees: Int, rect: Rect, lensFacing: CameraSelector): Rect {
            when (degrees) {
                90 -> {
                    return if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA)
                        Rect(rect.top, rect.left, rect.bottom, rect.right)
                    else
                        Rect(rect.top, 720 - rect.right, rect.bottom, 720 - rect.left)
                }

                180 -> {
                    return rect //Todo:实现180度旋转
                }

                270 -> {
                    return if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA)
                        Rect(
                            1280 - rect.bottom,
                            720 - rect.right,
                            1280 - rect.top,
                            720 - rect.left
                        )
                    else
                        Rect(1280 - rect.bottom, rect.left, 1280 - rect.top, rect.right)
                }
            }
            return rect
        }
    }
}