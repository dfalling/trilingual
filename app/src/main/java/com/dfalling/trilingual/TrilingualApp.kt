package com.dfalling.trilingual

import android.app.Application
import com.dfalling.trilingual.data.HistoryDatabase
import com.dfalling.trilingual.data.SettingsRepository
import com.dfalling.trilingual.translate.Translator

class TrilingualApp : Application() {
    val settings by lazy { SettingsRepository(this) }
    val history by lazy { HistoryDatabase.create(this).translations() }
    val translator by lazy { Translator() }
}
