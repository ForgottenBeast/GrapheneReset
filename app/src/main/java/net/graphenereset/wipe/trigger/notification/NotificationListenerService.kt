package net.graphenereset.wipe.trigger.notification

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import net.graphenereset.wipe.Preferences
import net.graphenereset.wipe.Trigger
import net.graphenereset.wipe.WipeManager

class NotificationListenerService : NotificationListenerService() {
    private lateinit var prefs: Preferences

    override fun onCreate() {
        super.onCreate()
        prefs = Preferences.new(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (!::prefs.isInitialized) prefs = Preferences.new(this)

        // Only if app armed + trigger enabled
        if (!prefs.isEnabled) return
        if (prefs.triggers and Trigger.NOTIFICATION.value == 0) return

        val secret = prefs.secret.trim()
        if (secret.isEmpty()) return

        val text = collectNotificationText(sbn.notification)

        // "message containing the secret code"
        if (!text.contains(secret, ignoreCase = true)) return

        // Optional: hide the notification that triggered the wipe
        cancelNotification(sbn.key)

        WipeManager.requestWipe(this, Trigger.NOTIFICATION)
    }

    private fun collectNotificationText(n: Notification): String {
        val e = n.extras ?: return ""
        val parts = ArrayList<String>(10)

        fun add(cs: CharSequence?) {
            val s = cs?.toString()?.trim()
            if (!s.isNullOrEmpty()) parts.add(s)
        }

        add(e.getCharSequence(Notification.EXTRA_TITLE))
        add(e.getCharSequence(Notification.EXTRA_TEXT))
        add(e.getCharSequence(Notification.EXTRA_BIG_TEXT))
        add(e.getCharSequence(Notification.EXTRA_SUB_TEXT))
        add(e.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))

        // InboxStyle often uses EXTRA_TEXT_LINES
        val lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        lines?.forEach { add(it) }

        // MessagingStyle (common for chat apps)
        @Suppress("DEPRECATION")
        val msgs = e.getParcelableArray(Notification.EXTRA_MESSAGES)
        msgs?.forEach { p ->
            val b = p as? Bundle ?: return@forEach
            add(b.getCharSequence("text"))
        }

        return parts.joinToString("\n")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            migrateNotificationFilter(
                FLAG_FILTER_TYPE_CONVERSATIONS or
                        FLAG_FILTER_TYPE_ALERTING or
                        FLAG_FILTER_TYPE_SILENT or
                        FLAG_FILTER_TYPE_ONGOING,
                null
            )
        }
    }
}
