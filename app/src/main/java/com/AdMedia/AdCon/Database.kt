package com.AdMedia.AdCon

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import android.util.Log
import com.AdMedia.AdCon.DataBase.SQL_CREATE_ENTRIES_HARMONOGRAM
import com.AdMedia.AdCon.DataBase.SQL_CREATE_ENTRIES_MEDIA
import com.AdMedia.AdCon.DataBase.SQL_CREATE_ENTRIES_MODULE
import com.AdMedia.AdCon.DataBase.SQL_CREATE_ENTRIES_SETTINGS
//import com.example.AdCon.DataBase.SQL_CREATE_ENTRIES_TIME
import com.AdMedia.AdCon.DataBase.SQL_DELETE_ENTRIES_MODULE
import com.AdMedia.AdCon.DataBase.SQL_DELETE_ENTRIES_SETTINGS

object DataBase {

    object MediaEntry : BaseColumns {
        const val TABLE_MEDIA = "MEDIA"
        const val TABLE_COLUMN_MEDIA_ID = "MEDIA_ID"
        const val TABLE_COLUMN_NAME = "NAME"
        const val TABLE_COLUMN_PATH = "PATH"
        const val TABLE_COLUMN_TYP = "TYP"
        const val TABLE_COLUMN_SIZE = "SIZE"
        const val TABLE_COLUMN_CHECKSUM = "CHECKSUM"
        const val TABLE_COLUMN_CHECKSUM_EXPECTED = "CHECKSUM_EXPECTED"
    }

    object HarmonogramEntry : BaseColumns {
        const val TABLE_HARMONOGRAM = "HARMONOGRAM"
        const val TABLE_COLUMN_HARMONOGRAM_ID = "HARMONOGRAM_ID"
        const val TABLE_COLUMN_MODULS_ID = "MODULS_ID"
        const val TABLE_COLUMN_DAY_ID = "DAY_ID"
        const val TABLE_COLUMN_TIME_START = "TIME_START"
        const val TABLE_COLUMN_TIME_STOP = "TIME_STOP"
    }

    object ModuleEntry : BaseColumns {
        const val TABLE_MODULE = "MODULE"
        const val TABLE_COLUMN_MODULE_ID = "MODULE_ID"
        const val TABLE_COLUMN_NAME = "NAME"
        const val TABLE_COLUMN_MEDIA_ID = "MEDIA_ID"
        const val TABLE_COLUMN_TIME = "TIME"
    }

    object SettingsEntry : BaseColumns {
        const val TABLE_SETTINGS = "SETTINGS"
        const val TABLE_COLUMN_INFORMATION_BAR = "INFORMATION_BAR"
        const val TABLE_COLUMN_ID_SYNCH = "ID_SYNCH"
        const val TABLE_COLUMN_SERVER_TIME = "SERVER_TIME"
        const val TABLE_COLUMN_BAR_BACKGROUND_COLOR = "BAR_BACKGROUND_COLOR"
        const val TABLE_COLUMN_BAR_FONT_COLOR = "BAR_FONT_COLOR"
    }

    const val SQL_CREATE_ENTRIES_MEDIA =
        "CREATE TABLE ${MediaEntry.TABLE_MEDIA} (" +
                "${BaseColumns._ID} INTEGER PRIMARY KEY," +
                "${MediaEntry.TABLE_COLUMN_MEDIA_ID} INTEGER," +
                "${MediaEntry.TABLE_COLUMN_NAME} TEXT," +
                "${MediaEntry.TABLE_COLUMN_PATH} TEXT," +
                "${MediaEntry.TABLE_COLUMN_CHECKSUM} TEXT," +
                "${MediaEntry.TABLE_COLUMN_CHECKSUM_EXPECTED} TEXT," +
                "${MediaEntry.TABLE_COLUMN_TYP} INTEGER," +
                "${MediaEntry.TABLE_COLUMN_SIZE} INTEGER," +
                "UNIQUE(${MediaEntry.TABLE_COLUMN_MEDIA_ID},${MediaEntry.TABLE_COLUMN_NAME},${MediaEntry.TABLE_COLUMN_PATH},${MediaEntry.TABLE_COLUMN_TYP},${MediaEntry.TABLE_COLUMN_SIZE}," +
                "${MediaEntry.TABLE_COLUMN_CHECKSUM}, ${MediaEntry.TABLE_COLUMN_CHECKSUM_EXPECTED}))"

    const val SQL_CREATE_ENTRIES_HARMONOGRAM =
        "CREATE TABLE ${HarmonogramEntry.TABLE_HARMONOGRAM} (" +
                "${BaseColumns._ID} INTEGER PRIMARY KEY," +
                "${HarmonogramEntry.TABLE_COLUMN_HARMONOGRAM_ID} INTEGER," +
                "${HarmonogramEntry.TABLE_COLUMN_MODULS_ID} INTEGER," +
                "${HarmonogramEntry.TABLE_COLUMN_DAY_ID} INTEGER," +
                "${HarmonogramEntry.TABLE_COLUMN_TIME_START} TEXT," +
                "${HarmonogramEntry.TABLE_COLUMN_TIME_STOP} TEXT," +
                "UNIQUE(${HarmonogramEntry.TABLE_COLUMN_HARMONOGRAM_ID},${HarmonogramEntry.TABLE_COLUMN_MODULS_ID},${HarmonogramEntry.TABLE_COLUMN_DAY_ID},${HarmonogramEntry.TABLE_COLUMN_TIME_START}," +
                "${HarmonogramEntry.TABLE_COLUMN_TIME_STOP}))"

    const val SQL_CREATE_ENTRIES_MODULE =
        "CREATE TABLE ${ModuleEntry.TABLE_MODULE} (" +
                "${ModuleEntry.TABLE_COLUMN_MODULE_ID} INTEGER," +
                "${ModuleEntry.TABLE_COLUMN_NAME} TEXT," +
                "${ModuleEntry.TABLE_COLUMN_MEDIA_ID} INTEGER," +
                "${ModuleEntry.TABLE_COLUMN_TIME} INTEGER)"

    const val SQL_CREATE_ENTRIES_SETTINGS =
        "CREATE TABLE ${SettingsEntry.TABLE_SETTINGS} (" +
                "${SettingsEntry.TABLE_COLUMN_INFORMATION_BAR} TEXT," +
                "${SettingsEntry.TABLE_COLUMN_ID_SYNCH} INTEGER," +
                "${SettingsEntry.TABLE_COLUMN_SERVER_TIME} LONG," +
                "${SettingsEntry.TABLE_COLUMN_BAR_BACKGROUND_COLOR} TEXT," +
                "${SettingsEntry.TABLE_COLUMN_BAR_FONT_COLOR} TEXT," +
                "UNIQUE(${SettingsEntry.TABLE_COLUMN_ID_SYNCH},${SettingsEntry.TABLE_COLUMN_INFORMATION_BAR},${SettingsEntry.TABLE_COLUMN_SERVER_TIME},${SettingsEntry.TABLE_COLUMN_BAR_BACKGROUND_COLOR}," +
                "${SettingsEntry.TABLE_COLUMN_BAR_FONT_COLOR}))"

    const val SQL_DELETE_ENTRIES_SETTINGS = "DROP TABLE IF EXISTS ${SettingsEntry.TABLE_SETTINGS}"
    const val SQL_DELETE_ENTRIES_MEDIA = "DROP TABLE IF EXISTS ${MediaEntry.TABLE_MEDIA}"
    const val SQL_DELETE_ENTRIES_HARMONOGRAM =
        "DROP TABLE IF EXISTS ${HarmonogramEntry.TABLE_HARMONOGRAM}"
    const val SQL_DELETE_ENTRIES_MODULE = "DROP TABLE IF EXISTS ${ModuleEntry.TABLE_MODULE}"
}

class DatabaseDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "AdConDB"
    }
    private val TAG = this::class.qualifiedName.toString()

    override fun onCreate(db: SQLiteDatabase) {
        Logger.i(TAG, "onCreate")
        db.execSQL(SQL_CREATE_ENTRIES_MEDIA)
        db.execSQL(SQL_CREATE_ENTRIES_HARMONOGRAM)
        db.execSQL(SQL_CREATE_ENTRIES_MODULE)
        db.execSQL(SQL_CREATE_ENTRIES_SETTINGS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Logger.i(TAG, "onUpgrade")
        db.execSQL(SQL_DELETE_ENTRIES_MODULE)
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Logger.i(TAG, "onDowngrade")
        onUpgrade(db, oldVersion, newVersion)
    }

    fun getInfoBarSettings(): Triple<String, String, String> {
        var informationBar: String = ""
        var colorTextInBar: String = ""
        var colorBackgroundInBar: String = ""
        val dbR = this.readableDatabase
        val cursor = dbR.query(
            DataBase.SettingsEntry.TABLE_SETTINGS, null, null, null, null, null, null
        )

        if(cursor.moveToLast()) {
            informationBar = cursor.getString(cursor.getColumnIndexOrThrow("INFORMATION_BAR"))
            colorTextInBar = cursor.getString(cursor.getColumnIndexOrThrow("BAR_FONT_COLOR"))
            colorBackgroundInBar = cursor.getString(cursor.getColumnIndexOrThrow("BAR_BACKGROUND_COLOR"))
        }
        cursor.close()
        val tripleCfg = Triple(informationBar, colorTextInBar, colorBackgroundInBar)

        Logger.d(TAG, "getInfoBarSettings, tripleCfg: $tripleCfg")

        return tripleCfg
    }

    fun getCurrentIDSynch(): Int {
        var idSynch: Int = -1
        val dbR = this.readableDatabase

        try {
            val cursorSettings = dbR.query(
                DataBase.SettingsEntry.TABLE_SETTINGS, null, null, null, null, null, null
            )
            cursorSettings?.moveToLast()
            Logger.v(TAG, "DB ${DataBase.SettingsEntry.TABLE_SETTINGS} cursor count: ${cursorSettings.count}")

            if (cursorSettings.count > 0) {
                idSynch = cursorSettings.getInt(cursorSettings.getColumnIndexOrThrow("ID_SYNCH"))
                Logger.d(TAG, "Current ID Synch in DB: $idSynch")
            } else {
                Logger.w(TAG, "No ${DataBase.SettingsEntry.TABLE_SETTINGS} table available")
            }
            cursorSettings.close()
        } catch (e: SQLiteException) {
            Logger.e(TAG, "updateSettings catch: ${e.message}")
        }
        return idSynch
    }

    fun updateSettingsTable(settings: Settings, oldIdSynch: Int): Boolean {
        val values = ContentValues()
        val dbW = this.writableDatabase
        try {
            if (-1 == oldIdSynch) {
                dbW.execSQL(SQL_DELETE_ENTRIES_SETTINGS)
                Logger.d(TAG, "oldIdSynch: $oldIdSynch, create NEW settings entries in DB")
                dbW.execSQL(SQL_CREATE_ENTRIES_SETTINGS)
            }
            dbW.execSQL("DELETE FROM ${DataBase.SettingsEntry.TABLE_SETTINGS}")
            // TODO: create only one entry
            values.put(DataBase.SettingsEntry.TABLE_COLUMN_ID_SYNCH, settings.idSynch)
            values.put(DataBase.SettingsEntry.TABLE_COLUMN_SERVER_TIME, settings.serverTime)
            values.put(DataBase.SettingsEntry.TABLE_COLUMN_INFORMATION_BAR, settings.informationBar)
            values.put(DataBase.SettingsEntry.TABLE_COLUMN_BAR_BACKGROUND_COLOR, settings.infoBarBackgroundColor)
            values.put(DataBase.SettingsEntry.TABLE_COLUMN_BAR_FONT_COLOR, settings.infoBarFontColor)
            dbW?.insertOrThrow(DataBase.SettingsEntry.TABLE_SETTINGS, null, values)
            Logger.d(TAG, "ID Synch ${settings.idSynch} stored in DB")
            return true
        } catch (e: SQLiteException) {
            Logger.e(TAG, "updateSettingsTable catch: ${e.message}")
            return false
        }
    }
}