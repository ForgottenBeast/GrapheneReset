package net.oblivion.wipe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        net.oblivion.wipe.trigger.broadcast.BroadcastReceiver().onReceive(context, intent)
    }
}