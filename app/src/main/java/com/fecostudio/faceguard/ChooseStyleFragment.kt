package com.fecostudio.faceguard

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.mlkit.vision.face.Face


class ChooseStyleFragment(private val bitmap: Bitmap, private val face: Face) : DialogFragment() {
    private lateinit var listener: ChooseStyleListener

    interface ChooseStyleListener {
        fun onDialogClick(bitmap: Bitmap, face: Face, which: Int)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(it)
            builder.setItems(
                R.array.styles
            ) { _, which ->
                // The 'which' argument contains the index position
                // of the selected item
                listener.onDialogClick(bitmap, face, which)
            }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            listener = context as ChooseStyleListener
        } catch (e: ClassCastException) {
            // The activity doesn't implement the interface, throw exception
            throw ClassCastException(
                (context.toString() +
                        " must implement NoticeDialogListener")
            )
        }
    }
}