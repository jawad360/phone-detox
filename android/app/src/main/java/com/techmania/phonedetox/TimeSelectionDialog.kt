package com.techmania.phonedetox

import android.app.Dialog
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicReference

class TimeSelectionDialog private constructor(
    private val context: Context,
    private val packageName: String,
    private val appName: String,
    private val isExtension: Boolean = false,
    private val onTimeSelected: (Int) -> Unit
) {
    private var dialog: Dialog? = null

    companion object {
        private val activeDialog = AtomicReference<TimeSelectionDialog?>()

        fun show(
            context: Context,
            packageName: String,
            appName: String,
            isExtension: Boolean = false,
            onTimeSelected: (Int) -> Unit
        ) {
            // Dismiss any existing dialog
            activeDialog.get()?.dismiss()

            // Use application context to avoid memory leaks
            val appContext = context.applicationContext
            val dialog = TimeSelectionDialog(appContext, packageName, appName, isExtension, onTimeSelected)
            activeDialog.set(dialog)
            dialog.show()
        }
    }

    private val timeOptions = listOf(5, 10, 15, 30, 60, 120, 180)

    fun show() {
        // Create custom view programmatically
        val view = createDialogView(context)

        // Create dialog with overlay type
        dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen).apply {
            window?.apply {
                setType(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                    }
                )
                // Allow touch events and focus
                setFlags(0, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                setFormat(PixelFormat.TRANSLUCENT)
                setGravity(Gravity.CENTER)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            setContentView(view)
            setCancelable(false)
            show()
        }
    }

    private fun createDialogView(context: Context): View {
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xE6000000.toInt()) // Semi-transparent black
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        // Content container
        val contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(32, 32, 32, 32)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(32, 32, 32, 32)
            }
        }
        
        // Title
        val title = TextView(context).apply {
            text = if (isExtension) {
                "Time's up! How much more time?"
            } else {
                "How long do you want to use $appName?"
            }
            textSize = 20f
            setTextColor(0xFF000000.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        contentContainer.addView(title)
        
        // Time options container
        timeOptions.forEach { minutes ->
            val button = createTimeButton(context, minutes)
            button.setOnClickListener {
                dismiss()
                onTimeSelected(minutes)
            }
            contentContainer.addView(button)
        }
        
        // Cancel button
        val cancelButton = Button(context).apply {
            text = "Cancel"
            setOnClickListener {
                dismiss()
                onTimeSelected(0)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(32, 24, 32, 0)
            }
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setBackgroundColor(0xFF666666.toInt())
            textSize = 16f
            isAllCaps = false
        }
        contentContainer.addView(cancelButton)
        
        mainLayout.addView(contentContainer)
        return mainLayout
    }

    private fun createTimeButton(context: Context, minutes: Int): Button {
        val button = Button(context)
        val text = when {
            minutes >= 60 -> "${minutes / 60}h"
            else -> "${minutes}m"
        }
        button.text = text
        button.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(32, 8, 32, 8)
        }
        button.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        button.setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
        button.textSize = 18f
        button.isAllCaps = false
        return button
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        activeDialog.set(null)
    }
}

