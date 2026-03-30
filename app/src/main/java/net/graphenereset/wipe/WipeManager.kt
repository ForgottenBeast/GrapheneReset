package net.graphenereset.wipe

import android.content.Context
import net.graphenereset.wipe.admin.DeviceAdminManager

/**
 * The ONLY place in the app allowed to call wipeData().
 */
object WipeManager {

    // Only these 3 triggers are allowed to wipe:
    private val allowed = setOf(Trigger.TILE, Trigger.LOCK, Trigger.NOTIFICATION)

    fun requestWipe(ctx: Context, trigger: Trigger, allowRecast: Boolean = true) {
        if (!allowed.contains(trigger)) return

        val prefs = Preferences.new(ctx)

        // Your app currently never set these anywhere -> we set them in MainActivity (see below)
        if (!prefs.isEnabled) return
        if (prefs.triggers.and(trigger.value) == 0) return

        val admin = DeviceAdminManager(ctx)
        if (!admin.isActive()) {
            android.util.Log.w("GrapheneReset", "WipeManager: Device admin not active, cannot wipe")
            return
        }

        try {
            android.util.Log.i("GrapheneReset", "WipeManager: Initiating wipe for trigger $trigger")
            admin.lockNow()
            admin.wipeData()
            android.util.Log.i("GrapheneReset", "WipeManager: Wipe initiated successfully")
        } catch (e: Exception) {
            android.util.Log.e("GrapheneReset", "WipeManager: Wipe failed - ${e.javaClass.simpleName}: ${e.message}", e)
        }

        if (prefs.isRecastEnabled && allowRecast) {
            Utils(ctx).recastPublic()
        }
    }
}
