package com.fecostudio.faceguard.utils

import android.content.Context
import android.content.res.AssetManager
import android.graphics.*
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.camera.core.CameraSelector
import com.google.mlkit.vision.face.Face
import java.io.InputStream


class FaceDrawer(context: Context) {
    private val assetManager: AssetManager = context.assets

    enum class DrawStyles(val style: Int, var bitmap: Bitmap?) {
        BlUR(1, null),
        BlACK(2, null),
        DOGE(3, null),
        SMILE_BOY(4, null),
    }

    private val faceHashMap: HashMap<Int?, Int> = HashMap()

    private val rs = RenderScript.create(context)
    private val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

    private val ratio = 10
    private val radius = 5f

    init {
        var inputStream: InputStream = assetManager.open("picture/doge.png")
        DrawStyles.DOGE.bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream = assetManager.open("picture/smileboy.png")
        DrawStyles.SMILE_BOY.bitmap = BitmapFactory.decodeStream(inputStream)
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
        scaleMatrix.postRotate(degrees.toFloat())
        val paint = Paint()
        if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
            matrix.postScale(-1f, 1f)
            matrix.postTranslate(bitmap.height.toFloat(), 0f)
            scaleMatrix.postScale(-1f, 1f)
        }
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
        blurBitmapByRender(scaledBitmap)
        canvas.drawBitmap(bitmap, matrix, paint)
        for (face in faces) {
            val faceRect = if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
                Rect(
                    1080 - face.boundingBox.right,
                    face.boundingBox.top,
                    1080 - face.boundingBox.left,
                    face.boundingBox.bottom
                )
            } else {
                Rect(face.boundingBox)
            }
            if (faceHashMap.containsKey(face.trackingId)) {
                when (faceHashMap[face.trackingId]) {
                    DrawStyles.BlUR.style -> {
                        val scaleFaceRect = Rect(
                            faceRect.left / ratio,
                            faceRect.top / ratio,
                            faceRect.right / ratio,
                            faceRect.bottom / ratio
                        )
                        canvas.drawBitmap(scaledBitmap, scaleFaceRect, faceRect, paint)
                    }
                    DrawStyles.BlACK.style -> {
                        paint.setARGB(255, 0, 0, 0)
                        canvas.drawRect(faceRect, paint)
                    }
                    DrawStyles.DOGE.style -> {
                        canvas.drawBitmap(DrawStyles.DOGE.bitmap!!, null, faceRect, paint)
                    }
                    DrawStyles.SMILE_BOY.style -> {
                        canvas.drawBitmap(
                            DrawStyles.SMILE_BOY.bitmap!!,
                            null,
                            faceRect,
                            paint
                        )
                    }
                }
            } else {
                faceHashMap[face.trackingId] = DrawStyles.BlUR.style//添加新的人脸，默认马赛克
            }

        }
        canvas.save()
    }

    fun setFaceStyle(faceID: Int, style: Int){
        faceHashMap[faceID] = style
    }

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

    private fun blurBitmapByRender(bitmap: Bitmap) {
        val allocation = Allocation.createFromBitmap(rs, bitmap)
        blurScript.setInput(allocation)
        blurScript.setRadius(radius)
        blurScript.forEach(allocation)
        //rs.finish()
    }
}