package com.example.finalproject

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltAndroidApp
class App : Application(){
    companion object{
        lateinit var context : App
        lateinit var langCode:String
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        context = this

        val sharedPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val lang = sharedPrefs.getString("language", "en") // fallback = English
        langCode = lang?:"en"
        val locale = LocaleListCompat.forLanguageTags(lang)
        AppCompatDelegate.setApplicationLocales(locale)

    }
}