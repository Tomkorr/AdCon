package com.AdMedia.AdCon

import SendMediaJson
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.os.AsyncTask
import android.os.Build
import androidx.annotation.RequiresApi
import com.AdMedia.AdCon.DataBase.MediaEntry.TABLE_COLUMN_CHECKSUM_EXPECTED
import com.AdMedia.AdCon.DataBase.MediaEntry.TABLE_COLUMN_CHECKSUM
import com.AdMedia.AdCon.DataBase.MediaEntry.TABLE_COLUMN_MEDIA_ID
import com.AdMedia.AdCon.DataBase.MediaEntry.TABLE_COLUMN_NAME
import com.AdMedia.AdCon.DataBase.MediaEntry.TABLE_COLUMN_PATH
import com.AdMedia.AdCon.DataBase.MediaEntry.TABLE_COLUMN_SIZE
import com.AdMedia.AdCon.DataBase.MediaEntry.TABLE_COLUMN_TYP
import com.AdMedia.AdCon.DataBase.MediaEntry.TABLE_MEDIA
import com.google.gson.Gson
import java.util.concurrent.TimeUnit


data class Media(
    val idMedia: Int,
    val name: String,
    val typ: Int,
    val path: String,
    val checkSum: String,
    val checkSumExcepted: String,
    val size: Long,
    val context: Context?
) {
    enum class MEDIA_TYPE {
        UNKNOWN,
        IMAGE,
        VIDEO,
        URL
    }

    data class MediaDbItem(
        val name: String,
        val mediaID: Int,
        val urlAddress: String,
        val checksum: String,
        val type: MEDIA_TYPE,
        val size: Long) {

        override fun toString(): String {
            return "MediaDbItem File: $type, ID: $mediaID, name: $name, path: $urlAddress, md5: $checksum"
        }
    }

    companion object {
        private val TAG = this::class.qualifiedName.toString()
        var mediaCount = -1
        var currentlyMediaDownloading = 0

        @RequiresApi(Build.VERSION_CODES.O)
        var mediaToDownload = 0

        fun getPathByMediaName(mediaName: String, context: Context): String {
            val dbHelper = DatabaseDbHelper(context)
            val dbR = dbHelper.readableDatabase
            val selection = "$TABLE_COLUMN_NAME = ?"
            val selectionArgs = arrayOf(mediaName)
            var mediaPath = ""
            val cursor = dbR.query(
                TABLE_MEDIA,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
            )

            if (cursor.moveToFirst()) {
                mediaPath = cursor.getString(cursor.getColumnIndexOrThrow("PATH"))
                Logger.d(TAG, "getPathByMediaName - for mediaId: $mediaName using path: $mediaPath")
            } else {
                Logger.w(TAG, "getPathByMediaName - for mediaId: $mediaName NO PATH")
            }
            cursor.close()

            return mediaPath
        }

        fun getCurrentMediaList(context: Context): ArrayList<MediaDbItem> {
            val mediaList = arrayListOf<MediaDbItem>()
            val dbHelper = DatabaseDbHelper(context)
            val dbR = dbHelper.readableDatabase

            val cursor = dbR.query(
                TABLE_MEDIA,
                null,
                null,
                null,
                null,
                null,
                null
            )

            Logger.i(TAG, "start creating mediaList, cursor.count: ${cursor.count}")
            if (cursor.moveToFirst()) {
                cursor.use { cur ->
                    do {
                        Logger.v(
                            TAG,
                            cur.getString(cur.getColumnIndexOrThrow(TABLE_COLUMN_NAME))
                        )
                        val type = cur.getInt(cur.getColumnIndexOrThrow(TABLE_COLUMN_TYP))
                        val mediaType = when (type) {
                            1 -> MEDIA_TYPE.IMAGE
                            2 -> MEDIA_TYPE.VIDEO
                            3 -> MEDIA_TYPE.URL
                            else -> MEDIA_TYPE.UNKNOWN
                        }

                        if (mediaType != MEDIA_TYPE.UNKNOWN) {
                            val dbItem = MediaDbItem(
                                cur.getString(cur.getColumnIndexOrThrow(TABLE_COLUMN_NAME)),
                                cur.getInt(cur.getColumnIndexOrThrow(TABLE_COLUMN_MEDIA_ID)),
                                cur.getString(cur.getColumnIndexOrThrow(TABLE_COLUMN_PATH)),
                                cur.getString(
                                    cur.getColumnIndexOrThrow(
                                        TABLE_COLUMN_CHECKSUM_EXPECTED
                                    )
                                ),
                                mediaType,
                                cur.getLong(cur.getColumnIndexOrThrow(TABLE_COLUMN_SIZE))
                            )
                            mediaList.add(dbItem)
                            Logger.d(TAG, "getCurrentMediaList, added $dbItem")
                        } else {
                            Logger.w(TAG, "getCurrentMediaList MEDIA_TYPE - UNKNOWN")
                        }
                    } while (cur.moveToNext())
                }
            } else {
                Logger.w(TAG, "mediaList DB EMPTY, unable to iterate over cursor")
            }
            cursor.close()

            return mediaList
        }

        fun udpateDownloadStatus(percent: Int, context: Context) {
            if (percent == 100) {
                val mediaStatusTask = SendMediaJson(
                    MediaActivity.currentlyPlayedMedia,
                    0,
                    percent,
                    UpdateChecker.lastUpdateId,
                    SendMediaJson.UPDATE_FINISHED,
                    MainActivity.getTime(),
                    MainActivity.MAC,
                    context)

                mediaStatusTask.execute()
            } else {
                val mediaStatusTask = SendMediaJson(
                    MediaActivity.currentlyPlayedMedia,
                    0,
                    percent,
                    UpdateChecker.lastUpdateId,
                    SendMediaJson.UPDATE_IN_PROGRESS,
                    MainActivity.getTime(),
                    MainActivity.MAC,
                    context)

                mediaStatusTask.execute()
            }
        }
    }

    override fun toString(): String {
        return "Media(idMedia=$idMedia, name='$name', typ=$typ, path='$path', size=$size)"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    class GetMediaConfigTask(
        private val updateId: Int,
        private val domain: String,
        private val MAC: String,
        private val dbHelper: DatabaseDbHelper,
        private val context: Context
    ) :
        AsyncTask<String, String, Boolean>() {
        private val TAG = this::class.qualifiedName.toString()

        var deviceMAC = MainActivity.MAC
        val dbW = dbHelper.writableDatabase

        override fun onPreExecute() {
            super.onPreExecute()
            Logger.v(TAG, "onPreExecute started, sending UPDATE_START status")

            val mediaStatusTask = SendMediaJson(
                MediaActivity.currentlyPlayedMedia,
                0,
                0,
                updateId,
                SendMediaJson.UPDATE_START,
                MainActivity.getTime(),
                deviceMAC,
                context)

            mediaStatusTask.execute()

            if (mediaStatusTask.get(20, TimeUnit.SECONDS)) {
                Logger.d(TAG, "onPreExecute UPDATE_START status send successfully")
            } else {
                Logger.w(TAG, "onPreExecute UPDATE_START status send PROBLEM")
            }
        }

        private fun insertMediaToDb(media: Media, fileName: String): Boolean {
            Logger.v(TAG, "Adding new item to Media DB: mediaId: ${media.idMedia}, fileName: $fileName")
            val values =
                ContentValues().apply {
                    put(TABLE_COLUMN_MEDIA_ID, media.idMedia)
                    put(TABLE_COLUMN_NAME, fileName)
                    put(TABLE_COLUMN_PATH, media.path)
                    put(TABLE_COLUMN_TYP, media.typ)
                    put(TABLE_COLUMN_SIZE, media.size)
                    put(TABLE_COLUMN_CHECKSUM, "0")
                    put(TABLE_COLUMN_CHECKSUM_EXPECTED, media.checkSum)
                }
            try {
                dbW?.insertOrThrow(TABLE_MEDIA, null, values)
            } catch (e: SQLiteConstraintException) {
                Logger.e(TAG, "DB Insert for TABLE_MEDIA catch: ${e.message}")
                e.printStackTrace()
                return false
            }
            return true
        }

        override fun doInBackground(vararg p0: String?): Boolean {
            var mediaArray: Array<Media>

            if (MediaActivity.fixedUrl.isNotEmpty()) {
                dbW.execSQL("DELETE FROM $TABLE_MEDIA")
                val media = Media(Int.MAX_VALUE, MediaActivity.fixedUrl, MEDIA_TYPE.URL.ordinal, "", "", "", 0, context)
                insertMediaToDb(media, MediaActivity.fixedUrl)
                return true
            }

            try {
                val mediaJson = ServerAccess(context).downloadMediaJson(domain, MAC)
                val gson = Gson()
                if (mediaJson.isNotEmpty()) {
                    mediaArray = gson.fromJson(mediaJson, Array<Media>::class.java)
                    Logger.d(TAG, "NEW mediaArray")
                } else {
                    Logger.w(TAG, "mediaJson EMPTY")
                    return false
                }
            } catch (e: java.lang.Exception) {
                Logger.w(TAG, "doInBackground JSON catch: ${e.message}")
                e.printStackTrace()
                return false
            }

            dbW.execSQL("DELETE FROM $TABLE_MEDIA")
            for (media in mediaArray) {
                val correctedName = media.name.replace(("\\s+").toRegex(), "_").replace(("[.,]+").toRegex(), ".")
                insertMediaToDb(media, correctedName)
            }
            return true
        }

        override fun onPostExecute(result: Boolean) {
            super.onPostExecute(result)
            Logger.d(TAG, "onPostExecute, result: $result")
        }
    }
}
