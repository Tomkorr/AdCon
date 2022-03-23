import com.AdMedia.AdCon.Logger
import com.AdMedia.AdCon.MainActivity
import com.AdMedia.AdCon.NetworkChecker
import com.AdMedia.AdCon.ServerAccess

import android.content.Context
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class SendMediaJson(
    val idmedia: Int,
    val validmediaUpdate: Int,
    val percent: Int,
    val idUpdate: Int,
    val state: Int,
    val timestamp: String,
    val idToken: String,
    val context: Context
) :
    AsyncTask<String?, Void?, Boolean>() {
    @RequiresApi(Build.VERSION_CODES.M)
    val TAG: String = this::class.qualifiedName.toString()

    companion object {
        val NO_STATUS: Int = 0
        val UPDATE_START: Int = 1
        val UPDATE_IN_PROGRESS: Int = 2
        val UPDATE_FINISHED: Int = 3
    }

    private fun mediaJsonObject(
        idmedia: Int,
        idmediaUpdate: Int,
        percent: Int,
        idUpdate: Int,
        state: Int,
        time: String,
        idToken: String
    ): JSONObject {
        return JSONObject("""{"play":{"idmedia":${idmedia}},"update":{"idmedia":${idmediaUpdate},"percent":${percent},"idUpdate":${idUpdate},"state":${state}},"timestamp":"${time}","idToken":"${idToken}"}""")
    }

    override fun doInBackground(vararg p: String?): Boolean {
        var ret = false
        lateinit var jsonObject: JSONObject

        @RequiresApi(Build.VERSION_CODES.O)
        if (NetworkChecker(context).isNetworkAvailable(context)) {
            jsonObject = mediaJsonObject(
                    idmedia,
                    validmediaUpdate,
                    percent,
                    idUpdate,
                    state,
                    timestamp,
                    idToken)

            val urlAddr = MainActivity.domain + "/api/set/"
            if (ServerAccess(context).sendJson(urlAddr, jsonObject)) {
                Logger.d(TAG, "mediaJsonObject send successfully")
                ret = true
            } else {
                Logger.e(TAG, "unable to send json object")
            }
        } else {
            Logger.w(TAG, "mediaJsonObject send not possible, NO NETWORK")
        }
        return ret
    }
}


class SendInformationsJson(
    val timestamp: String,
    val idToken: String,
    val restart: String,
    val context: Context,
    val internetNotAvailable: String,
    val deviceBoots: String,
    val error: String
) :
    AsyncTask<String?, Void?, Boolean>() {
    @RequiresApi(Build.VERSION_CODES.M)
    val TAG: String = this::class.qualifiedName.toString()

    override fun doInBackground(vararg p: String?): Boolean {
        var ret = false
        lateinit var jsonObject: JSONObject

        @RequiresApi(Build.VERSION_CODES.O)
        if (NetworkChecker(context).isNetworkAvailable(context)) {
            jsonObject = informationsJsonObject(
                timestamp,
                idToken,
                restart,
                internetNotAvailable,
                deviceBoots,
                error)

            val urlAddr = MainActivity.domain + "/api/log/"
            if (ServerAccess(context).sendJson(urlAddr, jsonObject)) {
                Logger.d(TAG, "informationsJsonObject send successfully")
                ret = true
            } else {
                Logger.e(TAG, "unable to send json object")
            }
        }  else {
            Logger.w(TAG, "mediaJsonObject send not possible, NO NETWORK")
        }
        return ret
    }

    private fun informationsJsonObject(
        time: String,
        idToken: String,
        restart: String,
        internetNotAvailable: String,
        deviceBootCounter: String,
        error: String

    ): JSONObject {
        return JSONObject("""{"timestamp":"${time}","idToken":"${idToken}","restart":"${restart}","internetNotAvailable:":"${internetNotAvailable}","deviceBootCounter:":"${deviceBootCounter}","error:":"${error}"}""")
    }
}

class SendData(
    val idmedia: Int,
    val validmediaUpdate: Int,
    val percent: Int,
    val idUpdate: Int,
    val state: Int,
    val timestamp: String,
    val idToken: String,
    val restart: String,
    val context: Context,
    val deviceStatusInformation: Boolean,
    val internetNotAvailable: String,
    val deviceBoots: String,
    val error: String
) :
    AsyncTask<String?, Void?, Void?>() {
    @RequiresApi(Build.VERSION_CODES.M)
    val TAG: String = this::class.qualifiedName.toString()

    override fun doInBackground(vararg p: String?): Void? {

        var url: URL
        lateinit var jsonObject: JSONObject

        @RequiresApi(Build.VERSION_CODES.O)
        if (NetworkChecker(context).isNetworkAvailable(context)) {
            // 1. build JSON object
            if (!deviceStatusInformation) {
                jsonObject = mediaJsonObject(
                    idmedia,
                    validmediaUpdate,
                    percent,
                    idUpdate,
                    state,
                    timestamp,
                    idToken
                )
                url = URL(MainActivity.domain + "/api/set/")

                Thread.sleep(101)

            } else {
                jsonObject = informationsJsonObject(
                    timestamp,
                    idToken,
                    restart,
                    internetNotAvailable,
                    deviceBoots,
                    error
                )
                url = URL(MainActivity.domain + "/api/log/")
            }

            // 2. create HttpURLConnection
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10 * 1000  // 10 sec timeout
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")

            try {
                // 3. add JSON content to POST request body
                setPostRequestContent(conn, jsonObject)

                Logger.d(TAG, "START Connect to server, timeout: ${conn.connectTimeout}")
                // 4. make POST request to the given URL
                conn.connect()

                // 5. return response message
                Logger.d(TAG, "HTTP responseMessage: ${conn.responseMessage}")
                conn.disconnect()

                return null
            } catch(e: UnknownHostException) {
                Logger.e(TAG, "doInBackground UnknownHostException catch")
            } catch(e: SocketTimeoutException) {
                Logger.e(TAG, "doInBackground SocketTimeoutException catch")
            } catch(e: Exception) {
                Logger.e(TAG, "doInBackground Exception catch: " + e.printStackTrace())
            }
            conn.disconnect()
        }
        return null
    }

    private fun mediaJsonObject(
        idmedia: Int,
        idmediaUpdate: Int,
        percent: Int,
        idUpdate: Int,
        state: Int,
        time: String,
        idToken: String
    ): JSONObject {
        return JSONObject("""{"play":{"idmedia":${idmedia}},"update":{"idmedia":${idmediaUpdate},"percent":${percent},"idUpdate":${idUpdate},"state":${state}},"timestamp":"${time}","idToken":"${idToken}"}""")
    }

    private fun informationsJsonObject(
        time: String,
        idToken: String,
        restart: String,
        internetNotAvailable: String,
        deviceBootCounter: String,
        error: String

    ): JSONObject {
        return JSONObject("""{"timestamp":"${time}","idToken":"${idToken}","restart":"${restart}","internetNotAvailable:":"${internetNotAvailable}","deviceBootCounter:":"${deviceBootCounter}","error:":"${error}"}""")
    }

    private fun setPostRequestContent(conn: HttpURLConnection, jsonObject: JSONObject) {

        val os = conn.outputStream
        val writer = BufferedWriter(OutputStreamWriter(os, "UTF-8"))
        writer.write(jsonObject.toString())
        Logger.d(TAG, "setPostRequestContent jsonObject: ${jsonObject.toString()}")
        writer.flush()
        writer.close()
        os.close()
    }

    override fun onPostExecute(result: Void?) {
        // TODO: check this.exception
        // TODO: do something with the feed
    }
}