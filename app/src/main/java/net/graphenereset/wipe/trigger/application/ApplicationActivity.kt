package net.graphenereset.wipe.trigger.application

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import net.graphenereset.wipe.Trigger
import net.graphenereset.wipe.Utils

class ApplicationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Utils(this).fire(Trigger.APPLICATION)
        finishAndRemoveTask()
    }
}