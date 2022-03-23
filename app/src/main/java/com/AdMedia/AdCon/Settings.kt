package com.AdMedia.AdCon

class Settings(val informationBar: String, val idSynch: Int, val serverTime: Long, val infoBarBackgroundColor: String, val infoBarFontColor: String) {
    override fun toString(): String {
        return "Settings (idSynch=$idSynch, serverTime=$serverTime, informationBar='$informationBar', infoBarBackgroundColor='$infoBarBackgroundColor', infoBarFontColor='$infoBarFontColor')"
    }
}