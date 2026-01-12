package net.oblivion.wipe.trigger.notification

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

import net.oblivion.wipe.Preferences
import net.oblivion.wipe.Trigger
import net.oblivion.wipe.Utils

class NotificationListenerService : NotificationListenerService() {
    private lateinit var prefs: Preferences
    private lateinit var utils: Utils

    override fun onCreate() {
        super.onCreate()
        init()
    }

    private fun init() {
        prefs = Preferences.new(this)
        utils = Utils(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        val secret = prefs.secret
        if (sbn.notification.extras[Notification.EXTRA_TEXT]?.toString()?.trim() != secret) return
        cancelNotification(sbn.key)

        utils.fire(Trigger.NOTIFICATION)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            migrateNotificationFilter(
                FLAG_FILTER_TYPE_CONVERSATIONS
                    or FLAG_FILTER_TYPE_ALERTING
                    or FLAG_FILTER_TYPE_SILENT
                    or FLAG_FILTER_TYPE_ONGOING,
                null,
            )
    }
}