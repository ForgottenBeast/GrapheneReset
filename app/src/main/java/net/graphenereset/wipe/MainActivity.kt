package net.graphenereset.wipe

import android.app.Activity
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.widget.ImageView
import android.widget.NumberPicker
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.content.ContextCompat
import net.graphenereset.wipe.Helpers.DatabaseHelper
import net.graphenereset.wipe.Helpers.Validator
import net.graphenereset.wipe.databinding.ActivityMainBinding
import net.graphenereset.wipe.trigger.lock.LockJobManager
import net.graphenereset.wipe.trigger.shared.NotificationManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.ncorti.slidetoact.SlideToActView
import java.util.Locale

open class MainActivity : AppCompatActivity() {
    companion object {
        var id: String = ""
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
    }
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Preferences
    private var setupDone = false;
    private lateinit var view: View
    private lateinit var prefsdb: Preferences
    private lateinit var validator: Validator
    private lateinit var dpm: DevicePolicyManager
    private lateinit var inflater: LayoutInflater
    private var popupView: View? = null
    private var util: Utils? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        prefs.copyTo(prefsdb, key)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        window.statusBarColor = ContextCompat.getColor(this, R.color.background)
        validator = Validator()
        inflater = LayoutInflater.from(this)
        dpm = this.getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        util = Utils(this@MainActivity)

        if (intent.getBooleanExtra("open_popup", false)) {
            setupDone = true
            intent.removeExtra("open_popup")
            showFirstPopup()
        }

        init1()
        init2()
        qrCodeOnClick()
        showInformation()
        initSpinner()
        initProtectionToggle()
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("GrapheneReset", "MainActivity.onResume() called")

        // First: device admin
        val adminEnabled = isDeviceAdminEnabled()
        android.util.Log.d("GrapheneReset", "Device admin enabled: $adminEnabled")
        if (!adminEnabled) {
            android.util.Log.w("GrapheneReset", "Device admin NOT enabled - opening settings")
            deviceAdminPermission()
            return
        }

        // Then: notification listener
        // Enable the service component first so it appears in settings
        Utils(this).setNotificationEnabled(true)

        val notifEnabled = validator.isNotificationServiceEnabled(this)
        android.util.Log.d("GrapheneReset", "Notification listener enabled: $notifEnabled")
        if (!notifEnabled) {
            android.util.Log.w("GrapheneReset", "Notification listener NOT enabled - opening settings")
            notificationAccessPermission()
            return
        }

        // Finally: POST_NOTIFICATIONS permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifPermission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(notifPermission) != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w("GrapheneReset", "POST_NOTIFICATIONS NOT granted - requesting")
                requestPermissions(arrayOf(notifPermission), REQUEST_CODE_POST_NOTIFICATIONS)
                return
            }
            android.util.Log.d("GrapheneReset", "POST_NOTIFICATIONS granted")

            // Check if notification channel is enabled
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channel = notificationManager.getNotificationChannel(net.graphenereset.wipe.trigger.shared.NotificationManager.CHANNEL_DEFAULT_ID)
            if (channel != null && channel.importance == android.app.NotificationManager.IMPORTANCE_NONE) {
                android.util.Log.w("GrapheneReset", "Notification channel disabled - showing warning")
                Toast.makeText(
                    this,
                    "Warning: Notifications are disabled. Service may not stay active.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        android.util.Log.i("GrapheneReset", "All permissions granted - applying protection state")
        val p = Preferences.new(this)

        // Apply current protection state (respects user's toggle setting)
        if (p.isEnabled) {
            android.util.Log.d("GrapheneReset", "Protection enabled - setting up triggers")
            if (p.triggers == 0) {
                // First time or triggers were cleared - enable functional triggers by default
                p.triggers = Trigger.TILE.value or Trigger.LOCK.value or Trigger.NOTIFICATION.value
            }
            android.util.Log.d("GrapheneReset", "Triggers set: ${p.triggers}, calling Utils.setEnabled()")
            Utils(this).setEnabled(true)
            android.util.Log.i("GrapheneReset", "Utils.setEnabled(true) completed")
        } else {
            android.util.Log.d("GrapheneReset", "Protection disabled by user - not starting service")
        }

        // Update toggle UI to reflect current state
        findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.protection_toggle)?.isChecked = p.isEnabled
        updateProtectionStatus(p.isEnabled, findViewById(R.id.protection_status))

        // Update trigger checkboxes
        val triggers = p.triggers
        findViewById<android.widget.CheckBox>(R.id.trigger_lock)?.apply {
            isChecked = triggers.and(Trigger.LOCK.value) != 0
            isEnabled = p.isEnabled
        }
        findViewById<android.widget.CheckBox>(R.id.trigger_tile)?.apply {
            isChecked = triggers.and(Trigger.TILE.value) != 0
            isEnabled = p.isEnabled
        }
        findViewById<android.widget.CheckBox>(R.id.trigger_notification)?.apply {
            isChecked = triggers.and(Trigger.NOTIFICATION.value) != 0
            isEnabled = p.isEnabled
        }
    }

    private fun init1() {
        prefs = Preferences(this)
        prefsdb = Preferences(this, encrypted = false)
        prefs.copyTo(prefsdb)
    }

    private fun init2() {
        NotificationManager(this).createNotificationChannels()

        val dbHelper = DatabaseHelper(this)
        id = dbHelper.getOwnCode()

        showId()
        copyIdOnClick()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(setAppLocale(newBase))
    }

    override fun onStart() {
        super.onStart()
        prefs.registerListener(prefsListener)
    }

    override fun onStop() {
        super.onStop()
        prefs.unregisterListener(prefsListener)
    }

    private fun showId() {
        this@MainActivity.findViewById<TextView>(R.id.idDisplay).text = id
        prefs.secret = id
    }

    private fun copyIdOnClick() {
        val copyButton: ImageButton = findViewById(R.id.copyButton)
        copyButton.setOnClickListener {
            copyToClipboard()
        }
    }

    private fun copyToClipboard() {
        val clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("showIdField", id)
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, getString(R.string.id_copied), Toast.LENGTH_SHORT).show()
    }

    fun setAppLocale(context: Context): Context {
        val locale = Locale.getDefault()
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    private fun adminComponent() =
        ComponentName(this, net.graphenereset.wipe.admin.DeviceAdminReceiver::class.java)

    private fun isDeviceAdminEnabled() =
        dpm.isAdminActive(adminComponent())

    private fun deviceAdminPermission() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent())
        }
        startActivity(intent)
    }

    private fun notificationAccessPermission() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    private fun showFirstPopup() {
        val dialogView = inflater.inflate(R.layout.popup_one, null)
        val dialog = AlertDialog.Builder(this@MainActivity)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val buttonToSecond = dialogView.findViewById<Button>(R.id.proceed_first_popup)
        buttonToSecond.setOnClickListener {
            dialog.dismiss()
            showFinalPopup()
        }

        val cancelFirstPopup = dialogView.findViewById<Button>(R.id.cancel_first_popup)
        cancelFirstPopup.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun showFinalPopup() {
        view = inflater.inflate(R.layout.final_wipe_popup, null)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        val wipeSlider = view.findViewById<SlideToActView>(R.id.final_wipe_slider)

        wipeSlider.onSlideCompleteListener = object : SlideToActView.OnSlideCompleteListener {
            override fun onSlideComplete(view: SlideToActView) {
                try {
                    WipeManager.requestWipe(this@MainActivity, Trigger.TILE)
                } finally {
                    wipeSlider.resetSlider()
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_popup", false)) {
            intent.removeExtra("open_popup")
            showFirstPopup()
        }
    }

    private fun showInformation() {
        val infoButton = findViewById<ImageButton>(R.id.infoButton)
        var infoModal: PopupWindow

        infoButton.setOnClickListener {
            popupView = LayoutInflater.from(this)
                .inflate(R.layout.info_popup, null)

            infoModal = centerModal(this@MainActivity, 0.95f)

            val tv = popupView?.findViewById<TextView>(R.id.information_second_part)
            tv?.replaceFffcWithDrawable(R.drawable.pencil)

            popupView?.findViewById<Button>(R.id.closeInfoPopup)?.setOnClickListener {
                infoModal.dismiss()
            }
        }
    }

    private fun centerModal(context: Context, widthPercent: Float = 0.9f): PopupWindow {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val popupWidth = (screenWidth * widthPercent).toInt()  // 0.7f = 70%

        val popupWindow = PopupWindow(
            popupView,
            popupWidth,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            elevation = 10f
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        popupWindow.showAtLocation(
            (context as Activity).window.decorView.rootView,
            Gravity.CENTER,
            0,
            0
        )

        return popupWindow
    }

    private fun qrCodeOnClick() {
        val qrCodeButton = findViewById<ImageButton>(R.id.qrButton)
        qrCodeButton.setOnClickListener {
            setQRCode(id)
        }
    }

    private fun initSpinner() {
        val spinner: AppCompatSpinner = findViewById(R.id.wipe_time_options)
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.wipe_time_options,
            R.layout.spinner_index
        )
        adapter.setDropDownViewResource(R.layout.spinner_index)
        spinner.adapter = adapter

        // Make dropdown width match the spinner
        spinner.post {
            spinner.dropDownWidth = spinner.width
        }

        // Restore saved selection
        val savedMinutes = Preferences.new(this@MainActivity).triggerLockCount
        val minutesInDay = 24 * 60
        val position = when (savedMinutes) {
            7 * minutesInDay -> 0
            14 * minutesInDay -> 1
            30 * minutesInDay -> 2
            60 * minutesInDay -> 3
            90 * minutesInDay -> 4
            else -> 5
        }
        spinner.setSelection(position) //This defines the default time a phones needs to be not unlocked before wipe.

        // Show custom time display if position is 5 (Custom)
        updateCustomTimeDisplay(position, savedMinutes)

        // Make custom time display clickable to edit the value
        val customTimeDisplay: TextView = findViewById(R.id.custom_time_display)
        customTimeDisplay.setOnClickListener {
            showCustomTimeDialog(spinner, spinner.selectedItemPosition)
        }

        selectWipeTime(spinner)
    }

    private fun selectWipeTime(spinner: Spinner) {
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var previousPosition = spinner.selectedItemPosition

            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val minutesInDay = 24 * 60
                when (position) {
                    0 -> {
                        setWipeTime(7 * minutesInDay)
                        previousPosition = position
                        updateCustomTimeDisplay(position, 7 * minutesInDay)
                        Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_LONG).show()
                    }
                    1 -> {
                        setWipeTime(14 * minutesInDay)
                        previousPosition = position
                        updateCustomTimeDisplay(position, 14 * minutesInDay)
                        Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_LONG).show()
                    }
                    2 -> {
                        setWipeTime(30 * minutesInDay)
                        previousPosition = position
                        updateCustomTimeDisplay(position, 30 * minutesInDay)
                        Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_LONG).show()
                    }
                    3 -> {
                        setWipeTime(60 * minutesInDay)
                        previousPosition = position
                        updateCustomTimeDisplay(position, 60 * minutesInDay)
                        Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_LONG).show()
                    }
                    4 -> {
                        setWipeTime(90 * minutesInDay)
                        previousPosition = position
                        updateCustomTimeDisplay(position, 90 * minutesInDay)
                        Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_LONG).show()
                    }
                    5 -> {
                        showCustomTimeDialog(spinner, previousPosition)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // This fires if the user closes the spinner without picking anything
            }
        }
    }

    private fun setWipeTime(minutes: Int) {
        Preferences.new(this@MainActivity).triggerLockCount = minutes

        val lockJobManager = LockJobManager(this@MainActivity)
        lockJobManager.cancel()

        // If device is currently locked, reschedule the job with the new timeout
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            km.isDeviceLocked
        } else {
            km.isKeyguardLocked
        }

        if (isLocked) {
            val prefs = Preferences(this@MainActivity, encrypted = false)
            if (prefs.lastUnlockTime > 0L) {
                lockJobManager.schedule()
            }
        }
    }

    private fun initProtectionToggle() {
        val toggle = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.protection_toggle)
        val statusText = findViewById<TextView>(R.id.protection_status)
        val lockCheckbox = findViewById<android.widget.CheckBox>(R.id.trigger_lock)
        val tileCheckbox = findViewById<android.widget.CheckBox>(R.id.trigger_tile)
        val notificationCheckbox = findViewById<android.widget.CheckBox>(R.id.trigger_notification)

        // Set initial state from preferences
        val p = Preferences.new(this@MainActivity)
        val isEnabled = p.isEnabled
        val triggers = p.triggers

        toggle.isChecked = isEnabled
        updateProtectionStatus(isEnabled, statusText)

        // Set checkbox states from saved triggers
        lockCheckbox.isChecked = triggers.and(Trigger.LOCK.value) != 0
        tileCheckbox.isChecked = triggers.and(Trigger.TILE.value) != 0
        notificationCheckbox.isChecked = triggers.and(Trigger.NOTIFICATION.value) != 0

        // Enable/disable checkboxes based on protection state
        lockCheckbox.isEnabled = isEnabled
        tileCheckbox.isEnabled = isEnabled
        notificationCheckbox.isEnabled = isEnabled

        // Handle toggle changes
        toggle.setOnCheckedChangeListener { _, isChecked ->
            val prefs = Preferences.new(this@MainActivity)
            prefs.isEnabled = isChecked

            // Enable/disable checkboxes
            lockCheckbox.isEnabled = isChecked
            tileCheckbox.isEnabled = isChecked
            notificationCheckbox.isEnabled = isChecked

            if (isChecked) {
                // Apply selected triggers
                applyTriggers(prefs, lockCheckbox, tileCheckbox, notificationCheckbox)
                updateProtectionStatus(true, statusText)
                Toast.makeText(this@MainActivity, "Protection enabled", Toast.LENGTH_SHORT).show()
            } else {
                // Disable all triggers
                prefs.triggers = 0
                Utils(this@MainActivity).setEnabled(false)
                updateProtectionStatus(false, statusText)
                Toast.makeText(this@MainActivity, "Protection disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle individual trigger checkbox changes
        val triggerChangeListener = android.widget.CompoundButton.OnCheckedChangeListener { _, _ ->
            if (toggle.isChecked) {
                val prefs = Preferences.new(this@MainActivity)
                applyTriggers(prefs, lockCheckbox, tileCheckbox, notificationCheckbox)
            }
        }

        lockCheckbox.setOnCheckedChangeListener(triggerChangeListener)
        tileCheckbox.setOnCheckedChangeListener(triggerChangeListener)
        notificationCheckbox.setOnCheckedChangeListener(triggerChangeListener)
    }

    private fun applyTriggers(
        prefs: Preferences,
        lockCheckbox: android.widget.CheckBox,
        tileCheckbox: android.widget.CheckBox,
        notificationCheckbox: android.widget.CheckBox
    ) {
        var triggers = 0
        if (lockCheckbox.isChecked) triggers = triggers or Trigger.LOCK.value
        if (tileCheckbox.isChecked) triggers = triggers or Trigger.TILE.value
        if (notificationCheckbox.isChecked) triggers = triggers or Trigger.NOTIFICATION.value

        prefs.triggers = triggers
        Utils(this@MainActivity).setEnabled(true)
    }

    private fun updateProtectionStatus(enabled: Boolean, statusText: TextView) {
        statusText.text = if (enabled) {
            "Active - Monitoring lock timeout"
        } else {
            "Disabled - Device not protected"
        }
    }

    private fun showCustomTimeDialog(spinner: Spinner, previousPosition: Int) {
        val dialogView = inflater.inflate(R.layout.dialog_custom_time, null)
        val dialog = AlertDialog.Builder(this@MainActivity)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val valuePicker = dialogView.findViewById<NumberPicker>(R.id.days_picker)
        val unitSpinner = dialogView.findViewById<Spinner>(R.id.unit_spinner)

        // Set up custom adapter for unit spinner to fix dark mode text color
        val unitAdapter = ArrayAdapter.createFromResource(
            this@MainActivity,
            R.array.time_units,
            R.layout.spinner_index
        )
        unitAdapter.setDropDownViewResource(R.layout.spinner_index)
        unitSpinner.adapter = unitAdapter

        // Get current saved time and determine best unit to display
        val savedMinutes = Preferences.new(this@MainActivity).triggerLockCount
        val (defaultValue, defaultUnit) = when {
            savedMinutes < 60 -> Pair(savedMinutes, 0) // Minutes
            savedMinutes < 24 * 60 -> Pair(savedMinutes / 60, 1) // Hours
            else -> Pair(savedMinutes / (24 * 60), 2) // Days
        }

        // Set up initial values
        unitSpinner.setSelection(defaultUnit)
        updatePickerRange(valuePicker, defaultUnit, defaultValue)

        // Update picker range when unit changes
        unitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updatePickerRange(valuePicker, position, valuePicker.value)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val okButton = dialogView.findViewById<Button>(R.id.ok_button)
        okButton.setOnClickListener {
            val selectedValue = valuePicker.value
            val selectedUnit = unitSpinner.selectedItemPosition

            val totalMinutes = when (selectedUnit) {
                0 -> selectedValue // Minutes
                1 -> selectedValue * 60 // Hours
                2 -> selectedValue * 24 * 60 // Days
                else -> selectedValue * 24 * 60
            }

            setWipeTime(totalMinutes)
            updateCustomTimeDisplay(5, totalMinutes)

            // Automatically check the lock timeout checkbox when setting a custom time
            val lockCheckbox = findViewById<android.widget.CheckBox>(R.id.trigger_lock)
            if (!lockCheckbox.isChecked) {
                lockCheckbox.isChecked = true

                // If protection is enabled, apply the updated triggers
                val toggle = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.protection_toggle)
                if (toggle.isChecked) {
                    val prefs = Preferences.new(this@MainActivity)
                    val tileCheckbox = findViewById<android.widget.CheckBox>(R.id.trigger_tile)
                    val notificationCheckbox = findViewById<android.widget.CheckBox>(R.id.trigger_notification)
                    applyTriggers(prefs, lockCheckbox, tileCheckbox, notificationCheckbox)
                }
            }

            dialog.dismiss()
            Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_LONG).show()
        }

        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        cancelButton.setOnClickListener {
            spinner.setSelection(previousPosition)
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun updatePickerRange(picker: NumberPicker, unit: Int, currentValue: Int) {
        when (unit) {
            0 -> { // Minutes
                picker.minValue = 1
                picker.maxValue = 1440 // 24 hours
                picker.value = currentValue.coerceIn(1, 1440)
            }
            1 -> { // Hours
                picker.minValue = 1
                picker.maxValue = 720 // 30 days
                picker.value = currentValue.coerceIn(1, 720)
            }
            2 -> { // Days
                picker.minValue = 1
                picker.maxValue = 365
                picker.value = currentValue.coerceIn(1, 365)
            }
        }
    }

    private fun setQRCode(text: String) {
        popupView = inflater.inflate(R.layout.qrcode_popup, null)

        popupView?.setPadding(0, 0, 0, 0)

        val size = 500
        val hints = hashMapOf<EncodeHintType, Any>(
            EncodeHintType.MARGIN to 0,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H
        )
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        // Rounded corners
        val roundedBmp = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(roundedBmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, bmp.width, bmp.height)
        val rectF = RectF(rect)
        val cornerRadius = 8 * resources.displayMetrics.density
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bmp, rect, rect, paint)

        // Put image + text into the inflated view
        popupView?.findViewById<ImageView>(R.id.qrImageView)?.apply {
            // Force the ImageView to a square size that matches your layout (match layout file)
            layoutParams = (layoutParams ?: android.view.ViewGroup.LayoutParams(220, 220)).apply {
                width = (220 * resources.displayMetrics.density).toInt()
                height = (220 * resources.displayMetrics.density).toInt()
            }
            scaleType = ImageView.ScaleType.FIT_XY
            setImageBitmap(roundedBmp)
        }
        popupView?.findViewById<TextView>(R.id.QRcontent)?.text = text

        val popupWindow = centerModal(this@MainActivity, 0.8f)

        popupView?.findViewById<Button>(R.id.closeQrPopup)?.setOnClickListener {
            popupWindow.dismiss()
        }

        popupWindow.contentView.setPadding(60, 100, 60, 60)

        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = true
    }

    private fun updateCustomTimeDisplay(position: Int, minutes: Int) {
        val customTimeDisplay: TextView = findViewById(R.id.custom_time_display)

        if (position == 5) {
            // Position 5 is "Custom" - show the display in the most appropriate unit
            val displayText = when {
                minutes < 60 -> getString(R.string.current_custom_time_minutes, minutes)
                minutes < 24 * 60 -> getString(R.string.current_custom_time_hours, minutes / 60)
                else -> getString(R.string.current_custom_time, minutes / (24 * 60))
            }
            customTimeDisplay.text = displayText
            customTimeDisplay.visibility = View.VISIBLE
            // Make it look clickable
            customTimeDisplay.isClickable = true
            customTimeDisplay.isFocusable = true
        } else {
            // Preset option selected - hide the display
            customTimeDisplay.visibility = View.GONE
        }
    }

    fun TextView.replaceFffcWithDrawable(@DrawableRes drawableRes: Int) {
        val placeholder = '\uFFFC'
        val ssb = SpannableStringBuilder(text)

        val d = AppCompatResources.getDrawable(context, drawableRes)!!.mutate()
        val sizePx = (textSize * 1.1f).toInt()
        d.setBounds(0, 0, sizePx, sizePx)

        var i = ssb.indexOf(placeholder.toString())
        while (i != -1) {
            ssb.setSpan(ImageSpan(d, ImageSpan.ALIGN_BOTTOM), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            i = ssb.indexOf(placeholder.toString(), i + 1)
        }

        text = ssb
    }
}