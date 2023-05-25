package com.fecostudio.faceguard

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.fecostudio.faceguard.utils.BitmapUtil
import com.fecostudio.faceguard.utils.FaceDrawer
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.face.Face


class StickerManageFragment(
    private val context: Context,
    private val faceDrawer: FaceDrawer,
    private val chooseSticker: Boolean = false,
    private val faceBitmap: Bitmap? = null,
    private val face: Face? = null,
) :
    DialogFragment() {

    private val stickerMap = faceDrawer.stickerMap
    private val faceSticker =
        context.getSharedPreferences("faceSticker", Context.MODE_PRIVATE)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater
            val stickerManageLayout: View = inflater.inflate(R.layout.fragment_sticker_manage, null)
            val gridView = stickerManageLayout.findViewById<GridView>(R.id.sticker_grid)
            val chooseImageResultLauncher =
                registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == AppCompatActivity.RESULT_OK) {
                        // There are no request codes
                        val data: Intent? = result.data
                        val uri: Uri? = data?.data
                        var bitmap =
                            BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri!!))
                        bitmap = Bitmap.createScaledBitmap(
                            bitmap,
                            512,
                            bitmap.height * 512 / bitmap.width,
                            true
                        )
//                        保存到内部空间
                        val filename = System.currentTimeMillis().toString()
                        stickerMap[filename] = bitmap
                        BitmapUtil.saveBitmap(
                            "$filename.png",
                            "stickers",
                            bitmap,
                            context
                        )
                        (gridView.adapter as BaseAdapter).notifyDataSetChanged()
                        Log.d("uri", "onActivityResult: ${bitmap!!.height}")
                    }
                }
            gridView.adapter = object : BaseAdapter() {
                override fun getCount(): Int {
                    return stickerMap.size // 返回数据集的大小
                }

                override fun getItem(position: Int): Any {
                    return stickerMap.values.toList()[position] // 返回指定位置的数据项
                }

                override fun getItemId(position: Int): Long {
                    return stickerMap.keys.toList()[position].toLong() // 返回指定位置的数据项的 ID
                }

                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    // 返回指定位置的子项视图
                    val view = convertView ?: LayoutInflater.from(context).inflate(
                        R.layout.gridview_item,
                        parent,
                        false
                    ) // 如果 convertView 为空，则创建新的视图，否则复用旧的视图
                    val imageView = view.findViewById<ImageView>(R.id.image_view) // 获取 ImageView 组件
                    val scaledBitmap = Bitmap.createScaledBitmap(
                        stickerMap.values.toList()[position],
                        256,
                        256,
                        true
                    )
                    imageView.setImageBitmap(scaledBitmap) // 设置 bitmap 图片
                    return view // 返回视图
                }

            }
            gridView.setOnItemClickListener { _, _, position, id ->
                Log.d("StickerManageFragment", "isChooseSticker: $chooseSticker")
                if (position == 0) {//添加贴图
                    val intent =
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    chooseImageResultLauncher.launch(intent)
                    (gridView.adapter as BaseAdapter).notifyDataSetChanged()
                } else {
                    if (chooseSticker && faceBitmap != null && face != null) {//从贴图选择进入
                        faceDrawer.setFaceStyle(
                            face,
                            FaceDrawer.DrawStyles.Sticker.style,
                            faceBitmap,
                            id
                        )
                        dismiss()
                    } else {
                        Snackbar.make(stickerManageLayout, "长按删除贴图", 3000).show()
                    }
                }
            }
            gridView.setOnItemLongClickListener { _, _, position, id ->
                if (position > 2) {
                    faceSticker.edit().remove(id.toString()).apply()
                    stickerMap.remove(id.toString())
                    BitmapUtil.removeBitmap("$id.png", "stickers", context)
                    (gridView.adapter as BaseAdapter).notifyDataSetChanged()
                } else if (position > 0) {
                    Snackbar.make(stickerManageLayout, "内置贴图无法删除", 3000).show()
                }
                true
            }
            builder.setTitle(R.string.sticker_manage_menu)
            builder.setView(stickerManageLayout)
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}