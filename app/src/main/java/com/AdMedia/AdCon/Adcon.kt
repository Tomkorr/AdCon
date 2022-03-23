package com.AdMedia.AdCon

import android.app.Application
import android.content.Context

class Adcon: Application() {

    companion object {
        lateinit var instance: Adcon

        @JvmName("getInstance1")
        fun getInstance(): Adcon {
            return instance
        }
    }

    @Override
    override fun  onCreate() {
        super.onCreate()
        instance = this
    }

    @Override
    override fun getApplicationContext(): Context {
        return super.getApplicationContext()
    }

}