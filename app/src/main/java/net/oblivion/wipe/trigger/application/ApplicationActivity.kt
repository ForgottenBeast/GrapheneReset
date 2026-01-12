package net.oblivion.wipe.trigger.application

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import net.oblivion.wipe.Trigger
import net.oblivion.wipe.Utils

class ApplicationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Utils(this).fire(Trigger.APPLICATION)
        finishAndRemoveTask()
    }
}