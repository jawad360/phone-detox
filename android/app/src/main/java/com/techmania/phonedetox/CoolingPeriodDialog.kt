package com.techmania.phonedetox

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
            // Dismiss any existing dialog (will show new one for this app)
            activeDialog.get()?.dismiss()

            // Use application context to avoid memory leaks
            val appContext = context.applicationContext
            val dialog = CoolingPeriodDialog(appContext, packageName, appName, coolingEndTime, onOkClicked)
            activeDialog.set(dialog)
            dialog.show()
        }
        
        fun dismiss() {
            activeDialog.get()?.dismiss()
        }
    }

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
        
        // Start timer
        startTimer()
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
        
        // Icon
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
        
        // Title
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
        
        // Message
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
        
        // Timer display
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
        
        // OK button - dismisses dialog and closes app
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
                    // Cooling period ended
                    dismiss()
                } else {
                    // Update timer display
                    timerTextView?.text = formatTimeRemaining(coolingEndTime)
                    timerHandler?.postDelayed(this, 1000) // Update every second
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

