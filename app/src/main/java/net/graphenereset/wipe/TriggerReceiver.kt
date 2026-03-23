package net.graphenereset.wipe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        net.graphenereset.wipe.trigger.broadcast.BroadcastReceiver().onReceive(context, intent)
    }
}