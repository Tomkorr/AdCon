package com.AdMedia.AdCon


//import java.util.Date.
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.joda.time.DateTimeZone
import org.joda.time.Instant
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.Thread.sleep
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.system.exitProcess


// Laczenie z urzadzeniem przez adb:
// PS C:\adb> .\adb.exe connect 192.168.10.104:5555

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {
    private val TAG: String = this::class.qualifiedName.toString()
    lateinit var mediaManager: MediaManager
    private var updateAndHarmonogramTimer = Timer()
    private val clockTimer = Timer()
    private val startDelay = 2 * 1000L
    private val clockUpdateInterval = 1 * 1000L
    private val updateCheckPeriod = 30 * 1000L

    private lateinit var updateCheckTask: UpdateChecker
    private lateinit var checkTimeTask: TimeChecker
    private lateinit var clockUpdateTask: TimerTask

    companion object {
        var checkDomainDefaultLink: String = "http://config.admedia.info.pl/?tokenIph="
        var firstLaunch: Boolean = true
        var deviceBanned: Boolean = false
        var serverConnection: Boolean = false
        var domain: String = ""
        var MAC: String = ""
        var instance: MainActivity? = null
        val warsawZone: ZoneId = ZoneId.of("Europe/Warsaw")
        val domainCheckLinkKey: String = "domain_link"
        val macKey: String = "use_mac"
        val propertiesFile: String = "/config/app.properties"
        var afterBoot = false
        var appStart = false
        var internetCounter = 0L
        val internetCheckInterval = 30L // 301 seconds
        var internetAvailableInThePast = true
        const val networkInterface: String = "eth0"

        fun getTime(): String {
            val nowUtc: Instant = Instant.now()
            val europeWarsaw = DateTimeZone.forID("Europe/Warsaw")
            val nowEuropeWarsaw = nowUtc.toDateTime(europeWarsaw)
            return nowEuropeWarsaw.toString().replace("T", " ").substring(0, 19)
        }
    }


    @SuppressLint("WrongConstant")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        instance = this
        Thread.setDefaultUncaughtExceptionHandler(MyExceptionHandler(this, applicationContext))

        Logger.i(TAG, "ON CREATE at ${getTime()}")

        Logger.i(TAG, "Checking canDrawOverlays")
        if (!Settings.canDrawOverlays(applicationContext)) {
            Logger.e(
                TAG,
                "canDrawOverlays not available, go to Settings -> Apps -> Draw over other apps and enable it for AdCon"
            )
        } else {
            Logger.i(TAG, "canDrawOverlays OK")
        }

        Logger.i(TAG, "ON CREATE at ${getTime()}")

        directoryCreate("images")
        directoryCreate("videos")
        directoryCreate("config")

        loadDomainCheckLink()

        appStart = true
        mediaManager = MediaManager(applicationContext)

        val mediaList = Media.getCurrentMediaList(applicationContext)
        mediaManager.updateStoredMedia(mediaList)

        window.decorView.apply {
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
        }

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels

        requestedOrientation = if (height > width) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        val newMAC = loadMACFromConfig()
        if (newMAC.isNotEmpty()) {
            MAC = newMAC
        } else {
            MAC = NetworkChecker(applicationContext).getMacAddress(networkInterface)
        }
        Logger.i(TAG, "Using MAC address: $MAC")

        backUpdate("MAC: $MAC")
        Handler().postDelayed(Runnable { backUpdate("") }, 7000)

        back.setOnClickListener {
            finishAffinity()
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(0)
        }

        clockUpdateTask = object : TimerTask() {
            override fun run() {
                try {
                    val warsawCurrentDateTime = ZonedDateTime.now(warsawZone)
                    clockUpdate(
                        warsawCurrentDateTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        warsawCurrentDateTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                    )

                    if (0L == (++internetCounter % internetCheckInterval)) {
                        val data = GetDomainAndBan(applicationContext).connectAndRead()
                        if (data.domain.isNotEmpty()) {
                            Logger.d(
                                TAG,
                                "TEST internet connection from clock update task - INTERNET WORKING, in the past: $internetAvailableInThePast"
                            )
                            if (!internetAvailableInThePast) {
                                internetAvailableInThePast = true

                                Logger.i(TAG, "Start restarting update And Harmonogram Timer")
                                updateAndHarmonogramTimer.cancel()
                                updateAndHarmonogramTimer.purge()

                                updateAndHarmonogramTimer = Timer()

                                updateCheckTask = UpdateChecker(applicationContext)
                                checkTimeTask = TimeChecker(applicationContext, mediaManager)
                                updateAndHarmonogramTimer.schedule(
                                    updateCheckTask,
                                    0,
                                    updateCheckPeriod
                                )
                                updateAndHarmonogramTimer.schedule(
                                    checkTimeTask,
                                    startDelay,
                                    updateCheckPeriod
                                )
                                Logger.i(TAG, "Update And Harmonogram Timer restarted successfully")
                            }
                        } else {
                            Logger.w(
                                TAG,
                                "TEST internet connection from clock update task - INTERNET UNAVAILABLE! (domain '$domain', MAC $MAC)"
                            )
                            internetAvailableInThePast = false
                        }
                    }
                } catch(e: Exception) {
                    Logger.e(TAG, "clockUpdateTask catch: " + e.printStackTrace())
                }
            }
        }

        try {
            clockTimer.schedule(clockUpdateTask, 0, clockUpdateInterval)

            updateCheckTask = UpdateChecker(applicationContext)
            checkTimeTask = TimeChecker(applicationContext, mediaManager)
            updateAndHarmonogramTimer.schedule(updateCheckTask, 0, updateCheckPeriod)
            updateAndHarmonogramTimer.schedule(checkTimeTask, startDelay, updateCheckPeriod)
        } catch(e: Exception) {
            Logger.e(TAG, "onCreate timer.schedule catch: " + e.printStackTrace())
        }
    }


    override fun finish() {
        Logger.w(TAG, "FINISH at ${getTime()}")

        super.finish()
        instance = null

        val intent =
            applicationContext.packageManager.getLaunchIntentForPackage("com.example.AdCon")

        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        applicationContext.startActivity(intent)
        exitProcess(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        val intent =
            applicationContext.packageManager.getLaunchIntentForPackage("com.example.AdCon")

        Logger.w(TAG, "DESTROY MAIN at ${getTime()}")

        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        applicationContext.startActivity(intent)
    }

    override fun onPause() {
        super.onPause()

        Logger.i(TAG, "ON PAUSE MAIN at ${getTime()}")
    }

    fun clockUpdate(time: String, data: String) {
        this@MainActivity.runOnUiThread(java.lang.Runnable {
            this.time.text = time
            this.date.text = data
        })
    }

    fun infoUpdate(newInfo: String) {
        this@MainActivity.runOnUiThread(java.lang.Runnable {
            this.info.text = newInfo
        })
    }

    fun backUpdate(newTxt: String) {
        this@MainActivity.runOnUiThread(java.lang.Runnable {
            this.back.text = newTxt
        })
    }

    fun printInfoAndWait(newInfo: String, delayMs: Long) {
        infoUpdate(newInfo)
        sleep(delayMs)
    }

    fun lostServerConnection() {
        Logger.w(TAG, "lostServerConnection CALLED")
        serverConnection = false
        infoUpdate("BRAK POŁĄCZENIA Z SERWEREM MEDIÓW")
    }

    fun serverConnectionEstablished() {
        Logger.d(TAG, "serverConnectionEstablished CALLED")
        serverConnection = true
    }

    fun isServerConnection(): Boolean {
        Logger.v(TAG, "isServerConnection CALLED, ret $serverConnection")
        return serverConnection
    }

    override fun onResume() {
        super.onResume()
        Logger.i(TAG, "MAIN RESUME at ${getTime()}")
    }

    override fun onBackPressed() {
//        super.onBackPressed()

        Logger.w(TAG, "MAIN ACTIVITY ON BACK PRESSED at ${getTime()}")

//        RestartApp().restartApp()
    }

    fun directoryCreate(directoryName: String) {
        val newDir =
            File(applicationContext.getExternalFilesDir(null)?.absolutePath.toString() + "/$directoryName/")
        val isNewDirectoryCreated: Boolean = newDir.mkdir()

        if (isNewDirectoryCreated) {
            Logger.i(TAG, "directory \"$directoryName\" is created successfully.")
        } else {
            Logger.i(TAG, "directory \"$directoryName\" already exists.")
        }
    }

    fun mediaUpdateRequired():Boolean {
        return mediaManager.mediaUpdate()
    }

    fun saveDomainCheckLink(link: String) {
        Logger.d(TAG, "Store $domainCheckLinkKey=$link in:")
        val properties = Properties()
        properties.put(domainCheckLinkKey, link)

        var cfgFile = applicationContext.getExternalFilesDir(null)?.absolutePath.toString() + propertiesFile
        Logger.d(TAG, "Store cfgFile: $cfgFile")

        var fileOutputStream = FileOutputStream(cfgFile)
        properties.store(fileOutputStream, "save to properties file")

        Logger.d(TAG, "saveDomainCheckLink finished")
    }

    fun loadDomainCheckLink() {
        val properties = Properties()
        val cfgFile = applicationContext.getExternalFilesDir(null)?.absolutePath.toString() + propertiesFile
        Logger.d(TAG, "loadDomainCheckLink from $cfgFile")

        if (File(cfgFile).exists()) {
            val inputStream = FileInputStream(cfgFile)
            properties.load(inputStream)
            val checkDomainLink = properties.getProperty(domainCheckLinkKey)
            if (checkDomainLink != null) {
                Logger.d(TAG, "domainLinkCheck available: $checkDomainLink")
                checkDomainDefaultLink = checkDomainLink
            } else {
                Logger.w(TAG, "domainLinkCheck NOT available! using: $checkDomainDefaultLink")
            }
        } else  {
            // Create config file with default link:
            Logger.w(TAG, "Properties file NOT available! Creating it with default link")
            saveDomainCheckLink(checkDomainDefaultLink)
        }
    }

    fun loadMACFromConfig(): String {
        var newMAC: String = ""
        val properties = Properties()
        val cfgFile = applicationContext.getExternalFilesDir(null)?.absolutePath.toString() + propertiesFile
        Logger.d(TAG, "loadMACFromConfig: $cfgFile")

        if (File(cfgFile).exists()) {
            val inputStream = FileInputStream(cfgFile)
            properties.load(inputStream)
            val useOtherMac = properties.getProperty(macKey)
            if (useOtherMac != null && useOtherMac.isNotEmpty()) {
                Logger.d(TAG, "New MAC available, using: $useOtherMac")
                newMAC = useOtherMac
            }
        }
        return newMAC
    }
}