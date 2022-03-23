package com.AdMedia.AdCon

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.AdMedia.AdCon.DataBase.HarmonogramEntry.TABLE_HARMONOGRAM
import com.google.gson.Gson

data class Harmonogram(
    val idHarmonogram: Int,
    val idModuls: Int,
    val idDay: Int,
    val timeStart: String,
    val timeStop: String
) {
    override fun toString(): String {
        return "Harmonogram (idHarmonogram=$idHarmonogram, idModuls=$idModuls, idDay=$idDay, timeStart='$timeStart', timeStop='$timeStop')"
    }

    class GetHarmonogramTask(
        private val domain: String,
        private val MAC: String,
        dbHelper: DatabaseDbHelper,
        val context: Context
    ) :
        AsyncTask<Unit, Unit, Boolean>() {
        private val TAG = this::class.qualifiedName.toString()
        var harmonogramArray = arrayOf<Harmonogram>()
        val dbW = dbHelper.writableDatabase

        @RequiresApi(Build.VERSION_CODES.O)
        override fun doInBackground(vararg params: Unit?): Boolean {
            try {
                val harmonogramJson = ServerAccess(context).downloadHarmonogramJson(domain, MAC)
                val gson = Gson()
                if (harmonogramJson.isNotEmpty()) {
                    harmonogramArray = gson.fromJson(harmonogramJson, Array<Harmonogram>::class.java)
                    Logger.d(TAG, "NEW harmonogramArray")
                } else {
                    Logger.w(TAG, "harmonogramJson EMPTY")
                    return false
                }
            } catch (e: Exception) {
                Logger.w(TAG, "doInBackground JSON catch: ${e.message}")
                e.printStackTrace()
                return false
            }

            dbW.execSQL("DELETE FROM $TABLE_HARMONOGRAM")
            for (harmonogram in harmonogramArray) {
                val values = ContentValues().apply {
                    put(
                        DataBase.HarmonogramEntry.TABLE_COLUMN_HARMONOGRAM_ID,
                        harmonogram.idHarmonogram
                    )
                    put(DataBase.HarmonogramEntry.TABLE_COLUMN_MODULS_ID, harmonogram.idModuls)
                    put(DataBase.HarmonogramEntry.TABLE_COLUMN_DAY_ID, harmonogram.idDay)
                    put(DataBase.HarmonogramEntry.TABLE_COLUMN_TIME_START, harmonogram.timeStart)
                    put(DataBase.HarmonogramEntry.TABLE_COLUMN_TIME_STOP, harmonogram.timeStop)
                }
                try {
                    dbW?.insertOrThrow(TABLE_HARMONOGRAM, null, values)
                } catch (e: SQLiteConstraintException) {
                    Logger.e(TAG, "DB Insert for TABLE_HARMONOGRAM catch: ${e.message}")
                    return false
                }
            }
            return true
        }
    }
}