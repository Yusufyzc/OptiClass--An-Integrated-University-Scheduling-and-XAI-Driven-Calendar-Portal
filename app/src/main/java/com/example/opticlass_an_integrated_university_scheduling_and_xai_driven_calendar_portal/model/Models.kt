package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.vector.ImageVector

data class Message(
    val sender: String,
    val recipient: String,
    val content: String,
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class AppNotification(
    val id: String,
    val text: String,
    val isRead: Boolean = false,
    val recipientName: String = ""
)

data class Availability(
    val instructorName: String,
    val slots: Map<String, Set<String>>
)

data class CourseImport(
    val code: String,
    val name: String,
    val lecturer: String,
    val department: String,
    val email: String,
    val duration: Int = 1,  // 1-3 hours; -1 = continuation slot marker
    val classroomId: String? = null
)

data class Classroom(
    val id: String,
    val roomCode: String,
    val capacity: Int
)

data class ScheduleChange(
    val changedBy: String,
    val timestamp: Long = System.currentTimeMillis(),
    val instructorUsername: String,
    val instructorFullName: String,
    val day: String,
    val timeSlot: String,
    val previousCourse: CourseImport?,
    val newCourse: CourseImport?
)

data class User(
    val username: String,          // Base64 encoded
    val password: String,          // SHA-256 hash
    val role: UserRole,
    val fullName: String,
    val email: String,
    val avatarUri: String? = null,
    val department: String = "",
    val mustChangePassword: Boolean = false,
    val courses: MutableList<CourseImport> = mutableListOf(),
    val schedule: SnapshotStateMap<String, SnapshotStateMap<String, CourseImport?>> = mutableStateMapOf()
)

enum class UserRole {
    ADMIN, INSTRUCTOR
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
    val roleRestriction: UserRole? = null,
    val showInSidebar: Boolean = true
) {
    MAIN_PAGE("Main Page", Icons.Default.Home),
    MY_SCHEDULE("My Schedule", Icons.Default.DateRange, roleRestriction = UserRole.INSTRUCTOR),
    MY_LECTURES("My Lectures", Icons.Default.Menu, roleRestriction = UserRole.INSTRUCTOR),
    MY_AVAILABILITY("My Availability", Icons.Default.Edit, roleRestriction = UserRole.INSTRUCTOR),
    NOTIFICATIONS("Notifications", Icons.Default.Notifications, roleRestriction = UserRole.INSTRUCTOR, showInSidebar = false),
    DATA_IMPORT("Data Import", Icons.Default.KeyboardArrowUp, roleRestriction = UserRole.ADMIN),
    USER_TRANSACTIONS("User Transactions", Icons.Default.Person, roleRestriction = UserRole.ADMIN),
    UPDATE_CALENDAR("Update Calendar", Icons.Default.Edit, roleRestriction = UserRole.ADMIN),
    INSTRUCTOR_AVAILABILITY("Instructor Availability", Icons.AutoMirrored.Filled.List, roleRestriction = UserRole.ADMIN),
    CLASSROOMS("Classrooms", Icons.Default.School, roleRestriction = UserRole.ADMIN),
    SETTINGS("Settings", Icons.Default.Settings),
}
