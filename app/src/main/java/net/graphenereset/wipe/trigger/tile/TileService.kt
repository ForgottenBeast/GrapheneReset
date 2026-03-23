package net.graphenereset.wipe.trigger.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import net.graphenereset.wipe.MainActivity

@RequiresApi(Build.VERSION_CODES.N)
class TileService : TileService() {
    override fun onClick() {
        super.onClick()

        val i = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("open_popup", true) // tells MainActivity to show the confirm popup
        }
        startActivityAndCollapse(i)
    }
}
