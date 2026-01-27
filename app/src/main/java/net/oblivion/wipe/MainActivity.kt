package net.oblivion.wipe

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.content.ContextCompat
import net.oblivion.wipe.Helpers.DatabaseHelper
import net.oblivion.wipe.Helpers.Validator
import net.oblivion.wipe.databinding.ActivityMainBinding
import net.oblivion.wipe.trigger.lock.LockJobManager
import net.oblivion.wipe.trigger.shared.NotificationManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.ncorti.slidetoact.SlideToActView
import java.util.Locale

open class MainActivity : AppCompatActivity() {
    companion object {
        var id: String = ""
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
    }

    override fun onResume() {
        super.onResume()

        // First: device admin
        if (!isDeviceAdminEnabled()) {
            deviceAdminPermission()
            return
        }

        // Then: notification listener
        if (!validator.isNotificationServiceEnabled(this)) {
            notificationAccessPermission()
            return
        }

        val p = Preferences.new(this)
        p.isEnabled = true
        p.triggers = Trigger.TILE.value or Trigger.LOCK.value or Trigger.NOTIFICATION.value
        Utils(this).setEnabled(true)
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
        ComponentName(this, net.oblivion.wipe.admin.DeviceAdminReceiver::class.java)

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
            else -> 2
        }
        spinner.setSelection(position) //This defines the default time a phones needs to be not unlocked before wipe.

        selectWipeTime(spinner)
    }

    private fun selectWipeTime(spinner: Spinner) {
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val minutesInDay = 24 * 60
                when (position) {
                    0 -> { setWipeTime(7 * minutesInDay)  }  // 1 week
                    1 -> { setWipeTime(14 * minutesInDay) }  // 2 weeks
                    2 -> { setWipeTime(30 * minutesInDay) }  // 1 month
                    3 -> { setWipeTime(60 * minutesInDay) }  // 2 months
                    4 -> { setWipeTime(90 * minutesInDay) }  // 3 months
                }

                Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_LONG).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // This fires if the user closes the spinner without picking anything
            }
        }
    }

    private fun setWipeTime(minutes: Int) {
        Preferences.new(this@MainActivity).triggerLockCount = minutes

        LockJobManager(this@MainActivity).cancel()
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