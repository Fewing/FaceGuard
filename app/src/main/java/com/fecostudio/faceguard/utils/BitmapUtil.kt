package com.fecostudio.faceguard.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
            } catch (ex: IOException) {
                ex.printStackTrace()
            }

        }
        fun removeBitmap(name: String, dirName: String, mContext: Context) {
            val targetPath = mContext.getDir(dirName, Context.MODE_PRIVATE)
            val saveFile = File(targetPath, name)
            saveFile.delete()
        }

        fun loadAllBitmap(dirName: String, mContext: Context): HashMap<String,Bitmap> {
            val targetPath = mContext.getDir(dirName, Context.MODE_PRIVATE)
            val bitmaps =  HashMap<String,Bitmap>()
            for (file in targetPath.listFiles()!!) {
                val inputStream: InputStream = FileInputStream(file)
                bitmaps[file.nameWithoutExtension] = BitmapFactory.decodeStream(inputStream)
            }
            return bitmaps
        }
    }
}