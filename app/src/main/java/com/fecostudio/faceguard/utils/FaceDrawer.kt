package com.fecostudio.faceguard.utils

import android.content.Context
import android.graphics.*
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.camera.core.CameraSelector
import com.google.mlkit.vision.face.Face


class FaceDrawer(context: Context) {
    /** 人脸id对应的绘制方式
     *  0：不绘制
     *  1：绘制马赛克
     *  2：绘制黑色块
     *  大于2：图片*/
    private val faceHashMap: HashMap<Int?, Int> = HashMap()
    private val rs = RenderScript.create(context)
    private val ratio = 10
    private val radius = 5f

    fun drawFace(faces: List<Face>, bitmap: Bitmap, canvas: Canvas, lensFacing: CameraSelector, degrees: Int) {
        val matrix = getRotateMatrix(degrees, bitmap)//用于绘制原图的matrix
        val scaleMatrix = Matrix()//用于绘制马赛克的matrix
        scaleMatrix.postRotate(degrees.toFloat())
        val paint = Paint()
        if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
            matrix.postScale(-1f, 1f)
            matrix.postTranslate(bitmap.height.toFloat(), 0f)
            scaleMatrix.postScale(-1f, 1f)
        }
        var scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / ratio, bitmap.height / ratio, false)
        scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.width, scaledBitmap.height, scaleMatrix, false)
        blurBitmapByRender(scaledBitmap)
        canvas.drawBitmap(bitmap, matrix, paint)
        for (face in faces) {
            if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) {
                val temp = face.boundingBox.left
                face.boundingBox.left = 1080 - face.boundingBox.right
                face.boundingBox.right = 1080 - temp
            }
            if (faceHashMap.containsKey(face.trackingId)) {
                when (faceHashMap[face.trackingId]) {
                    1 -> {
                        val scaleFaceRect = Rect(face.boundingBox.left / ratio, face.boundingBox.top / ratio, face.boundingBox.right / ratio, face.boundingBox.bottom / ratio)
                        canvas.drawBitmap(scaledBitmap, scaleFaceRect, face.boundingBox, paint)
                    }
                    2 -> {
                        paint.setARGB(255, 0, 0, 0)
                        canvas.drawRect(face.boundingBox, paint)
                    }
                    3, 4, 5, 6, 7 -> {
                        //Todo:添加图片头像绘制
                    }
                }
            } else {//添加新的人脸
                faceHashMap[face.trackingId] = 1//默认马赛克
            }

        }
        canvas.save()
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
        val outBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        val allocation = Allocation.createFromBitmap(rs, bitmap)
        blurScript.setInput(allocation)
        blurScript.setRadius(radius)
        blurScript.forEach(allocation)
        allocation.copyTo(outBitmap)
    }
}