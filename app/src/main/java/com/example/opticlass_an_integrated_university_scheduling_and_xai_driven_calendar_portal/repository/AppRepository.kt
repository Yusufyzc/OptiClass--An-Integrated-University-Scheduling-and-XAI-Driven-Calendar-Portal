package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.ClassroomDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.UserDto

object AppRepository {
    // API'den dinamik olarak yüklenecekler
    val users = mutableStateListOf<User>()
    val classrooms = mutableStateListOf<Classroom>()
    
    val messages = mutableStateListOf<Message>()
    val notifications = mutableStateListOf<AppNotification>()
    val availabilities = mutableStateListOf<Availability>()
    val courseImports = mutableStateListOf<CourseImport>()
    val availabilityDrafts = mutableStateMapOf<String, Map<String, Set<String>>>()
    val scheduleHistory = mutableStateListOf<ScheduleChange>()

    fun syncUsers(userDtos: List<UserDto>) {
        users.clear()
        userDtos.forEach { dto ->
            users.add(User(
                username = dto.username,
                password = "", 
                role = if (dto.role == "ADMIN") UserRole.ADMIN else UserRole.INSTRUCTOR,
                fullName = dto.fullName,
                email = dto.email ?: "",
                department = dto.department ?: ""
            ))
        }
    }

    fun syncClassrooms(classroomDtos: List<ClassroomDto>) {
        classrooms.clear()
        classroomDtos.forEach { dto ->
            classrooms.add(Classroom(
                id = dto.id,
                roomCode = dto.roomCode,
                capacity = dto.capacity,
                department = ""
            ))
        }
    }
}
