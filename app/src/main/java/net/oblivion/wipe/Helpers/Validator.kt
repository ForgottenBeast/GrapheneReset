package net.oblivion.wipe.Helpers

import android.content.Context
import android.provider.Settings
import android.text.TextUtils

class Validator {
    fun isNotificationServiceEnabled(context: Context) : Boolean
    {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return !TextUtils.isEmpty(flat) && flat.contains(pkgName)
    }
}