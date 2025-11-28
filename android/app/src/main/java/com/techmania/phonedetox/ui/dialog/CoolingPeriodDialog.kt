package com.techmania.phonedetox.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicReference

class CoolingPeriodDialog private constructor(
    private val context: Context,
    private val packageName: String,
    private val appName: String,
    private val coolingEndTime: Long,
    private val onOkClicked: (() -> Unit)? = null
) {
    private var dialog: Dialog? = null
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var timerTextView: TextView? = null

    companion object {
        private val activeDialog = AtomicReference<CoolingPeriodDialog?>()

        fun show(
            context: Context,
            packageName: String,
            appName: String,
            coolingEndTime: Long,
            onOkClicked: (() -> Unit)? = null
        ) {
            activeDialog.get()?.dismiss()
            // Use the context directly for overlay dialogs, not applicationContext
            // This ensures we have the proper window manager access
            val dialog = CoolingPeriodDialog(context, packageName, appName, coolingEndTime, onOkClicked)
            activeDialog.set(dialog)
            dialog.show()
        }
        
        fun dismiss() {
            activeDialog.get()?.dismiss()
        }
    }

    fun show() {
        try {
            val view = createDialogView(context)
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
                    // Allow touch events - remove FLAG_NOT_FOCUSABLE
                    // Keep default flags but ensure dialog is focusable and can receive touch
                    addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                    addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
                    setFormat(PixelFormat.TRANSLUCENT)
                    setGravity(Gravity.CENTER)
                    setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                setContentView(view)
                setCancelable(false)
                show()
            }
            startTimer()
        } catch (e: Exception) {
            android.util.Log.e("CoolingPeriodDialog", "Error showing dialog: ${e.message}", e)
        }
    }

    private fun createDialogView(context: Context): View {
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xE6000000.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
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
        
        val icon = TextView(context).apply {
            text = "⏰"
            textSize = 48f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        contentContainer.addView(icon)
        
        val title = TextView(context).apply {
            text = "$appName is in cooling period"
            textSize = 20f
            setTextColor(0xFF000000.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        contentContainer.addView(title)
        
        val message = TextView(context).apply {
            text = "Please come back after the cooling period is complete."
            textSize = 16f
            setTextColor(0xFF666666.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        contentContainer.addView(message)
        
        val timerContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFFF0F0F0.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        
        val timerLabel = TextView(context).apply {
            text = "Time remaining"
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        timerContainer.addView(timerLabel)
        
        val timerText = TextView(context).apply {
            id = View.generateViewId()
            text = formatTimeRemaining(coolingEndTime)
            textSize = 32f
            setTextColor(0xFF007AFF.toInt())
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        timerTextView = timerText
        timerContainer.addView(timerText)
        contentContainer.addView(timerContainer)
        
        val okButton = Button(context).apply {
            text = "OK"
            isEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(32, 0, 32, 0)
            }
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setBackgroundColor(0xFF007AFF.toInt())
            textSize = 16f
            isAllCaps = false
            setOnClickListener {
                dismiss()
                onOkClicked?.invoke()
            }
        }
        contentContainer.addView(okButton)
        
        mainLayout.addView(contentContainer)
        return mainLayout
    }
    
    private fun startTimer() {
        timerHandler = Handler(Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val remaining = coolingEndTime - now
                
                if (remaining <= 0) {
                    dismiss()
                } else {
                    timerTextView?.text = formatTimeRemaining(coolingEndTime)
                    timerHandler?.postDelayed(this, 1000)
                }
            }
        }
        timerHandler?.post(timerRunnable!!)
    }
    
    private fun formatTimeRemaining(endTime: Long): String {
        val remaining = endTime - System.currentTimeMillis()
        if (remaining <= 0) {
            return "00:00"
        }
        
        val totalSeconds = (remaining / 1000).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun dismiss() {
        timerRunnable?.let { timerHandler?.removeCallbacks(it) }
        timerHandler = null
        timerRunnable = null
        timerTextView = null
        dialog?.dismiss()
        dialog = null
        activeDialog.set(null)
    }
}

