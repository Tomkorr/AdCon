package com.AdMedia.AdCon

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlin.system.exitProcess


class RestartApp() {
    private val TAG: String = this::class.qualifiedName.toString()

    @RequiresApi(Build.VERSION_CODES.O)
    fun restartApp() {
        Logger.w(TAG, "RESTARTING APP STARTED")
        val intent = Intent(MainActivity.instance?.baseContext, MainActivity::class.java)
        val pendingIntentId = 101
        val pendingIntent = PendingIntent.getActivity(
            MainActivity.instance,
            pendingIntentId,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )

        Thread.sleep(2000)

        val alarmManager =
            (MainActivity.instance?.getSystemService(Context.ALARM_SERVICE)) as AlarmManager
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pendingIntent)

        Logger.w(TAG, "AlarmManager set up, EXITING")
        exitProcess(2)
    }
}