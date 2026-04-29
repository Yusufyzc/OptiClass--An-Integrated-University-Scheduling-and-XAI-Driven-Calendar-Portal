package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(application)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            application,
            "opticlass_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var isLoggedIn by mutableStateOf(false)
        private set
    var currentUserName by mutableStateOf("")
        private set
    var userRole by mutableStateOf(UserRole.INSTRUCTOR)
        private set

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val savedUsername = prefs.getString(KEY_USERNAME, null) ?: return
        val user = AppRepository.users.find { it.username == savedUsername } ?: return
        currentUserName = savedUsername
        userRole = user.role
        isLoggedIn = true
    }

    fun login(username: String, password: String): Boolean {
        val user = AppRepository.users.find {
            decodeUsername(it.username).equals(username.trim(), ignoreCase = true) &&
            it.password == sha256(password)
        } ?: return false
        currentUserName = user.username
        userRole = user.role
        isLoggedIn = true
        prefs.edit().putString(KEY_USERNAME, user.username).apply()
        return true
    }

    fun logout() {
        isLoggedIn = false
        currentUserName = ""
        prefs.edit().remove(KEY_USERNAME).apply()
    }

    companion object {
        private const val KEY_USERNAME = "username"
    }
}
