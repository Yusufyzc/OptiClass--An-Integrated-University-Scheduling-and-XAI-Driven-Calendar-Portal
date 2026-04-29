package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf

object AppRepository {
    val users = mutableStateListOf(
        User(encodeUsername("admin"), sha256("admin123"), UserRole.ADMIN, "System Admin", "admin@opticlass.com"),
        User(encodeUsername("berkay"), sha256("berkay123"), UserRole.INSTRUCTOR, "Berkay", "berkay@example.com", mustChangePassword = true)
    )
    val messages = mutableStateListOf<Message>()
    val notifications = mutableStateListOf<AppNotification>()
    val availabilities = mutableStateListOf<Availability>()
    val courseImports = mutableStateListOf<CourseImport>()
    val classrooms = mutableStateListOf<Classroom>()
    val availabilityDrafts = mutableStateMapOf<String, Map<String, Set<String>>>()
    val scheduleHistory = mutableStateListOf<ScheduleChange>()
}
