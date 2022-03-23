package com.AdMedia.AdCon

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson

data class CheckServer(val domain: String, val ban: Int)

@RequiresApi(Build.VERSION_CODES.M)
class GetDomainAndBan(var context: Context) {
    private val TAG = this::class.qualifiedName.toString()

    @RequiresApi(Build.VERSION_CODES.O)
    private val urlAddr: String = "${MainActivity.checkDomainDefaultLink}${MainActivity.MAC}"

    @RequiresApi(Build.VERSION_CODES.O)
    fun connectAndRead(): CheckServer {
        var result = CheckServer("", 0);
        val gson = Gson()
        val jsonString = ServerAccess(context).jsonDownload(urlAddr)

        if (jsonString.isNotEmpty()){
            result = gson.fromJson(jsonString, CheckServer::class.java)
            Logger.d(TAG, "decoded domain: \"${result.domain}\" ban: ${result.ban}")
        } else{
            Logger.w(TAG, "CheckServer JSON EMPTY")
        }
        return result
    }
}