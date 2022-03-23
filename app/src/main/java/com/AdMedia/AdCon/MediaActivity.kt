package com.AdMedia.AdCon

import SendMediaJson
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.webkit.WebSettings
import android.widget.MediaController
import android.widget.TextView
import androidx.annotation.RequiresApi
import kotlinx.android.synthetic.main.activity_media.*
import java.io.File
import java.lang.Thread.sleep
import java.util.*
import kotlin.collections.ArrayList

@RequiresApi(Build.VERSION_CODES.O)
class MediaActivity() : Activity() {
    data class PlayedMediaItem(val pair: Pair<Int, Long>) {
        val mediaId = pair.first
        var playingTime = pair.second
        var fileName: String = ""
        var mediaType: Media.MEDIA_TYPE = Media.MEDIA_TYPE.UNKNOWN
        var mediaStatus: MediaManager.ITEM_STATUS = MediaManager.ITEM_STATUS.UNKNOWN
        var checksumStatus: MediaManager.CHEKSUM_STATUS = MediaManager.CHEKSUM_STATUS.UNKNOWN

        override fun toString(): String {
            return "PlayedMediaItem mediaId: $mediaId, playingTime: $playingTime, $fileName, $mediaType, $mediaStatus, $checksumStatus"
        }
    }

    companion object {
        var instance: MediaActivity? = null
        val fixedUrl: String = ""  // "http://fornal.webd.pro/"
        val refreshIntervalFixedURL: Long = 60 * 60 // Reload page every 1 hour
        var currentlyPlayedMedia: Int = 0
        var active = false
    }
    private lateinit var mediaRunner: Runnable
    private lateinit var cdTimer: CountDownTimer
    private lateinit var myBitmap: Bitmap
    private lateinit var marqueeTextBar: TextView
    private val TAG: String = this::class.qualifiedName.toString()
    private val storedMedia = MainActivity.instance?.mediaManager?.getStoredMediaMap()
    private val imagesPath = MainActivity.instance?.mediaManager?.imagesPath
    private val videosPath = MainActivity.instance?.mediaManager?.videosPath
    private val playList = arrayListOf<PlayedMediaItem>()
    private var playedMediaIndex = 0
    private val handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media)
        instance = this
        active = true

        Logger.i(TAG, "ON CREATE")

        Thread.setDefaultUncaughtExceptionHandler(
            MyExceptionHandler(
                this@MediaActivity,
                applicationContext))

        window.decorView.apply {
            systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN}

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels

        requestedOrientation = if (height > width) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        val intent = intent

        (intent.getSerializableExtra("MediaIdsWithTime") as? ArrayList<Pair<Int, Long>>)?.forEach() {
            playList.add(PlayedMediaItem(it))
        }
        updatePlayListWithAdditionalInformation(applicationContext)

        marqueeTextBar = findViewById(R.id.informationBar)
        var informationBar = intent.getStringExtra("informationBar")
        var colorTextInBar = intent.getStringExtra("colorTextInBar")
        var colorBackgroundInBar = intent.getStringExtra("colorBackgroundInBar")

        if (informationBar.isEmpty())
            marqueeTextBar.visibility = View.GONE
        else {
            informationBar += "        "
            marqueeTextBar.visibility = View.VISIBLE
            marqueeTextBar.ellipsize = TextUtils.TruncateAt.MARQUEE;
            marqueeTextBar.text = informationBar.repeat(15)
            marqueeTextBar.marqueeRepeatLimit = -1
            marqueeTextBar.isSelected = true
            marqueeTextBar.setTextColor(Color.parseColor(colorTextInBar))
            marqueeTextBar.setBackgroundColor(Color.parseColor(colorBackgroundInBar))
        }

        playedMediaIndex = playList.size
        mediaRunner = object : Runnable {
            override fun run() {
                var counter = playList.size + 1
                do {
                    playedMediaIndex++
                    if (playedMediaIndex >= playList.size) {
                        playedMediaIndex = 0
                    }
                    counter--
                } while ((counter > 0) && checkIfMediaNotOk(playList[playedMediaIndex]))

                Logger.i(TAG, "mediaRunner run, playedMediaIndex: $playedMediaIndex")

                if (counter == 0) {
                    currentlyPlayedMedia = 0
                    instance?.finishAndRemoveTask()
                }

                currentlyPlayedMedia = playList[playedMediaIndex].mediaId
                if (playList[playedMediaIndex].mediaType == Media.MEDIA_TYPE.IMAGE) {
                    imageView.visibility = View.VISIBLE
                    videoView.visibility = View.GONE
                    webView.visibility = View.GONE
                    try {
                        myBitmap =
                            BitmapFactory.decodeFile(imagesPath + playList[playedMediaIndex].fileName)
                        imageView.setImageBitmap(myBitmap)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else if (playList[playedMediaIndex].mediaType == Media.MEDIA_TYPE.VIDEO) {
                    videoView.visibility = View.VISIBLE
                    imageView.visibility = View.GONE
                    webView.visibility = View.GONE

                    try {
                        val video = Uri.fromFile(
                            File(videosPath + playList[playedMediaIndex].fileName)
                        )
                        videoView.setVideoURI(video)
                        val mediaController = MediaController(this@MediaActivity)
                        mediaController.setAnchorView(videoView)
                        mediaController.visibility = View.GONE
                        videoView.setMediaController(mediaController)
                        videoView.requestFocus()
                        videoView.start()
                        videoView.setOnErrorListener(object : MediaPlayer.OnErrorListener {
                            override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
                                videoView.resume()
                                return true
                            }
                        })
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(videosPath + playList[playedMediaIndex].fileName)
                        val duration: String? =
                            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
                        retriever.release()
                        if (duration != null) {
                            playList[playedMediaIndex].playingTime = duration.toLong() / 1000
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else if (playList[playedMediaIndex].mediaType == Media.MEDIA_TYPE.URL) {
                    try {
                        webView.visibility = View.VISIBLE
                        videoView.visibility = View.GONE
                        imageView.visibility = View.GONE
                        webView.webViewClient = MyWebViewClient()
                        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
                        webView.settings.setJavaScriptEnabled(true)
                        webView.settings.domStorageEnabled
                        webView.settings.javaScriptCanOpenWindowsAutomatically = true
                        webView.settings.mediaPlaybackRequiresUserGesture = false;
                        webView.loadUrl(playList[playedMediaIndex].fileName)

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                try {
                    var t = 1L
                    if (playList.isNotEmpty()) {
                        t = playList[playedMediaIndex].playingTime
                    }

                    cdTimer = object : CountDownTimer(t * 1000, 30000) {
                        override fun onTick(millisUntilFinished: Long) {
                            // ("Timer: ${UpdateChecker.isUpdate}")
                            if (!UpdateChecker.isUpdate && NetworkChecker(this@MediaActivity).isNetworkAvailable(
                                    this@MediaActivity
                                ) && active
                            ) {
                                val mediaStatusTask = SendMediaJson(
                                    currentlyPlayedMedia,
                                    0,
                                    0,
                                    0,
                                    SendMediaJson.NO_STATUS,
                                    MainActivity.getTime(),
                                    MainActivity.MAC,
                                    applicationContext)

                                mediaStatusTask.execute()
                            }
                        }

                        override fun onFinish() {
                            Logger.d(TAG, "onFinish")
                        }
                    }
                    cdTimer.start()

                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (playList.isNotEmpty()) {
                    handler.postDelayed(this, playList[playedMediaIndex].playingTime * 1000)
                }
            }
        }

        handler.post(mediaRunner)
    }

    private fun checkIfMediaNotOk(item: PlayedMediaItem): Boolean {
        var mediaNotOk = true

        Logger.d(TAG, "Checking if media id ${item.mediaId} of type ${item.mediaType} is OK? ")

        if (item.mediaType == Media.MEDIA_TYPE.IMAGE || item.mediaType == Media.MEDIA_TYPE.VIDEO) {
            if (item.fileName.isNotEmpty() && item.checksumStatus == MediaManager.CHEKSUM_STATUS.MATCHING) {
                mediaNotOk = false
            }
        } else if (item.mediaType == Media.MEDIA_TYPE.URL) {
            if (item.fileName.isNotEmpty()) {
                mediaNotOk = false
            }
        }
        Logger.d(TAG, "Media id ${item.mediaId} of type ${item.mediaType} is ${if (mediaNotOk) "NOT OK" else "OK"} ")
        sleep(100)

        return mediaNotOk
    }

    private fun updatePlayListWithAdditionalInformation(context: Context) {
        Logger.v(TAG, "playList before update $playList")
        val getStoredItemIndex = fun(mediaId: Int): String {
            var key = ""
            if (storedMedia != null) {
                for (item in storedMedia) {
                    if (item.value.mediaID == mediaId) {
                        key = item.key
                        break
                    }
                }
            }
            return key
        }

        playList.forEach() {
            val storedMediaKey = getStoredItemIndex(it.mediaId)
            if (storedMediaKey.isNotEmpty() && storedMedia != null) {
                it.mediaStatus = storedMedia[storedMediaKey]?.itemStatus!!
                it.checksumStatus = storedMedia[storedMediaKey]?.checksumStatus!!
            }

            it.fileName = getMediaNameById(it.mediaId, context)
            it.mediaType = getMediaTypeById(it.mediaId, context)
        }
        Logger.v(TAG, "playList after update $playList")
    }

    private fun getMediaTypeById(mediaId: Int, context: Context): Media.MEDIA_TYPE {
        var mediaType = Media.MEDIA_TYPE.UNKNOWN
        val dbHelper = DatabaseDbHelper(context)
        val dbR = dbHelper.readableDatabase
        val selection = "${DataBase.MediaEntry.TABLE_COLUMN_MEDIA_ID} = ?"
        val selectionArgs = arrayOf(mediaId.toString())

        if (Int.MAX_VALUE == mediaId && fixedUrl.isNotEmpty()) {
            return Media.MEDIA_TYPE.URL
        }

        val cursor = dbR.query(
            DataBase.MediaEntry.TABLE_MEDIA,
            null,
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            val intType = cursor.getInt(cursor.getColumnIndexOrThrow(DataBase.MediaEntry.TABLE_COLUMN_TYP))
            mediaType = when (intType) {
                1 -> Media.MEDIA_TYPE.IMAGE
                2 -> Media.MEDIA_TYPE.VIDEO
                3 -> Media.MEDIA_TYPE.URL
                else -> Media.MEDIA_TYPE.UNKNOWN
            }
            Logger.d(TAG, "getMediaTypeById - for mediaId: $mediaId using type: $mediaType")
        } else {
            Logger.w(TAG, "getMediaTypeById - for mediaId: $mediaId NO TYPE")
        }
        cursor.close()

        return mediaType
    }

    private fun getMediaNameById(mediaId: Int, context: Context): String {
        Logger.d(TAG, "getMediaNameById for mediaId: $mediaId")
        val dbHelper = DatabaseDbHelper(context)
        val dbR = dbHelper.readableDatabase
        val selection = "${DataBase.MediaEntry.TABLE_COLUMN_MEDIA_ID} = ?"
        val selectionArgs = arrayOf(mediaId.toString())
        var mediaName = ""

        if (Int.MAX_VALUE == mediaId && fixedUrl.isNotEmpty()) {
            return fixedUrl
        }

        val cursor = dbR.query(
            DataBase.MediaEntry.TABLE_MEDIA,
            null,
            selection,
            selectionArgs,
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            mediaName = cursor.getString(cursor.getColumnIndexOrThrow("NAME"))
            Logger.d(TAG, "getMediaNameById - for mediaId: $mediaId using file: $mediaName")
        } else {
            Logger.w(TAG, "getMediaNameById - for mediaId: $mediaId NO FILE NAME")
        }
        cursor.close()

        return mediaName
    }

    override fun finish() {
        super.finish()
        Logger.i(TAG, "FINISH, media ${mediaRunner}")
//        ("FINISH MEDIA ${runn[0]}")
        active = false
        cdTimer.cancel()
        instance = null
    }

    override fun onBackPressed() {
//        super.onBackPressed()
        Logger.w(TAG, "MEDIA ACTIVITY ON BACK PRESSED")
//        instance?.finishAndRemoveTask()
    }

    override fun onStop() {
        super.onStop()
        active = false
        Logger.w(TAG, "ON STOP")
//        ("STOPPED MEDIA ACTIVITY")
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.w(TAG, "MEDIA ON DESTROY")
        cdTimer.cancel()
        MainActivity.firstLaunch = true
        handler.removeCallbacks(mediaRunner)
        active = false
        TimeChecker.prevCfgHash = 0
    }
}