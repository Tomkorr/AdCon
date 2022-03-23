package com.AdMedia.AdCon

import android.util.Log

class Logger {
    enum class LEVEL {
        ERROR,
        WARNING,
        INFO,
        DEBUG,
        VERBOSE
    }
    companion object {
        val app_log_level = LEVEL.WARNING

        @JvmStatic
        fun e(tag: String, msg: String) {
            if (LEVEL.ERROR <= app_log_level) {
                Log.e(tag, msg)
            }
        }

        @JvmStatic
        fun w(tag: String, msg: String) {
            if (LEVEL.WARNING <= app_log_level) {
                Log.w(tag, msg)
            }
        }

        @JvmStatic
        fun i(tag: String, msg: String) {
            if (LEVEL.INFO <= app_log_level) {
                Log.i(tag, msg)
            }
        }

        @JvmStatic
        fun d(tag: String, msg: String) {
            if (LEVEL.DEBUG <= app_log_level) {
                Log.d(tag, msg)
            }
        }

        @JvmStatic
        fun v(tag: String, msg: String) {
            if (LEVEL.VERBOSE <= app_log_level) {
                Log.v(tag, msg)
            }
        }
    }
}