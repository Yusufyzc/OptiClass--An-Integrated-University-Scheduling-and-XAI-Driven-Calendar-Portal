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
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.AdminResetPasswordRequest
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.AvailabilityDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.CourseDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.LoginRequest
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.MessageDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.NotificationDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.RetrofitClient
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.ScheduleDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.UserCreateDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.UserUpdateDto
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
    var mustChangePassword by mutableStateOf(false)
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

                    mustChangePassword = body.mustChangePassword
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
                if (userResponse.isSuccessful) AppRepository.syncUsers(userResponse.body() ?: emptyList())

                val classroomResponse = RetrofitClient.instance.getClassrooms(authToken)
                if (classroomResponse.isSuccessful) AppRepository.syncClassrooms(classroomResponse.body() ?: emptyList())

                val courseResponse = RetrofitClient.instance.getCourses(authToken)
                if (courseResponse.isSuccessful) AppRepository.syncCourses(courseResponse.body() ?: emptyList())

                val scheduleResponse = RetrofitClient.instance.getAllSchedules(authToken)
                if (scheduleResponse.isSuccessful) {
                    scheduleResponse.body()?.forEach { dto ->
                        AppRepository.syncSchedule(dto.instructorUsername, dto.slots)
                    }
                }

                val availResponse = RetrofitClient.instance.getAllAvailabilities(authToken)
                if (availResponse.isSuccessful) AppRepository.syncAvailabilities(availResponse.body() ?: emptyList())

                val notifResponse = RetrofitClient.instance.getNotifications(authToken)
                if (notifResponse.isSuccessful) AppRepository.syncNotifications(notifResponse.body() ?: emptyList())
            } catch (e: Exception) {
                Log.e("AppViewModel", "Fetch data error", e)
            }
        }
    }

    fun importCourseData(courses: List<CourseImport>, onResult: (List<Pair<String, String>>) -> Unit) {
        viewModelScope.launch {
            try {
                val newCredentials = mutableListOf<Pair<String, String>>()
                val seenUsernames = mutableSetOf<String>()

                for (course in courses) {
                    val plainUsername = course.email.substringBefore("@").ifBlank { generateUsername(course.lecturer) }
                    if (seenUsernames.contains(plainUsername)) continue
                    seenUsernames.add(plainUsername)

                    val alreadyExists = AppRepository.users.any { it.username == plainUsername }
                    if (!alreadyExists) {
                        val plainPassword = generatePassword()
                        val resp = RetrofitClient.instance.addUser(
                            token = authToken,
                            user = UserCreateDto(
                                username = plainUsername,
                                passwordHash = sha256(plainPassword),
                                role = "INSTRUCTOR",
                                fullName = course.lecturer,
                                email = course.email,
                                department = course.department
                            )
                        )
                        if (resp.isSuccessful) newCredentials.add(plainUsername to plainPassword)
                    }
                }

                val courseDtos = courses.filter { it.duration != -1 }.map { c ->
                    val lecturerUsername = c.email.substringBefore("@").ifBlank { generateUsername(c.lecturer) }
                    CourseDto(code = c.code, name = c.name, lecturerUsername = lecturerUsername,
                        department = c.department, email = c.email, duration = c.duration, classroomId = c.classroomId)
                }
                if (courseDtos.isNotEmpty()) RetrofitClient.instance.importCourses(authToken, courseDtos)

                val userResponse = RetrofitClient.instance.getUsers(authToken)
                if (userResponse.isSuccessful) AppRepository.syncUsers(userResponse.body() ?: emptyList())

                val courseResponse = RetrofitClient.instance.getCourses(authToken)
                if (courseResponse.isSuccessful) AppRepository.syncCourses(courseResponse.body() ?: emptyList())

                onResult(newCredentials)
            } catch (e: Exception) {
                Log.e("AppViewModel", "Import error", e)
                onResult(emptyList())
            }
        }
    }

    fun clearMustChangePassword() {
        mustChangePassword = false
    }

    fun changePassword(currentPassword: String, newPassword: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.changePassword(
                    token = authToken,
                    username = currentUserName,
                    request = com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.ChangePasswordRequest(
                        currentPasswordHash = sha256(currentPassword),
                        newPasswordHash = sha256(newPassword)
                    )
                )
                if (response.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(false, "Current password is incorrect")
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage)
            }
        }
    }

    fun addUser(username: String, password: String, role: String, fullName: String, email: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.addUser(
                    token = authToken,
                    user = UserCreateDto(username = username, passwordHash = sha256(password),
                        role = role, fullName = fullName, email = email, department = "")
                )
                if (response.isSuccessful) {
                    val userResponse = RetrofitClient.instance.getUsers(authToken)
                    if (userResponse.isSuccessful) AppRepository.syncUsers(userResponse.body() ?: emptyList())
                    onResult(true, null)
                } else {
                    onResult(false, "Kullanıcı eklenemedi: ${response.code()}")
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage)
            }
        }
    }

    fun deleteUser(username: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.deleteUser(token = authToken, username = username)
                if (response.isSuccessful) {
                    AppRepository.users.removeAll { it.username == username }
                    onResult(true, null)
                } else {
                    onResult(false, "Kullanıcı silinemedi: ${response.code()}")
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage)
            }
        }
    }

    fun updateUser(username: String, fullName: String, email: String, role: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.updateUser(
                    token = authToken,
                    username = username,
                    user = UserUpdateDto(role = role, fullName = fullName, email = email, department = "")
                )
                if (response.isSuccessful) {
                    val userResponse = RetrofitClient.instance.getUsers(authToken)
                    if (userResponse.isSuccessful) AppRepository.syncUsers(userResponse.body() ?: emptyList())
                    onResult(true, null)
                } else {
                    onResult(false, "Güncelleme başarısız: ${response.code()}")
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage)
            }
        }
    }

    fun resetUserPassword(username: String, newPassword: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.adminResetPassword(
                    token = authToken,
                    username = username,
                    request = AdminResetPasswordRequest(newPasswordHash = sha256(newPassword))
                )
                if (response.isSuccessful) onResult(true, null)
                else onResult(false, "Şifre sıfırlanamadı: ${response.code()}")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage)
            }
        }
    }

    fun deleteCourse(code: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.deleteCourse(token = authToken, code = code)
                if (response.isSuccessful) {
                    AppRepository.courseImports.removeAll { it.code == code }
                    AppRepository.users.forEachIndexed { index, user ->
                        if (user.courses.any { it.code == code }) {
                            AppRepository.users[index] = user.copy(courses = user.courses.filter { it.code != code }.toMutableList())
                        }
                    }
                    onResult(true)
                } else onResult(false)
            } catch (e: Exception) { onResult(false) }
        }
    }

    fun addClassroom(classroom: Classroom, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.addClassroom(
                    token = authToken,
                    classroom = com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.ClassroomDto(
                        id = classroom.id, roomCode = classroom.roomCode, capacity = classroom.capacity)
                )
                if (response.isSuccessful) {
                    AppRepository.classrooms.add(classroom)
                    onResult(true, null)
                } else {
                    onResult(false, "Eklenemedi: ${response.code()}")
                }
            } catch (e: Exception) { onResult(false, e.localizedMessage) }
        }
    }

    fun deleteClassroom(id: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.deleteClassroom(token = authToken, id = id)
                if (response.isSuccessful) {
                    AppRepository.classrooms.removeAll { it.id == id }
                    onResult(true, null)
                } else {
                    onResult(false, "Silinemedi: ${response.code()}")
                }
            } catch (e: Exception) { onResult(false, e.localizedMessage) }
        }
    }

    fun importClassrooms(classrooms: List<Classroom>, onResult: (Int, Int) -> Unit) {
        viewModelScope.launch {
            var saved = 0; var skipped = 0
            for (classroom in classrooms) {
                if (AppRepository.classrooms.any { it.roomCode.equals(classroom.roomCode, ignoreCase = true) }) {
                    skipped++; continue
                }
                try {
                    val response = RetrofitClient.instance.addClassroom(
                        token = authToken,
                        classroom = com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.ClassroomDto(
                            id = classroom.id, roomCode = classroom.roomCode, capacity = classroom.capacity)
                    )
                    if (response.isSuccessful) { AppRepository.classrooms.add(classroom); saved++ }
                    else skipped++
                } catch (e: Exception) { skipped++ }
            }
            onResult(saved, skipped)
        }
    }

    fun saveSchedule(username: String, draftSchedule: Map<String, SnapshotStateMap<String, CourseImport?>>) {
        viewModelScope.launch {
            try {
                val flatSlots = mutableMapOf<String, CourseDto?>()
                draftSchedule.forEach { (day, dayMap) ->
                    dayMap.forEach { (slot, course) ->
                        if (course != null) {
                            flatSlots["${day}_${slot}"] = CourseDto(
                                code = course.code, name = course.name,
                                lecturerUsername = AppRepository.users.find { u -> u.courses.any { it.code == course.code } }?.username,
                                department = course.department, email = course.email,
                                duration = course.duration, classroomId = course.classroomId
                            )
                        }
                    }
                }
                RetrofitClient.instance.updateSchedule(authToken, username, ScheduleDto(username, flatSlots))
            } catch (e: Exception) { Log.e("AppViewModel", "Save schedule error", e) }
        }
    }

    fun submitAvailability(username: String, slots: Map<String, Set<String>>, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.submitAvailability(
                    token = authToken, username = username,
                    body = AvailabilityDto(instructorUsername = username, slots = slots.mapValues { it.value.toList() })
                )
                if (response.isSuccessful) {
                    AppRepository.availabilities.removeAll { it.instructorName == username }
                    AppRepository.availabilities.add(Availability(instructorName = username, slots = slots))
                    onResult(true)
                } else onResult(false)
            } catch (e: Exception) { onResult(false) }
        }
    }

    fun sendNotification(recipientUsername: String, text: String) {
        viewModelScope.launch {
            try {
                RetrofitClient.instance.sendNotification(
                    token = authToken,
                    body = NotificationDto(recipientUsername = recipientUsername, text = text)
                )
            } catch (e: Exception) { Log.e("AppViewModel", "Send notification error", e) }
        }
    }

    fun refreshNotifications() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.getNotifications(authToken)
                if (response.isSuccessful) AppRepository.syncNotifications(response.body() ?: emptyList())
            } catch (e: Exception) { Log.e("AppViewModel", "Refresh notifications error", e) }
        }
    }

    fun loadMessages(withUser: String? = null, onResult: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.getMessages(token = authToken, otherUsername = withUser ?: "")
                if (response.isSuccessful) {
                    val dtos = response.body() ?: emptyList()
                    if (withUser != null) AppRepository.syncMessages(dtos, withUser)
                    else AppRepository.syncAllMessages(dtos)
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Load messages error", e)
            }
            onResult?.invoke()
        }
    }

    fun sendMessage(toUser: String, content: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.sendMessage(
                    token = authToken,
                    body = MessageDto(senderUsername = currentUserName, recipientUsername = toUser, content = content)
                )
                if (response.isSuccessful) {
                    response.body()?.let { dto ->
                        AppRepository.messages.add(
                            Message(sender = dto.senderUsername, recipient = dto.recipientUsername,
                                content = dto.content, isRead = dto.isRead,
                                timestamp = dto.createdAt ?: System.currentTimeMillis())
                        )
                    }
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                Log.e("AppViewModel", "Send message error", e)
                onResult(false)
            }
        }
    }

    fun logout() {
        isLoggedIn = false
        currentUserName = ""
        authToken = ""
        mustChangePassword = false
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USERNAME = "username"
        private const val KEY_ROLE = "user_role"
    }
}
