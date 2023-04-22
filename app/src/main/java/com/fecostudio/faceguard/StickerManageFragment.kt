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
import com.google.android.material.snackbar.Snackbar
import java.io.InputStream


class StickerManageFragment(private val context: Context) : DialogFragment() {

    val bitmaps = arrayListOf<Bitmap>()
    private fun loadBitmap() {
        var inputStream: InputStream = context.assets.open("picture/add_picture.png")
        bitmaps.add(BitmapFactory.decodeStream(inputStream))
        inputStream = context.assets.open("picture/doge.png")
        bitmaps.add(BitmapFactory.decodeStream(inputStream))
        inputStream = context.assets.open("picture/laughing_man.png")
        bitmaps.add(BitmapFactory.decodeStream(inputStream))
        bitmaps += (BitmapUtil.loadAllBitmap("stickers", context))
    }

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
                        bitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
//                        保存到内部空间
                        BitmapUtil.saveBitmap("${System.currentTimeMillis()}.png", "stickers", bitmap, context)
                        (gridView.adapter as BaseAdapter).notifyDataSetChanged()
                        Log.d("uri", "onActivityResult: ${bitmap!!.height}")
                    }
                }
            loadBitmap()
            gridView.adapter = object : BaseAdapter() {
                override fun getCount(): Int {
                    return bitmaps.size // 返回数据集的大小
                }

                override fun getItem(position: Int): Any {
                    return bitmaps[position] // 返回指定位置的数据项
                }

                override fun getItemId(position: Int): Long {
                    return position.toLong() // 返回指定位置的数据项的 ID
                }

                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    // 返回指定位置的子项视图
                    val view = convertView ?: LayoutInflater.from(context).inflate(
                        R.layout.gridview_item,
                        parent,
                        false
                    ) // 如果 convertView 为空，则创建新的视图，否则复用旧的视图
                    val imageView = view.findViewById<ImageView>(R.id.image_view) // 获取 ImageView 组件
                    imageView.setImageBitmap(bitmaps[position]) // 设置 bitmap 图片
                    return view // 返回视图
                }

            }
            gridView.setOnItemClickListener { _, _, position, _ ->
                if (position == 0) {//添加贴图
                    val intent =
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    chooseImageResultLauncher.launch(intent)
                    (gridView.adapter as BaseAdapter).notifyDataSetChanged()
                } else {
                    Snackbar.make(stickerManageLayout, "长按删除贴图", 3000).show()
                }
            }
            gridView.setOnItemLongClickListener { _, _, position, _ ->
                if (position > 2) {
                    bitmaps.remove(bitmaps[position])
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