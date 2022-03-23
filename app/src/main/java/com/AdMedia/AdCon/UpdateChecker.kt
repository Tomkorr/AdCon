package com.AdMedia.AdCon

import SendMediaJson
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs


@RequiresApi(Build.VERSION_CODES.O)
class UpdateChecker(context_: Context) : TimerTask() {
    companion object {
        var isUpdate = false
        var lastUpdateId = 0
    }
    private val TAG = this::class.qualifiedName.toString()
    private val context = context_
    private val dbHelper = DatabaseDbHelper(context)
    private val maxTimeDiff = 60 // [sec]

    override fun run() {
        updateSettings(dbHelper, context)
    }

    fun downloadSettings(Domain: String, Mac: String): Settings? {
        var settings: Settings? = null
        val gson = Gson()

        if (Domain.isEmpty() or Mac.isEmpty()) {
            return settings
        }

        try {
            val jsonString = ServerAccess(context).downloadSettingsJson(Domain, Mac)
            if (jsonString.isNotEmpty()) {
                settings = gson.fromJson(jsonString, Settings::class.java)
                Logger.d(TAG, "Downloaded $settings");
            } else {
                Logger.w(TAG, "UNABLE TO DOWNLOAD SETTINGS");
            }
        } catch(e: java.lang.Exception) {
            Logger.e(TAG, "downloadSettings catch: ${e.message}");
        }
        return settings
    }

    fun updateFullConfiguration(updateId: Int, dbHelper: DatabaseDbHelper, context: Context): Boolean {
        var harmDone: Boolean = false
        var moduleDone: Boolean = false
        var mediaCfgDone: Boolean = false
        var retries: Int = 20
        val downloadTimeout: Long = 12 // [sec]

        while(!harmDone && retries > 0) {
            Logger.d(TAG, "Start Get Harmonogram Task")
            val harmTask = Harmonogram.GetHarmonogramTask(MainActivity.domain, MainActivity.MAC, dbHelper, context)
            harmTask.execute()

            harmDone = harmTask.get(downloadTimeout, TimeUnit.SECONDS)
            retries--

            if (harmDone) {
                Logger.d(TAG, "Get Harmonogram Task DONE")
            } else {
                Logger.w(TAG, "Get Harmonogram Task FAILED, retries $retries")
            }
        }

        while(!moduleDone && retries > 0) {
            Logger.d(TAG, "Start Get Module Task")

            val moduleTask = Module.GetModuleTask(MainActivity.domain, MainActivity.MAC, dbHelper, context)
            moduleTask.execute()

            moduleDone = moduleTask.get(downloadTimeout, TimeUnit.SECONDS)
            retries--

            if (moduleDone) {
                Logger.d(TAG, "Get Module Task DONE")
            } else {
                Logger.w(TAG, "Get Module Task FAILED, retries $retries")
            }
        }

        while(!mediaCfgDone && retries > 0) {
            Logger.d(TAG, "Start Get Media Config Task")

            val mediaConfiTask = Media.GetMediaConfigTask(updateId, MainActivity.domain, MainActivity.MAC, dbHelper, context)
            mediaConfiTask.execute()

            mediaCfgDone = mediaConfiTask.get(downloadTimeout, TimeUnit.SECONDS)
            retries--

            if (mediaCfgDone) {
                Logger.d(TAG, "Get Media Cfg Task DONE")
            } else {
                Logger.w(TAG, "Get Media Cfg Task FAILED, retries $retries")
            }
        }

        return harmDone && moduleDone && mediaCfgDone
    }

    fun updateSettings(dbHelper: DatabaseDbHelper, context: Context) {
        Logger.d(TAG, "updateSettings check started")

        try {
            val data = GetDomainAndBan(context).connectAndRead()
            if(data.domain == null || data.domain == "null") {
                Logger.d(TAG, "updateSettings detect null domain, STOP settings update")
                return
            }

            MainActivity.domain = data.domain
            MainActivity.deviceBanned = (data.ban == 1)

            if (MainActivity.deviceBanned) {
                Logger.e(TAG, "DEVICE BANNED, stop settings update")
                MediaActivity.instance?.finishAndRemoveTask()
                return
            }

            if(MainActivity.domain.isEmpty()) {
                Logger.e(TAG, "NO DOMAIN AVAILABLE, stop settings update")
                return
            }
        } catch(e: Exception) {
            Logger.e(
                TAG,
                "updateSettings/GetDomainAndBan Exception catch(${e.message}), stop settings update"
            )
            e.printStackTrace()
            return
        }

        val settings = downloadSettings(MainActivity.domain, MainActivity.MAC)

        try {
            if (settings != null) {

                val tsLong = System.currentTimeMillis() / 1000
                val timeDiff = tsLong - settings.serverTime
                if (abs(timeDiff) > maxTimeDiff ) {
                    Logger.w(TAG, "Time diff TO BIG (local - server > $maxTimeDiff sec), diff $timeDiff [sec]")
                } else {
                    Logger.d(TAG, "Time diff (local - server) = $timeDiff [sec]")
                }

                val idSynch = dbHelper.getCurrentIDSynch()

                if ((idSynch != settings.idSynch) || MainActivity.afterBoot || MainActivity.appStart) {
                    Logger.i(TAG, "UPDATE REQUIRED for new idSynch: ${settings.idSynch}, old was $idSynch, STOP MEDIA PLAY")
                    MainActivity.afterBoot = false
                    MainActivity.appStart = false
                    isUpdate = true
                    lastUpdateId = settings.idSynch

                    MainActivity.instance?.infoUpdate("Aktualizacja w toku")

                    MediaActivity.currentlyPlayedMedia = 0
                    MediaActivity.instance?.finishAndRemoveTask()

                    if (updateFullConfiguration(settings.idSynch, dbHelper, context)) {
                        Logger.i(TAG, "UPDATE FULL CONFIGURATION DONE")
                        if (MainActivity.instance?.mediaUpdateRequired() == false) {
                            Logger.i(TAG, "MEDIA FILES UPDATE DONE")
                            if (dbHelper.updateSettingsTable(settings, idSynch)) {
                                Logger.i(TAG, "UPDATE NEW SETTINGS DONE, FINISHED")
                                MainActivity.instance?.infoUpdate("")
                            } else {
                                Logger.w(TAG, "UPDATE NEW SETTINGS  FAILED")
                                MainActivity.instance?.printInfoAndWait("Problem z aktualizacją ustawień", 5000)
                            }
                        } else {
                            Logger.w(TAG, "MEDIA UPDATE FAILED")
                        }

                    } else {
                        Logger.w(TAG, "UPDATE FULL CONFIGURATION FAILED")
                        MainActivity.instance?.printInfoAndWait("Problem z aktualizacją konfiguracji", 5000)
                    }

                    isUpdate = false
                } else {
                    Logger.i(TAG, "DB idSynch: $idSynch THE SAME as from latest settings: ${settings.idSynch}, NO UPDATE REQUIRED")

                    if (!MediaActivity.active) {
                        Logger.i(TAG, "Send Status Info to server")
                        val mediaStatusTask = SendMediaJson(
                            0,
                            0,
                            0,
                            0,
                            SendMediaJson.NO_STATUS,
                            MainActivity.getTime(),
                            MainActivity.MAC,
                            context)

                        mediaStatusTask.execute()
                    }
                }
            }
        } catch(e: Exception) {
            Logger.e(TAG, "updateSettings catch: $e")
            e.printStackTrace()
        }
    }
}
