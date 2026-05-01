package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ==========================================
    // AUTH & HEALTH
    // ==========================================
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("/")
    suspend fun healthCheck(): Response<Map<String, String>>

    // ==========================================
    // USERS (CRUD) & SETTINGS
    // ==========================================
    @GET("users")
    suspend fun getUsers(@Header("Authorization") token: String): Response<List<UserDto>>

    // DİKKAT: Yeni kullanıcı eklerken şifre gerektiği için UserCreateDto kullanılır
    @POST("users")
    suspend fun addUser(@Header("Authorization") token: String, @Body user: UserCreateDto): Response<UserDto>

    @PUT("users/{username}")
    suspend fun updateUser(
        @Header("Authorization") token: String,
        @Path("username") username: String,
        @Body user: UserUpdateDto
    ): Response<UserDto>

    @DELETE("users/{username}")
    suspend fun deleteUser(
        @Header("Authorization") token: String,
        @Path("username") username: String
    ): Response<Unit>

    @PUT("users/{username}/password")
    suspend fun changePassword(
        @Header("Authorization") token: String,
        @Path("username") username: String,
        @Body request: ChangePasswordRequest
    ): Response<Unit>

    // ==========================================
    // CLASSROOMS
    // ==========================================
    @GET("classrooms")
    suspend fun getClassrooms(@Header("Authorization") token: String): Response<List<ClassroomDto>>

    @POST("classrooms")
    suspend fun addClassroom(
        @Header("Authorization") token: String,
        @Body classroom: ClassroomDto
    ): Response<ClassroomDto>

    // ==========================================
    // COURSES
    // ==========================================
    @GET("courses")
    suspend fun getCourses(@Header("Authorization") token: String): Response<List<CourseDto>>

    @POST("courses/bulk")
    suspend fun importCourses(
        @Header("Authorization") token: String,
        @Body courses: List<CourseDto>
    ): Response<Unit>

    @DELETE("courses/{code}")
    suspend fun deleteCourse(
        @Header("Authorization") token: String,
        @Path("code") code: String
    ): Response<Unit>

    // ==========================================
    // AVAILABILITIES
    // ==========================================
    @GET("availabilities/{username}")
    suspend fun getAvailability(
        @Header("Authorization") token: String,
        @Path("username") username: String
    ): Response<AvailabilityDto>

    @PUT("availabilities/{username}")
    suspend fun submitAvailability(
        @Header("Authorization") token: String,
        @Path("username") username: String,
        @Body body: AvailabilityDto
    ): Response<Unit>

    // ==========================================
    // SCHEDULES & HISTORY
    // ==========================================
    @GET("schedules/{username}")
    suspend fun getSchedule(
        @Header("Authorization") token: String,
        @Path("username") username: String
    ): Response<ScheduleDto>

    @PUT("schedules/{username}")
    suspend fun updateSchedule(
        @Header("Authorization") token: String,
        @Path("username") username: String,
        @Body body: ScheduleDto
    ): Response<Unit>

    @GET("history")
    suspend fun getHistory(@Header("Authorization") token: String): Response<List<ScheduleHistoryDto>>

    @POST("history")
    suspend fun addHistory(@Header("Authorization") token: String, @Body body: ScheduleHistoryDto): Response<Unit>

    // ==========================================
    // MESSAGES
    // ==========================================
    @GET("messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Query("with_user") otherUsername: String // Python'daki with_user parametresi ile uyumlu
    ): Response<List<MessageDto>>

    @POST("messages")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body body: MessageDto
    ): Response<MessageDto>

    @PATCH("messages/{id}/read")
    suspend fun markMessageRead(
        @Header("Authorization") token: String,
        @Path("id") id: Int
    ): Response<Unit>

    // ==========================================
    // NOTIFICATIONS
    // ==========================================
    @GET("notifications")
    suspend fun getNotifications(@Header("Authorization") token: String): Response<List<NotificationDto>>

    @POST("notifications")
    suspend fun sendNotification(
        @Header("Authorization") token: String,
        @Body body: NotificationDto
    ): Response<Unit>
}

// ==============================================================================
// DATA TRANSFER OBJECTS (DTOs)
// ==============================================================================

data class LoginRequest(
    val username: String,
    val passwordHash: String
)

data class LoginResponse(
    val token: String,
    val role: String,
    val username: String,
    val mustChangePassword: Boolean
)

// Kullanıcı okuma işlemlerinde şifre gelmez
data class UserDto(
    val username: String,
    val role: String,
    val fullName: String,
    val email: String?,
    val department: String?
)

// Kullanıcı oluştururken şifre hash'i gereklidir
data class UserCreateDto(
    val username: String,
    val passwordHash: String,
    val role: String,
    val fullName: String,
    val email: String?,
    val department: String?
)

// Kullanıcı güncellerken şifre harici alanlar
data class UserUpdateDto(
    val role: String,
    val fullName: String,
    val email: String?,
    val department: String?
)

data class ChangePasswordRequest(
    val currentPasswordHash: String,
    val newPasswordHash: String
)

data class ClassroomDto(
    val id: String,
    val roomCode: String,
    val capacity: Int
)

data class CourseDto(
    val code: String,
    val name: String,
    val lecturerUsername: String?,
    val department: String,
    val email: String,
    val duration: Int,
    val classroomId: String?
)

data class AvailabilityDto(
    val instructorUsername: String,
    val slots: Map<String, List<String>>
)

data class ScheduleDto(
    val instructorUsername: String,
    val slots: Map<String, CourseDto?>
)

data class ScheduleHistoryDto(
    val changedBy: String,
    val instructorUsername: String,
    val instructorFullName: String,
    val day: String,
    val timeSlot: String,
    val previousCourseCode: String?,
    val newCourseCode: String?
)

data class MessageDto(
    val id: Int? = null,
    val senderUsername: String,
    val recipientUsername: String,
    val content: String,
    val isRead: Boolean = false,
    val createdAt: Long? = null
)

data class NotificationDto(
    val id: String? = null,
    val recipientUsername: String,
    val text: String,
    val isRead: Boolean = false
)
