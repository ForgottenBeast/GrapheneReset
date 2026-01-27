package net.oblivion.wipe

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import net.oblivion.wipe.trigger.notification.NotificationListenerService
import net.oblivion.wipe.trigger.panic.PanicConnectionActivity
import net.oblivion.wipe.trigger.panic.PanicResponderActivity
import net.oblivion.wipe.trigger.shared.ForegroundService
import net.oblivion.wipe.trigger.shared.RestartReceiver
import net.oblivion.wipe.trigger.shortcut.ShortcutActivity
import net.oblivion.wipe.trigger.shortcut.ShortcutManager
import net.oblivion.wipe.trigger.tile.TileService
import net.oblivion.wipe.trigger.usb.UsbReceiver

class Utils(private val ctx: Context) {
    companion object {
        fun setFlag(key: Int, value: Int, enabled: Boolean) =
            when(enabled) {
                true -> key.or(value)
                false -> key.and(value.inv())
            }
    }

    private val prefs by lazy { Preferences.new(ctx) }

    fun setEnabled(enabled: Boolean) {
        val triggers = prefs.triggers

        setTileEnabled(enabled && triggers.and(Trigger.TILE.value) != 0)
        setNotificationEnabled(enabled && triggers.and(Trigger.NOTIFICATION.value) != 0)
        updateForegroundRequiredEnabled() // (LOCK / inactivity)
    }

    fun setPanicKitEnabled(enabled: Boolean) {
        setComponentEnabled(PanicConnectionActivity::class.java, enabled)
        setComponentEnabled(PanicResponderActivity::class.java, enabled)
    }

    fun setTileEnabled(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            setComponentEnabled(TileService::class.java, enabled)
    }

    fun setShortcutEnabled(enabled: Boolean) {
        val shortcut = ShortcutManager(ctx)
        if (!enabled) shortcut.remove()
        setComponentEnabled(ShortcutActivity::class.java, enabled)
        if (enabled) shortcut.push()
    }

    fun setBroadcastEnabled(enabled: Boolean) =
        setComponentEnabled(TriggerReceiver::class.java, enabled)

    fun setNotificationEnabled(enabled: Boolean) =
        setComponentEnabled(NotificationListenerService::class.java, enabled)

    fun updateApplicationEnabled() {
        val prefix = "${ctx.packageName}.trigger.application"
        val options = prefs.triggerApplicationOptions
        val enabled = prefs.isEnabled && prefs.triggers.and(Trigger.APPLICATION.value) != 0
        setComponentEnabled(
            "$prefix.SignalActivity",
            enabled && options.and(ApplicationOption.SIGNAL.value) != 0,
        )
        setComponentEnabled(
            "$prefix.TelegramActivity",
            enabled && options.and(ApplicationOption.TELEGRAM.value) != 0,
        )
        setComponentEnabled(
            "$prefix.ThreemaActivity",
            enabled && options.and(ApplicationOption.THREEMA.value) != 0,
        )
        setComponentEnabled(
            "$prefix.SessionActivity",
            enabled && options.and(ApplicationOption.SESSION.value) != 0,
        )
    }

    fun updateForegroundRequiredEnabled() {
        val enabled = prefs.isEnabled
        val triggers = prefs.triggers

        // ONLY LOCK/inactivity
        val foregroundEnabled = enabled && triggers.and(Trigger.LOCK.value) != 0

        setForegroundEnabled(foregroundEnabled)
        setComponentEnabled(RestartReceiver::class.java, foregroundEnabled)
    }


    private fun setForegroundEnabled(enabled: Boolean) =
        Intent(ctx.applicationContext, ForegroundService::class.java).also {
            if (enabled) ContextCompat.startForegroundService(ctx.applicationContext, it)
            else ctx.stopService(it)
        }

    private fun setComponentEnabled(cls: Class<*>, enabled: Boolean) =
        setComponentEnabled(ComponentName(ctx, cls), enabled)

    private fun setComponentEnabled(cls: String, enabled: Boolean) =
        setComponentEnabled(ComponentName(ctx, cls), enabled)

    private fun setComponentEnabled(componentName: ComponentName, enabled: Boolean) {
        try {
            ctx.packageManager.setComponentEnabledSetting(
                componentName,
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: IllegalArgumentException) {
            // Component missing from manifest (or renamed). Don't crash the whole app.
        }
    }

    fun fire(trigger: Trigger, safe: Boolean = true) {
        WipeManager.requestWipe(ctx, trigger, allowRecast = safe)
    }
    internal fun recastPublic() = recast()

    fun isDeviceLocked() = ctx.getSystemService(KeyguardManager::class.java).isDeviceLocked

    private fun recast() {
        val action = prefs.recastAction
        if (action.isEmpty()) return
        ctx.sendBroadcast(Intent(action).apply {
            val cls = prefs.recastReceiver.split('/')
            val packageName = cls.firstOrNull() ?: ""
            if (packageName.isNotEmpty()) {
                setPackage(packageName)
                if (cls.size == 2)
                    setClassName(
                        packageName,
                        "$packageName.${cls[1].trimStart('.')}",
                    )
            }
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            val extraKey = prefs.recastExtraKey
            if (extraKey.isNotEmpty()) putExtra(extraKey, prefs.recastExtraValue)
        })
    }


}