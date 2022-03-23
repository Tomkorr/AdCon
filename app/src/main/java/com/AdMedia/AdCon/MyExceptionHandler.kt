package com.AdMedia.AdCon

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi


@RequiresApi(Build.VERSION_CODES.O)
class MyExceptionHandler(private val activity: Activity, private val context: Context) :
    Thread.UncaughtExceptionHandler {
    private val TAG: String = this::class.qualifiedName.toString()

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        Logger.w(TAG, "HANDLING uncaught Exception, restarting App")
        RestartApp().restartApp()
    }
}