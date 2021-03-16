package com.fecostudio.faceguard

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class PrivacyFragment : DialogFragment() {
    private lateinit var listener: PrivacyDialogListener

    interface PrivacyDialogListener {
        fun onDialogPositiveClick(dialog: DialogFragment)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            // Use the Builder class for convenient dialog construction
            val builder = AlertDialog.Builder(it)
            builder.setTitle(R.string.privacy_title)
            builder.setMessage(R.string.privacy_content).setPositiveButton(
                R.string.privacy_agree
            ) { _, _ ->
                //同意
                listener.onDialogPositiveClick(this)
            }.setNegativeButton(
                R.string.privacy_cancel
            ) { _, _ ->
                // 退出应用
                activity!!.finish()
            }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            listener = context as PrivacyDialogListener
        } catch (e: ClassCastException) {
            // The activity doesn't implement the interface, throw exception
            throw ClassCastException(
                (context.toString() +
                        " must implement NoticeDialogListener")
            )
        }
    }
}