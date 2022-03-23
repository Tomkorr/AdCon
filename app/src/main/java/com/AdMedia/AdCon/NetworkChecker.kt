package com.AdMedia.AdCon

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.PrintWriter
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*

@RequiresApi(Build.VERSION_CODES.O)
class NetworkChecker(val context: Context) : TimerTask() {
    val TAG: String = this::class.qualifiedName.toString()

    @RequiresApi(Build.VERSION_CODES.O)
    fun isNetworkAvailable(context: Context): Boolean {
        var ret: Boolean = false
        try {
            if (!isTransportAvailable(context)) {
                Logger.w(TAG, "isNetworkAvailable NO TRANSPORT")
                return ret
            }

            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

            if (capabilities != null) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    Logger.v(TAG, "NET_CAPABILITY_INTERNET available")
                    ret = MainActivity.instance?.isServerConnection() == true
                    return ret
                }
                else {
                    Logger.w(TAG, "NO INTERNET - NET_CAPABILITY_INTERNET unavailable!")
                    return ret
                }
            }
            Logger.e(TAG, "connectivityManager or capabilities NULL")
            return ret
        } catch (e: Exception) {
            Logger.e(TAG, "isNetworkAvailable catch" + e.printStackTrace())
            return ret
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun isTransportAvailable(context: Context): Boolean {
        try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

            if (capabilities != null) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) or
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) or
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                ) {
                    Logger.v(TAG, "TRANSPORT available")
                    return true
                } else {
                    Logger.w(TAG, "NO INTERNET - TRANSPORT unavailable!")
                    return false
                }
            }
            Logger.e(TAG, "connectivityManager or capabilities NULL")
            return false
        } catch (e: Exception) {
            Logger.e(TAG, "isTransportAvailable catch" + e.printStackTrace())
            return false
        }
    }

    fun getMacAddress(interfaceName: String = MainActivity.networkInterface): String {
        var macAddress: String = ""// "f0:ce:ee:21:88:54" // default

        if (macAddress.isEmpty()) {
            Logger.d(TAG, "getMacAddress reading MAC address...")
            try {
                val interfaces: Enumeration<NetworkInterface> =
                    NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface: NetworkInterface = interfaces.nextElement()
                    if (TextUtils.equals(networkInterface.name, interfaceName)) {
                        val bytes = networkInterface.hardwareAddress
                        val builder = StringBuilder()
                        for (b in bytes) {
                            builder.append(String.format("%02x:", b))
                        }
                        if (builder.isNotEmpty()) {
                            builder.deleteCharAt(builder.length - 1)
                        }
                        macAddress = builder.toString()
                        Logger.d(TAG, "getMacAddress using: $macAddress")
                    }
                }
            } catch(e: SocketException) {
                Logger.e(TAG, "getMacAddress catch $e")
            }
        } else {
            Logger.w(TAG, "getMacAddress USING FIXED MAC: $macAddress")
        }
        return "f0:ce:ee:21:88:54" //macAddress
    }

    override fun run() {

        if (!NetworkChecker(context).isNetworkAvailable(context)) {
            if (File(context.getExternalFilesDir(null)?.absolutePath.toString() + "/settings/network.txt").exists()) {
                File(context.getExternalFilesDir(null)?.absolutePath.toString() + "/settings/network.txt").appendText(
                    "Internet niedostepny o godzinie ${MainActivity.getTime()} **** \r\n"
                )
            }
        }

        val reader =
            BufferedReader(FileReader(context.getExternalFilesDir(null)?.absolutePath.toString() + "/settings/network.txt"))
        var lines = 0
        while (reader.readLine() != null) lines++
        reader.close()

        if (lines > 10) {
            val writer =
                PrintWriter(context.getExternalFilesDir(null)?.absolutePath.toString() + "/settings/network.txt")
            writer.print("")
            writer.close()
        }
    }
}