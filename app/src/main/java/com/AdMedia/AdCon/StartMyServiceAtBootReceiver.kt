package com.AdMedia.AdCon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

class StartMyServiceAtBootReceiver : BroadcastReceiver() {
    val TAG = this::class.qualifiedName.toString()

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Logger.i(TAG, "RECEIVED ACTION_BOOT_COMPLETED, starting MainActivity")
            MainActivity.afterBoot = true
            MainActivity.firstLaunch = true
            val i = Intent(context, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } else if (intent.action == Intent.ACTION_DATE_CHANGED) {
            Logger.i(TAG, "RECEIVED ACTION_DATE_CHANGED, restarting MainActivity")
        } else {
            Logger.i(TAG, "RECEIVED UNKNOWN ACTION: ${intent.action}")
        }
    }
}