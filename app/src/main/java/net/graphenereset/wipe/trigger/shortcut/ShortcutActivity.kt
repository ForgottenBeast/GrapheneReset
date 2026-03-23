package net.graphenereset.wipe.trigger.shortcut

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import net.graphenereset.wipe.Trigger
import net.graphenereset.wipe.trigger.broadcast.BroadcastReceiver

class ShortcutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BroadcastReceiver.panic(this, intent, Trigger.SHORTCUT)
        finishAndRemoveTask()
    }
}