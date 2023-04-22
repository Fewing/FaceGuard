package com.fecostudio.faceguard.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream


class BitmapUtil {
    companion object {
        fun saveBitmap(name: String, dirName: String, bm: Bitmap, mContext: Context) {
            val targetPath = mContext.getDir(dirName, Context.MODE_PRIVATE)
            val saveFile = File(targetPath, name)
            try {
                val saveImgOut = FileOutputStream(saveFile)
                // compress - 压缩的意思
                bm.compress(Bitmap.CompressFormat.PNG, 100, saveImgOut)
                //存储完成后需要清除相关的进程
                saveImgOut.flush()
                saveImgOut.close()
                Log.d("Save Bitmap", "The picture is save to $targetPath")
            } catch (ex: IOException) {
                ex.printStackTrace()
            }

        }

        fun loadAllBitmap(dirName: String, mContext: Context): ArrayList<Bitmap> {
            val targetPath = mContext.getDir(dirName, Context.MODE_PRIVATE)
            val bitmaps = arrayListOf<Bitmap>()
            for (file in targetPath.listFiles()!!) {
                val inputStream: InputStream = FileInputStream(file)
                bitmaps.add(BitmapFactory.decodeStream(inputStream))
            }
            Log.d("BitmapUtil", "bitmaps size:${bitmaps.size} ")
            return bitmaps
        }
    }
}