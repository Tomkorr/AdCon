package com.AdMedia.AdCon

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.lang.Thread.sleep
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList

@RequiresApi(Build.VERSION_CODES.O)
class TimeChecker(val context: Context, val mediaManager: MediaManager) : TimerTask() {

    val warsawZone = ZoneId.of("Europe/Warsaw")
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dbHelper = DatabaseDbHelper(context)
    val dbR = dbHelper.readableDatabase
    val TAG = this::class.qualifiedName.toString()

    companion object {
        var prevCfgHash: Int = -1
    }

    override fun run() {
        checkHarmonogramTime()
    }

    fun checkHarmonogramTime() {
        Logger.d(TAG, "checkHarmonogramTime - start, getTime ret: " + MainActivity.getTime())

        var areMediaDownloaded = mediaManager.ifAllFilesOk() && !mediaManager.isDownloadInProgress() && !UpdateChecker.isUpdate

        if (!areMediaDownloaded) {
            Logger.w(TAG, "checkHarmonogramTime - update in progress, stop playing")
            MediaActivity.currentlyPlayedMedia = 0
            MediaActivity.instance?.finishAndRemoveTask()
            return
        }

        val currentDateTime = ZonedDateTime.now(warsawZone)
        val dow: DayOfWeek = currentDateTime.dayOfWeek
        var todayHarmonograms = getHarmonogramsByDay(dow.value)
        var currentTime = currentDateTime.format(formatter)

        val currentModuleId = getModuleIdForCurrentTime(todayHarmonograms, currentTime)

        if (currentModuleId >= 0) {
            Logger.i(TAG, "checkHarmonogramTime - found MATCHING harmonogram, using module id: $currentModuleId")
            mediaInformationsIntent(currentModuleId)
        } else {
            Logger.i(TAG, "checkHarmonogramTime - NOT MATCHING harmonogram, show clock ONLY")
            MainActivity.instance?.infoUpdate("")
            MediaActivity.currentlyPlayedMedia = 0
            MediaActivity.instance?.finishAndRemoveTask()
        }

        Logger.d(TAG, "checkHarmonogramTime - finished, getTime ret: " + MainActivity.getTime())
    }

    private fun stringTimeToSec(time: String?): Int {
        var sec = -1;

        if (time?.contains(':') == true) {
            val timeSplited = time.split(':')

            if (timeSplited.size >= 2) {
                val hour = timeSplited[0].toInt()
                val minutes = timeSplited[1].toInt()

                Logger.v(TAG, "Converting time: $time hour: $hour, minutes: $minutes")
                sec = (hour * 60 * 60) + (minutes * 60)
            } else {
                Logger.w(TAG, "Unable to split time: $time to hours and minutes")
            }

            Logger.v(TAG, "Converting time: $time to sec val, return: $sec sec")
        }
        else {
            Logger.w(TAG, "Unable to convert time: $time to sec val, ret 0")
        }
        return sec
    }

    private fun getModuleIdForCurrentTime(todayHarmonograms: List<Harmonogram>, currentTime: String?): Int {
        var moduleId = -1
        val currentSec = stringTimeToSec(currentTime)

        if (currentSec >= 0) {
            Logger.d(TAG, "Searching for matching harmonogram, currentSec: $currentSec")
            for(h in todayHarmonograms) {
                if (harmonogramWithinCurrentTime(h, currentSec)) {
                    moduleId = h.idModuls
                    Logger.d(TAG, "Found MATHING harmonogram id: ${h.idHarmonogram}, returning module id: $moduleId")
                    break
                }
            }
        } else {
            Logger.w(TAG, "Unable to provide current module ID, wrong current time")
        }

        return moduleId
    }

    private fun harmonogramWithinCurrentTime(h: Harmonogram, currentSec: Int): Boolean {
        var harmMatchingToCurrentTime = false
        val startTime = stringTimeToSec(h.timeStart)
        val stopTime = stringTimeToSec(h.timeStop)

        if (startTime <= currentSec && currentSec <= stopTime) {
            Logger.d(TAG, "Current timeSec: $currentSec WITHIN RANGE <$startTime, $stopTime> for harmonogram: ${h.idHarmonogram}")
            harmMatchingToCurrentTime = true
        } else {
            Logger.d(TAG, "Current timeSec: $currentSec OUT OF RANGE <$startTime, $stopTime> for harmonogram: ${h.idHarmonogram}")
        }
        return harmMatchingToCurrentTime
    }


    private fun mediaInformationsIntent(idModuls: Int) {
        Logger.d(TAG, "mediaInformationsIntent for moduleId: $idModuls")

        val (informationBar, colorTextInBar, colorBackgroundInBar) = dbHelper.getInfoBarSettings()
        val mediaIdAndTimes = getMediaIdAndTimeByModuleId(idModuls)

        val mediaIntent = Intent(context, MediaActivity::class.java)
        mediaIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        mediaIntent.putExtra("MediaIdsWithTime", mediaIdAndTimes)
        mediaIntent.putExtra("informationBar", informationBar)
        mediaIntent.putExtra("colorTextInBar", colorTextInBar)
        mediaIntent.putExtra("colorBackgroundInBar", colorBackgroundInBar)

        val stringToHash = idModuls.toString() + mediaIdAndTimes.toString() +
                            informationBar + colorTextInBar + colorBackgroundInBar
        val currentCfgHash = stringToHash.hashCode()
        Logger.d(TAG, "comparing config hash: current: $currentCfgHash, prev: $prevCfgHash")

        if (currentCfgHash != prevCfgHash && !MainActivity.deviceBanned) {
            Logger.i(TAG, "RESTART MEDIA ACTIVITY, apply new config")
            MediaActivity.instance?.finishAndRemoveTask()
            MainActivity.instance?.infoUpdate("")
            sleep(2000)
            prevCfgHash = currentCfgHash
            context.startActivity(mediaIntent)
        }
    }

    fun getHarmonogramsByDay(day: Int): List<Harmonogram> {
        Logger.d(TAG, "getHarmonogramsByDay start, for day: $day")
        val harmonograms = arrayListOf<Harmonogram>()
        val selection = "${DataBase.HarmonogramEntry.TABLE_COLUMN_DAY_ID} = ?"
        val selectionArgs = arrayOf(day.toString())

        val cursor = dbR.query(
            DataBase.HarmonogramEntry.TABLE_HARMONOGRAM,
            null,
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            with(cursor) {
               do {
                    val h = Harmonogram(
                        cursor.getInt(cursor.getColumnIndexOrThrow("HARMONOGRAM_ID")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("MODULS_ID")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("DAY_ID")),
                        cursor.getString(cursor.getColumnIndexOrThrow("TIME_START")),
                        cursor.getString(cursor.getColumnIndexOrThrow("TIME_STOP"))
                    )
                    Logger.v(TAG, "getHarmonogramsByDay adding: $h")
                    harmonograms.add(h)
                } while (moveToNext())
            }
        }
        else {
            Logger.w(TAG, "getHarmonogramsByDay, cursor EMPTY")
        }
        cursor.close()
        Logger.d(TAG, "getHarmonogramsByDay finish, harmonogram list size: ${harmonograms.size} for day: $day")

        return harmonograms
    }

    fun getMediaIdAndTimeByModuleId(moduleId: Int): ArrayList<Pair<Int, Long>> {
        val selection = "${DataBase.ModuleEntry.TABLE_COLUMN_MODULE_ID} = ?"
        val selectionArgs = arrayOf(moduleId.toString())
        var mediaIdAndTime = arrayListOf<Pair<Int, Long>>()

        if (MediaActivity.fixedUrl.isNotEmpty()) {
            mediaIdAndTime.add(Pair(Int.MAX_VALUE, MediaActivity.refreshIntervalFixedURL))
            return mediaIdAndTime
        }

        val cursor = dbR.query(
            DataBase.ModuleEntry.TABLE_MODULE,
            null,
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            do {
                if (cursor.getInt(cursor.getColumnIndexOrThrow("MODULE_ID")) == moduleId) {
                    mediaIdAndTime.add(Pair(cursor.getInt(cursor.getColumnIndexOrThrow("MEDIA_ID")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("TIME"))))
                }
            } while (cursor.moveToNext())
            Logger.d(TAG, "Found media ids and time $mediaIdAndTime for module $moduleId")
        } else {
            Logger.w(TAG, "No media ids for module $moduleId")
        }
        cursor.close()
        return mediaIdAndTime
    }
}