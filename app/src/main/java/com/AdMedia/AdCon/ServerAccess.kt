package com.AdMedia.AdCon

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class ServerAccess(context_: Context) {
    private val context = context_
    private val conTimeout: Int = 10 * 1000   // 10 sek
    private val TAG = this::class.qualifiedName.toString()

    @RequiresApi(Build.VERSION_CODES.O)
    fun jsonDownload(urlAddr: String): String {
        val emptyStr: String = ""
        var jsonStr: String
        if (NetworkChecker(context).isTransportAvailable(context)) {
            try {
                if (urlAddr.isEmpty()) {
                    Logger.w(TAG, "jsonDownload URL EMPTY, return")
                    return emptyStr
                }
                Logger.v(TAG, "jsonDownload URL address: \"$urlAddr\"")
                var reader: BufferedReader? = null
                var connection = URL(urlAddr).openConnection() as HttpURLConnection
                connection.connectTimeout = conTimeout

                try {
                    connection.connect()
                    reader = connection.inputStream.bufferedReader()
                    jsonStr = reader.readText()
                    reader.close()
                } catch(e: IOException) {
                    Logger.e(TAG, "connectAndRead connection catch: ${e.message}")
                    MainActivity.instance?.lostServerConnection()
                    connection.disconnect()
                    reader?.close()
                    return emptyStr
                }
                connection.disconnect()

                if (jsonStr.isNotEmpty() && !jsonStr.contains("<b>Notice</b>")) {
                    MainActivity.instance?.serverConnectionEstablished()
                    Logger.v(TAG, "JSON from server: $jsonStr")
                    return jsonStr
                } else {
                    MainActivity.instance?.lostServerConnection()
                    Logger.w(TAG, "CONNECTION PROBLEM")
                }
            } catch(e: Exception) {
                Logger.e(
                    TAG, "jsonDownload Exception catch(${e.message}), stop settings update"
                )
                e.printStackTrace()
                return emptyStr
            }
        } else {
            MainActivity.instance?.lostServerConnection()
            Logger.w(TAG, "NETWORK NOT AVAILABLE")
        }
        return emptyStr
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun sendJson(urlAddr: String, jsonObject: JSONObject): Boolean {
        var ret: Boolean = false;

        try
        {
            val conn = URL(urlAddr).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10 * 1000  // 10 sec timeout
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            // 3. add JSON content to POST request body
            setPostRequestContent(conn, jsonObject)

            try {
                Logger.v(TAG, "START Connect to server, timeout: ${conn.connectTimeout}")
                conn.connect()

//                conn.

                Logger.v(TAG, "HTTP responseMessage: ${conn.responseMessage}")
                conn.disconnect()
                ret = true
            } catch(e: IOException) {
                Logger.e(TAG, "sendJson connection catch: ${e.message}")
                MainActivity.instance?.lostServerConnection()
                conn.disconnect()
                return ret
            }

        } catch(e: UnknownHostException)
        {
            Logger.e(TAG, "sendJson UnknownHostException catch: ${e.message}")
        } catch(e: SocketTimeoutException)
        {
            Logger.e(TAG, "sendJson SocketTimeoutException catch: ${e.message}")
        } catch(e: Exception)
        {
           Logger.e(TAG, "sendJson Exception catch: ${e.message}")
        }
        return ret
    }

    private fun setPostRequestContent(conn: HttpURLConnection, jsonObject: JSONObject) {

        val os = conn.outputStream
        val writer = BufferedWriter(OutputStreamWriter(os, "UTF-8"))
        writer.write(jsonObject.toString())
        Logger.v(TAG, "setPostRequestContent jsonObject: ${jsonObject.toString()}")
        writer.flush()
        writer.close()
        os.close()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun downloadHarmonogramJson(domain: String, MAC: String): String {
        val url = "$domain/api/get/harmonogram/?tokenIph=$MAC"
        return ServerAccess(context).jsonDownload(url)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun downloadModuleJson(domain: String, MAC: String): String {
        val url = "$domain/api/get/module/?tokenIph=$MAC"
        return ServerAccess(context).jsonDownload(url)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun downloadMediaJson(domain: String, MAC: String): String {
        val url = "$domain/api/get/media/?tokenIph=$MAC"
        return ServerAccess(context).jsonDownload(url)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun downloadSettingsJson(domain: String, MAC: String): String {
        val url = "$domain/api/get/settings/?tokenIph=$MAC"
        return ServerAccess(context).jsonDownload(url)
    }
}
