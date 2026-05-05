package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("/")
    suspend fun healthCheck(): Response<Map<String, String>>

    @GET("users")
    suspend fun getUsers(@Header("Authorization") token: String): Response<List<UserDto>>

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

    @PUT("users/{username}/avatar")
    suspend fun updateAvatar(
        @Header("Authorization") token: String,
        @Path("username") username: String,
        @Body body: AvatarUpdateDto
    ): Response<Unit>

    @PUT("users/{username}/reset-password")
    suspend fun adminResetPassword(
        @Header("Authorization") token: String,
        @Path("username") username: String,
        @Body request: AdminResetPasswordRequest
    ): Response<Unit>

    @GET("classrooms")
    suspend fun getClassrooms(@Header("Authorization") token: String): Response<List<ClassroomDto>>

    @POST("classrooms")
    suspend fun addClassroom(
        @Header("Authorization") token: String,
        @Body classroom: ClassroomDto
    ): Response<ClassroomDto>

    @DELETE("classrooms/{id}")
    suspend fun deleteClassroom(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Unit>

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

    @GET("availabilities")
    suspend fun getAllAvailabilities(@Header("Authorization") token: String): Response<List<AvailabilityDto>>

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

    @GET("schedules")
    suspend fun getAllSchedules(@Header("Authorization") token: String): Response<List<ScheduleDto>>

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

    @GET("messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Query("with_user") otherUsername: String? = null
    ): Response<List<MessageDto>>

    @POST("messages")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body body: MessageDto
    ): Response<MessageDto>

    @PATCH("messages/mark-read")
    suspend fun markMessagesRead(
        @Header("Authorization") token: String,
        @Query("sender") sender: String
    ): Response<Unit>

    @GET("notifications")
    suspend fun getNotifications(@Header("Authorization") token: String): Response<List<NotificationDto>>

    @POST("notifications")
    suspend fun sendNotification(
        @Header("Authorization") token: String,
        @Body body: NotificationDto
    ): Response<Unit>

    @PATCH("notifications/{id}/read")
    suspend fun markNotificationRead(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Unit>
}

data class LoginRequest(val username: String, val passwordHash: String)

data class LoginResponse(
    val token: String,
    val role: String,
    val username: String,
    val mustChangePassword: Boolean
)

data class UserDto(
    val username: String,
    val role: String,
    val fullName: String,
    val email: String?,
    val department: String?,
    val avatarUrl: String? = null
)

data class UserCreateDto(
    val username: String,
    val passwordHash: String,
    val role: String,
    val fullName: String,
    val email: String?,
    val department: String?
)

data class UserUpdateDto(
    val role: String,
    val fullName: String,
    val email: String?,
    val department: String?
)

data class ChangePasswordRequest(val currentPasswordHash: String, val newPasswordHash: String)

data class AdminResetPasswordRequest(val newPasswordHash: String)

data class AvatarUpdateDto(val avatarUrl: String)

data class ClassroomDto(val id: String, val roomCode: String, val capacity: Int)

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
    val newCourseCode: String?,
    val changedAt: Long? = null
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
