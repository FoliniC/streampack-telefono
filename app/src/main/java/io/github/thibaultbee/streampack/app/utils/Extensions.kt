package io.github.thibaultbee.streampack.app.utils

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog

fun Context.toast(message: String) =
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

fun Context.showDialog(
    title: String,
    message: String = "",
    @StringRes
    positiveButtonText: Int = android.R.string.ok,
    @StringRes
    negativeButtonText: Int = android.R.string.cancel,
    onPositiveButtonClick: () -> Unit = {},
    onNegativeButtonClick: () -> Unit = {},
    autoConfirmSeconds: Int = 0
) {
    showDialogWithLogs(
        title = title,
        message = message,
        positiveButtonText = positiveButtonText,
        negativeButtonText = negativeButtonText,
        onPositiveButtonClick = onPositiveButtonClick,
        onNegativeButtonClick = onNegativeButtonClick,
        autoConfirmSeconds = autoConfirmSeconds,
        logText = ""
    )
}

fun Context.showDialogWithLogs(
    title: String,
    message: String = "",
    @StringRes
    positiveButtonText: Int = android.R.string.ok,
    @StringRes
    negativeButtonText: Int = android.R.string.cancel,
    onPositiveButtonClick: () -> Unit = {},
    onNegativeButtonClick: () -> Unit = {},
    autoConfirmSeconds: Int = 0,
    logText: String = ""
) {
    val dialog = AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .apply {
            if (positiveButtonText != 0) {
                setPositiveButton(positiveButtonText) { dialogInterface: DialogInterface, _: Int ->
                    dialogInterface.dismiss()
                    onPositiveButtonClick()
                }
            }
            if (negativeButtonText != 0) {
                setNegativeButton(negativeButtonText) { dialogInterface: DialogInterface, _: Int ->
                    dialogInterface.dismiss()
                    onNegativeButtonClick()
                }
            }
        }
        .show()

    // Add log text to dialog if provided
    if (logText.isNotEmpty()) {
        val scrollView = ScrollView(this)
        val logView = TextView(this)
        logView.text = logText
        logView.textSize = 10f
        logView.setPadding(10, 10, 10, 10)
        logView.typeface = Typeface.MONOSPACE
        logView.setTextColor(Color.BLACK)
        logView.setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        scrollView.addView(logView)
        dialog.listView?.addFooterView(scrollView)
    }

    // Auto-confirm after specified seconds if no interaction
    if (autoConfirmSeconds > 0) {
        Handler(this.mainLooper).postDelayed({
            if (dialog.isShowing) {
                dialog.dismiss()
                onPositiveButtonClick()
            }
        }, (autoConfirmSeconds * 1000L))
    }
}