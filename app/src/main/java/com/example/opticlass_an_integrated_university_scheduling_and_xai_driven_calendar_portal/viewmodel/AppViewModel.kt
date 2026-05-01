package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.LoginRequest
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.RetrofitClient
import kotlinx.coroutines.launch

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
    var authToken by mutableStateOf("")
        private set

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val token = prefs.getString(KEY_TOKEN, null)
        val username = prefs.getString(KEY_USERNAME, null)
        val roleStr = prefs.getString(KEY_ROLE, "INSTRUCTOR")

        if (token != null && username != null) {
            authToken = token
            currentUserName = username
            userRole = if (roleStr == "ADMIN") UserRole.ADMIN else UserRole.INSTRUCTOR
            isLoggedIn = true
            fetchInitialData()
        }
    }

    fun login(username: String, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val passwordHash = sha256(password)
                val response = RetrofitClient.instance.login(LoginRequest(username, passwordHash))

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    authToken = "Bearer ${body.token}"
                    currentUserName = body.username
                    userRole = if (body.role == "ADMIN") UserRole.ADMIN else UserRole.INSTRUCTOR
                    isLoggedIn = true

                    prefs.edit().apply {
                        putString(KEY_TOKEN, authToken)
                        putString(KEY_USERNAME, currentUserName)
                        putString(KEY_ROLE, body.role)
                        apply()
                    }

                    fetchInitialData()
                    onResult(true, null)
                } else {
                    onResult(false, "Giriş başarısız: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Login error", e)
                onResult(false, e.localizedMessage)
            }
        }
    }

    private fun fetchInitialData() {
        viewModelScope.launch {
            try {
                val userResponse = RetrofitClient.instance.getUsers(authToken)
                if (userResponse.isSuccessful) {
                    AppRepository.syncUsers(userResponse.body() ?: emptyList())
                }

                val classroomResponse = RetrofitClient.instance.getClassrooms(authToken)
                if (classroomResponse.isSuccessful) {
                    AppRepository.syncClassrooms(classroomResponse.body() ?: emptyList())
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Fetch data error", e)
            }
        }
    }

    fun logout() {
        isLoggedIn = false
        currentUserName = ""
        authToken = ""
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROLE = "user_role"
    }
}
