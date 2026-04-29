package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf

val globalMessages = mutableStateListOf<Message>()
val globalNotifications = mutableStateListOf<AppNotification>()
val globalAvailabilities = mutableStateListOf<Availability>()
val globalCourseImports = mutableStateListOf<CourseImport>()
val globalUsers = mutableStateListOf(
    User(encodeUsername("admin"), sha256("admin123"), UserRole.ADMIN, "System Admin", "admin@opticlass.com"),
    User(encodeUsername("berkay"), sha256("berkay123"), UserRole.INSTRUCTOR, "Berkay", "berkay@example.com", mustChangePassword = true)
)
val globalClassrooms = mutableStateListOf<Classroom>()
val globalAvailabilityDrafts = mutableStateMapOf<String, Map<String, Set<String>>>()
val globalScheduleHistory = mutableStateListOf<ScheduleChange>()
