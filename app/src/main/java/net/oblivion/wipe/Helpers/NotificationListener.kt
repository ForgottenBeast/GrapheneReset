package net.oblivion.wipe.Helpers


import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import net.oblivion.wipe.MainActivity
import net.oblivion.wipe.Trigger
import net.oblivion.wipe.Utils

class NotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            try {
                val text = it.notification.extras.getCharSequence("android.text")?.toString() ?: return

                val myId = MainActivity.id

                if (myId.isNotEmpty() && text.contains(myId, ignoreCase = false)) {
                    //Wipe The Device
                    Toast.makeText(this, "Wiping!!", Toast.LENGTH_SHORT).show()
                    Utils(this).fire(Trigger.NOTIFICATION)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let {
            Log.d("NotifListener", "Notification removed from ${it.packageName}")
        }
    }
}