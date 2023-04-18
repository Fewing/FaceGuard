package com.fecostudio.faceguard

import android.app.Dialog
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment


class AboutFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater
            val aboutLayout: View = inflater.inflate(R.layout.fragment_about, null)
            val textView = aboutLayout.findViewById<TextView>(R.id.about_text)
            textView.movementMethod = LinkMovementMethod.getInstance()
            builder.setTitle(R.string.about)
            builder.setMessage(R.string.about_content)
            builder.setView(aboutLayout)
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}