package com.fecostudio.faceguard

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment


class AboutFragment(context: Context) : DialogFragment() {

    private val appDetailsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        }
    private val intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater
            val aboutLayout: View = inflater.inflate(R.layout.fragment_about, null)
            val textView = aboutLayout.findViewById<TextView>(R.id.about_text)
            textView.movementMethod = LinkMovementMethod.getInstance()
            val clearDataButton = aboutLayout.findViewById<TextView>(R.id.clear_data)
            clearDataButton.setOnClickListener {
                appDetailsLauncher.launch(intent)
            }
            builder.setTitle(R.string.about)
            builder.setMessage(R.string.about_content)
            builder.setView(aboutLayout)
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}