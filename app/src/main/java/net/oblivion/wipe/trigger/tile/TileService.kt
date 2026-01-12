package net.oblivion.wipe.trigger.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import net.oblivion.wipe.MainActivity
import java.util.*
import kotlin.concurrent.timerTask

import net.oblivion.wipe.admin.DeviceAdminManager
import net.oblivion.wipe.Preferences
import net.oblivion.wipe.Trigger
import net.oblivion.wipe.Utils

@RequiresApi(Build.VERSION_CODES.N)
class TileService : TileService() {
    companion object {
        const val ACTION_TRIGGER_WIPE = "net.oblivion.wipe.ACTION_TRIGGER_WIPE"
    }

    private lateinit var prefs: Preferences
    private lateinit var admin: DeviceAdminManager
    private lateinit var utils: Utils
    private var counter = 0
    private var timer: Timer? = null

    override fun onCreate() {
        super.onCreate()
        init()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TRIGGER_WIPE) {
            init()
            tileWipe()
        }
        return START_NOT_STICKY
    }

    private fun init() {
        prefs = Preferences.new(this)
        admin = DeviceAdminManager(this)
        utils = Utils(this)
    }

    override fun onClick() {
        super.onClick()
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("open_popup", true)
        }
        startActivityAndCollapse(activityIntent)
    }

    fun tileWipe() {
        if (!prefs.isWipeData) {
            utils.fire(Trigger.TILE, false)
            return
        }
        val v = counter
        counter++
        when (v) {
            0 -> {
                update(Tile.STATE_ACTIVE)
                timer?.cancel()
                timer = Timer()
                timer?.schedule(timerTask {
                    utils.fire(Trigger.TILE)
                }, prefs.triggerTileDelay)
            }

            else -> {
                timer?.cancel()
                update(Tile.STATE_INACTIVE)
                counter = 0
            }
        }
    }

    private fun update(tileState: Int) {
        qsTile.state = tileState
        qsTile.updateTile()
    }
}
