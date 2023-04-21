package com.fecostudio.faceguard

import android.app.Dialog
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment


class StickerManageFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater
            val aboutLayout: View = inflater.inflate(R.layout.fragment_sticker_manage, null)
            val textView = aboutLayout.findViewById<TextView>(R.id.about_text)
            textView.movementMethod = LinkMovementMethod.getInstance()

            builder.setTitle(R.string.sticker_manage_menu)
            builder.setView(aboutLayout)
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}