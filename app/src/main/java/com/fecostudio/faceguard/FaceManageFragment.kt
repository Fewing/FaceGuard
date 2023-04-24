package com.fecostudio.faceguard

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.fecostudio.faceguard.utils.FaceDrawer
import com.google.android.material.snackbar.Snackbar


class FaceManageFragment(private val context: Context,private val faceDrawer: FaceDrawer) :
    DialogFragment() {

    private val faceMap = faceDrawer.faceRecognizer.faceBitmapMap

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater
            val stickerManageLayout: View = inflater.inflate(R.layout.fragment_face_manage, null)
            val gridView = stickerManageLayout.findViewById<GridView>(R.id.sticker_grid)
            gridView.adapter = object : BaseAdapter() {
                override fun getCount(): Int {
                    return faceMap.size // 返回数据集的大小
                }

                override fun getItem(position: Int): Any {
                    return faceMap.values.toList()[position] // 返回指定位置的数据项
                }

                override fun getItemId(position: Int): Long {
                    return faceMap.keys.toList()[position].toLong() // 返回指定位置的数据项的 ID
                }

                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    // 返回指定位置的子项视图
                    val view = convertView ?: LayoutInflater.from(context).inflate(
                        R.layout.gridview_item,
                        parent,
                        false
                    ) // 如果 convertView 为空，则创建新的视图，否则复用旧的视图
                    val imageView = view.findViewById<ImageView>(R.id.image_view) // 获取 ImageView 组件
                    imageView.setImageBitmap(faceMap.values.toList()[position]) // 设置 bitmap 图片
                    return view // 返回视图
                }

            }
            gridView.setOnItemClickListener { _, _, _, _ ->
                Snackbar.make(stickerManageLayout, "长按删除人脸", 3000).show()
            }
//            删除人脸数据
            gridView.setOnItemLongClickListener { _, _, _, id ->
                faceMap.remove(id.toString())
                faceDrawer.removeFaceAndStyle(id)
                (gridView.adapter as BaseAdapter).notifyDataSetChanged()
                true
            }
            builder.setTitle(R.string.face_manage_menu)
            builder.setView(stickerManageLayout)
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}