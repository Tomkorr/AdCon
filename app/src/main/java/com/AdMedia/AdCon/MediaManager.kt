package com.AdMedia.AdCon

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.lang.Thread.sleep


class MediaManager {
    private val TAG = this::class.qualifiedName.toString()
    private val context: Context
    var downloadManager: DownloadManager
    val dbHelper: DatabaseDbHelper
    var downloadInProgres: Boolean = false

    val imagesPath:String
    val videosPath: String
    val updateInterval: Long = 10  // Seconds
    var storedMedia = mutableMapOf<mediaKey, MediaItem>()
    var downloadingItems = mutableListOf<MediaItem>()

    constructor(context_: Context) {
        Logger.i(TAG, "MediaManager CREATED")

        context = context_
        dbHelper = DatabaseDbHelper(context)
        downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        imagesPath = context.getExternalFilesDir(null)?.absolutePath.toString() + "/images/"
        Logger.d(TAG, "MediaManager use imagesPath: $imagesPath")

        videosPath = context.getExternalFilesDir(null)?.absolutePath.toString() + "/videos/"
        Logger.d(TAG, "MediaManager use videosPath: $videosPath")
    }

    protected fun finalize() {
        Logger.w(TAG, "MediaManager FINALIZED")
    }

    enum class CHEKSUM_STATUS {
        UNKNOWN,
        CALCULATE,
        CALCULATED,
        MATCHING
    }

    enum class ITEM_STATUS {
        UNKNOWN,
        DOWNLOADING,
        DOWNLOADAD,
        REMOVE,
        KEEP
    }

    data class MediaItem(
        val itemName: String,
        var mediaID: Int,
        var urlAddress: String,
        var expectedChecksum: String,
        val fileSize: Long
    ) {
        var calculatedChecksum: String = ""
        var itemStatus: ITEM_STATUS = ITEM_STATUS.UNKNOWN
        var checksumStatus: CHEKSUM_STATUS = CHEKSUM_STATUS.UNKNOWN
        var downloadReference: Long = -1
    }

    fun externalMemoryAvailable(): Boolean {
        return Environment.getExternalStorageState() ==
                Environment.MEDIA_MOUNTED
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun getAvailableExternalMemorySize(): Long {
        return if (externalMemoryAvailable()) {
            val path: String = context.getExternalFilesDir(null)?.absolutePath.toString()
            val stat = StatFs(path)
            val blockSize = stat.blockSizeLong
            val availableBlocks = stat.availableBlocksLong
            availableBlocks * blockSize
        } else {
            -1
        }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun getTotalExternalMemorySize(): Long {
        return if (externalMemoryAvailable()) {
            val path: String = context.getExternalFilesDir(null)?.absolutePath.toString()
            val stat = StatFs(path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            totalBlocks * blockSize
        } else {
            -1
        }
    }

    fun formatSize(size_: Long): String {
        var size = size_
        var suffix: String? = null
        if (size >= 1024) {
            suffix = " KB"
            size /= 1024
            if (size >= 1024) {
                suffix = " MB"
                size /= 1024
            }
        }
        val resultBuffer = StringBuilder(java.lang.Long.toString(size))

        var commaOffset = resultBuffer.length - 3
        while (commaOffset > 0) {
            resultBuffer.insert(commaOffset, '\'')
            commaOffset -= 3
        }

        if (suffix != null) resultBuffer.append(suffix)
        return resultBuffer.toString()
    }

//    class DownloadStatusChecker: AsyncTask<MutableList<MediaItem>, >

    fun updateStoredFiles(path: String, mediaList: ArrayList<Media.MediaDbItem>):Int {
        Logger.d(TAG, "update Stored Media CALLED, path: $path")
        val mediaNotEmpty = mediaList.isNotEmpty()
        var knownOkFiles = 0

        try {
            File(path).walk().forEach {
                if (it.isFile) {
                    Logger.d(TAG, "Find file: ${it.name}")
                    val storedItem = MediaItem(it.name, 0, "", "", it.length())
                    storedItem.checksumStatus = CHEKSUM_STATUS.CALCULATE

                    try {
                        storedItem.calculatedChecksum = MD5.calculateMD5(it)
                    } catch(e: Exception) {
                        storedItem.checksumStatus = CHEKSUM_STATUS.UNKNOWN
                        Logger.e(TAG, "File: ${it.name} MD5 calculation FAILED")
                    }

                    if (storedItem.calculatedChecksum.isNotEmpty()) {
                        storedItem.checksumStatus = CHEKSUM_STATUS.CALCULATED

                        fun findNameInMediaDb(
                            locMediaList: ArrayList<Media.MediaDbItem>,
                            fileName: String
                        ): Media.MediaDbItem? {
                            locMediaList.forEach{
                                if (it.name == fileName) {
                                    return it
                                }
                            }
                            return null
                        }

                        if (mediaNotEmpty) {
                            val dbItem = findNameInMediaDb(mediaList, storedItem.itemName)
                            if (dbItem != null && dbItem.checksum == storedItem.calculatedChecksum) {
                                Logger.d(TAG, "Name and checksum MATCHING, KEEP: ${it.name}")
                                storedItem.checksumStatus = CHEKSUM_STATUS.MATCHING
                                storedItem.itemStatus = ITEM_STATUS.KEEP
                                storedItem.mediaID = dbItem.mediaID
                                storedItem.urlAddress = dbItem.urlAddress
                                storedItem.expectedChecksum = dbItem.checksum
                                knownOkFiles++
                            }
                            else {
                                Logger.d(TAG, "Name or checksum NOT MATCH, REMOVE: ${it.name}")
                                storedItem.itemStatus = ITEM_STATUS.REMOVE
                            }
                        } else {
                            Logger.w(TAG, "Media DB empty, no file removal")
                        }

                        if (storedItem.itemStatus == ITEM_STATUS.KEEP) {
                            storedMedia.put(storedItem.itemName, storedItem)

                            Logger.d(
                                TAG,
                                "File: ${storedItem.itemName} added to storedMedia map, size: ${storedItem.fileSize} MD5: ${storedItem.calculatedChecksum}"
                            )
                        } else if (storedItem.itemStatus == ITEM_STATUS.REMOVE) {
                            Logger.i(TAG, "REMOVING File: ${storedItem.itemName}")
                            try {
                                it.delete()
                            } catch(e: java.lang.Exception) {
                                Logger.w(
                                    TAG,
                                    "DELETE file ${storedItem.itemName} catch, e: ${e.message}"
                                )
                            }
                        } else {
                            Logger.d(
                                TAG,
                                "Other Status (${storedItem.itemStatus}) for file: ${storedItem.itemName}, DO NOTHING"
                            )
                        }
                    }
                }
            }
        } catch(e: Exception) {
            Logger.e(TAG, "updateStoredImages CATCH, e.message: ${e.message}")
        }

        Logger.d(TAG, "update Stored Media FINISHED OK (knownOkFiles: $knownOkFiles, path: $path")
        return knownOkFiles
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun updateStoredMedia(mediaList: ArrayList<Media.MediaDbItem>):Boolean {
        var downloadRequired :Boolean
        var knownOkFiles: Int
        var imageOrVideo:Int = 0
        knownOkFiles = updateStoredFiles(imagesPath, mediaList)
        knownOkFiles += updateStoredFiles(videosPath, mediaList)

        mediaList.forEach{
            if (it.type == Media.MEDIA_TYPE.IMAGE || it.type == Media.MEDIA_TYPE.VIDEO) {
                imageOrVideo++
            }
        }

        downloadRequired = knownOkFiles < imageOrVideo

        Logger.i(
            TAG,
            "updateStoredMedia downloadRequired: $downloadRequired (knownOkFiles: $knownOkFiles, imageOrVideo: ${imageOrVideo})"
        )

        Logger.i(
            TAG,
            "Total External Storage: ${formatSize(getTotalExternalMemorySize())}"
        )
        Logger.i(
            TAG,
            "Free External Storage: ${formatSize(getAvailableExternalMemorySize())}"
        )

        return downloadRequired
    }

    private fun downloadItem(uri: Uri, destFilePath: String, fileName: String): Long {
        val downloadReference: Long
        val request = DownloadManager.Request(uri)
        //Setting title of request
        request.setTitle("Downloading $fileName")

        request.setDestinationUri(Uri.parse("file://$destFilePath/$fileName"))

        downloadReference = downloadManager.enqueue(request)
        return downloadReference
    }

    fun startDownload(itemMedia: MediaItem, type: Media.MEDIA_TYPE): Long {
        var referenceID: Long = -1
        var path: String

        if (Media.MEDIA_TYPE.IMAGE == type) {
            path = imagesPath
        } else if (Media.MEDIA_TYPE.VIDEO == type) {
            path = videosPath
        } else {
            Logger.e(TAG, "UNKNOWN TYPE")
            return referenceID
        }

        val image_uri: Uri = Uri.parse("${itemMedia.urlAddress}")
        referenceID = downloadItem(image_uri, path, itemMedia.itemName)
        Logger.i(TAG, "Download STARTED, refId: $referenceID")

        return referenceID
    }

    fun processDownloadItem(cursor: Cursor): Triple<Int, Long, Long> {
        val id = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_ID));
        val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
        val fileName = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
        var toDownload = 0
        var bytesDownloaded = 0L
        var fileSize = 0L
        Logger.v(TAG, "Checking download status for $fileName id $id (status: $status})")

        for(item in downloadingItems) {
            if (item.downloadReference == id) {
                bytesDownloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                fileSize = item.fileSize

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Logger.d(TAG, "Downloading Complete for $id, downloaded $bytesDownloaded bytes")
                    item.checksumStatus = CHEKSUM_STATUS.CALCULATE
                } else if (status == DownloadManager.STATUS_RUNNING) {
                    Logger.d(TAG, "Downloading Running for $id, downloaded $bytesDownloaded bytes")
                    toDownload = 1
                } else if (status == DownloadManager.STATUS_PENDING) {
                    Logger.d(TAG, "Downloading Pending for $id, downloaded $bytesDownloaded bytes")
                    toDownload = 1
                } else if (status == DownloadManager.STATUS_PAUSED) {
                    Logger.d(TAG, "Downloading Paused for $id, downloaded $bytesDownloaded bytes")
                    toDownload = 1
                } else {
                    Logger.w(TAG, "Downloading other status $status")
                }
            }
        }
        return Triple(toDownload, bytesDownloaded, fileSize)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun updateDownloadStatus(): Int {
        Logger.d(TAG, "Update download status")
        val q = DownloadManager.Query()
        var refIds = mutableListOf<Long>()

        var allToDownload = 0
        var allBytesDownloaded = 0L
        var allFileSize = 0L

        // Check for all in downloadingItems
        downloadingItems.onEach {
            refIds.add(it.downloadReference)
        }

        q.setFilterById(*refIds.toLongArray())
        val cursor: Cursor = downloadManager.query(q)

        cursor.use { cur ->
            while (cur.moveToNext()) {
                val (toDownload, bytesDownloaded, fileSize) = processDownloadItem(cur)
                allToDownload += toDownload
                allBytesDownloaded += bytesDownloaded
                allFileSize += fileSize
            }
        }

        val percent = allBytesDownloaded.toFloat() * 100F / allFileSize
        var downloading = (downloadingItems.size + 1) - allToDownload
        if (allToDownload > 0) {
            Logger.d(TAG, "Downloaded ${percent}% of all files")
            MainActivity.instance?.infoUpdate("Pobieranie pliku $downloading/${downloadingItems.size} - ${percent.toInt()}%")
            Media.udpateDownloadStatus(percent.toInt(), context)
        }

        return allToDownload
    }

    fun addNewItem(
        itemName: String,
        mediaID: Int,
        urlAddress: String,
        expectedChecksum: String,
        fileSize: Long,
        type: Media.MEDIA_TYPE
    ): Long {
        var fileToDownloadSize: Long = 0
        Logger.v(TAG, "Add new Media Item $itemName")

        if (storedMedia.containsKey(itemName)) {
            Logger.d(TAG, "Checking file $itemName")
            val mediaItem = storedMedia.get(itemName)
            if (mediaItem?.calculatedChecksum == expectedChecksum) {
                Logger.i(TAG, "Checksum  OK for ($mediaID) $itemName")
            } else {
                Logger.w(TAG, "Checksum NOT MATCHING for ($mediaID) $itemName")
            }
        } else {
            Logger.d(TAG, "Start file download ($mediaID) $itemName")

            val newItem = MediaItem(itemName, mediaID, urlAddress, expectedChecksum, fileSize)
            newItem.itemStatus = ITEM_STATUS.DOWNLOADING
            newItem.checksumStatus = CHEKSUM_STATUS.UNKNOWN

            val downloadRef = startDownload(newItem, type)

            if (downloadRef > 0) {
                fileToDownloadSize = newItem.fileSize
                newItem.downloadReference = downloadRef
                downloadingItems.add(newItem)
            } else {
                Logger.w(TAG, "download referenceID <= 0 ($downloadRef)")
            }

            storedMedia.put(itemName, newItem)
        }
        return fileToDownloadSize
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun mediaUpdate():Boolean {
        var repeatUpdate = false
        Logger.d(TAG, "mediaUpdate REQUESTED, starting media update")

        while (downloadInProgres) {
            Logger.w(TAG, "Downloading currently in progress, waiting")
            sleep(1000)
        }

        val mediaList = Media.getCurrentMediaList(context)

        if (updateStoredMedia(mediaList)) {
            val leftFree: Long = 10         // Percent, left free at least 10%
            val freeSpace = getAvailableExternalMemorySize()
            val safeStorageMargin = (getTotalExternalMemorySize() * leftFree / 100)
            val available90PercentOfSpace = if (freeSpace > safeStorageMargin) freeSpace - safeStorageMargin else  0
            var allNewItemsSize:Long = 0

            Logger.i(TAG, "DOWNLOAD NEEDED, available free storage for new files: ${formatSize(available90PercentOfSpace)}")

            downloadInProgres = true
            downloadingItems.clear()
            mediaList.forEach() {
                Logger.d(TAG, it.toString())

                if (it.size < available90PercentOfSpace) {
                    if (Media.MEDIA_TYPE.IMAGE == it.type || Media.MEDIA_TYPE.VIDEO == it.type) {
                        Logger.v(TAG, "processing ${it.type}")
                        allNewItemsSize += addNewItem(it.name, it.mediaID, it.urlAddress, it.checksum, it.size, it.type)
                        Logger.v(TAG, "Bytes to download ${formatSize(allNewItemsSize)}, available free storage ${formatSize(available90PercentOfSpace)}")
                    } else if (Media.MEDIA_TYPE.URL == it.type) {
                        Logger.v(TAG, "processing ${it.type}")
//                TODO: addURL
                    } else {
                        Logger.w(TAG, "UNKNOWN MEDIA_TYPE: ${it.type}")
                    }
                }
                else {
                    Logger.w(TAG, "NO MORE FREE STORAGE FOR: ${it.name}, skipping all next files")
                }
            }

            Logger.i(TAG, "Start checking files download progress")
            while (updateDownloadStatus() > 0) {
                sleep(1000 * updateInterval)
            }
            Logger.i(TAG, "No more files to download")
            Media.udpateDownloadStatus(100, context)

            sleep(1000)
            repeatUpdate = updateStoredMedia(mediaList)  // 'false' if all media are downloaded correctly, 'true' if still something need to be downloaded
            downloadInProgres = false
            mediaList.clear()
        } else {
            Logger.i(TAG, "Nothing to update, all files up to date")
            Media.udpateDownloadStatus(100, context)
        }
        return repeatUpdate
    }

    fun isDownloadInProgress(): Boolean {
        Logger.d(TAG, "isDownloadInProgress ? - $downloadInProgres")
        return downloadInProgres
    }

    fun ifAllFilesOk(): Boolean {
        Logger.d(TAG, "start ifAllFilesOk?")
        var allOk = true
        for (mediaItem in storedMedia) {
            if (mediaItem.value.checksumStatus != CHEKSUM_STATUS.MATCHING) {
                allOk = false
                Logger.w(TAG, "File ${mediaItem.value.itemName} Checksum NOT OK, allOk set to false")
            }
            else {
                Logger.v(TAG, "File ${mediaItem.value.itemName} Checksum OK")
            }
        }
        Logger.d(TAG, "end ifAllFilesOk ? - $allOk")
        return allOk
    }

    fun getStoredMediaMap(): Map<mediaKey, MediaItem> {
        return storedMedia
    }
}

typealias mediaKey = String