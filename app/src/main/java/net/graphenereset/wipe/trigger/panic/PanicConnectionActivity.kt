package net.graphenereset.wipe.trigger.panic

import android.os.Bundle

import info.guardianproject.panic.PanicResponder
import net.graphenereset.wipe.MainActivity

class PanicConnectionActivity : MainActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (PanicResponder.checkForDisconnectIntent(this)) {
            finish()
            return
        }

    }

}