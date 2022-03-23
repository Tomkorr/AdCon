package com.AdMedia.AdCon

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import java.lang.Exception

data class Module(val idModule: Int, val name: String, val list: Array<List>) {
    override fun toString(): String {
        return "Module(idModule=$idModule, name='$name', list=$list)"
    }

    data class List(val idMedia: Int, val time: Int) {
        override fun toString(): String {
            return "list(idMedia=$idMedia, time=$time)"
        }
    }

    class GetModuleTask(
        private val domain: String,
        private val MAC: String,
        val dbHelper: DatabaseDbHelper,
        val context: Context
    ) : AsyncTask<Unit, Unit, Boolean>() {
        private val TAG = this::class.qualifiedName.toString()
        var moduleArray = arrayOf<Module>()
        val dbW = dbHelper.writableDatabase

        @RequiresApi(Build.VERSION_CODES.O)
        override fun doInBackground(vararg params: Unit?): Boolean {
            try {
                val moduleJson = ServerAccess(context).downloadModuleJson(domain, MAC)
                val gson = Gson()
                if (moduleJson.isNotEmpty()) {
                    moduleArray = gson.fromJson(moduleJson, Array<Module>::class.java)
                    Logger.d(TAG, "NEW moduleArray")
                } else {
                    Logger.w(TAG, "moduleJson EMPTY")
                    return false
                }
            } catch (e: Exception) {
                Logger.w(TAG, "doInBackground JSON catch: ${e.message}")
                e.printStackTrace()
                return false
            }

            dbW.execSQL("DELETE FROM ${DataBase.ModuleEntry.TABLE_MODULE}")
            for (module in moduleArray) {
                val values = ContentValues()
                for (item in module.list) {
                    values.put(DataBase.ModuleEntry.TABLE_COLUMN_MODULE_ID, module.idModule)
                    values.put(DataBase.ModuleEntry.TABLE_COLUMN_NAME, module.name)
                    values.put(DataBase.ModuleEntry.TABLE_COLUMN_MEDIA_ID, item.idMedia)
                    values.put(DataBase.ModuleEntry.TABLE_COLUMN_TIME, item.time)
                    try {
                        dbW?.insertOrThrow(DataBase.ModuleEntry.TABLE_MODULE, null, values)
                    } catch (e: SQLiteConstraintException) {
                        Logger.e(TAG, "DB Insert for TABLE_MODULE catch: ${e.message}")
                        e.printStackTrace()
                        return false
                    }
                }
            }
            return true
        }
    }
}
