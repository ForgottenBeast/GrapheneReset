package net.oblivion.wipe.trigger.shortcut

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import net.oblivion.wipe.Trigger
import net.oblivion.wipe.trigger.broadcast.BroadcastReceiver

class ShortcutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BroadcastReceiver.panic(this, intent, Trigger.SHORTCUT)
        finishAndRemoveTask()
    }
}