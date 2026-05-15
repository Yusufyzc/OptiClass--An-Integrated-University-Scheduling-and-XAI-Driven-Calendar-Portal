package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.AvailabilityDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.ClassroomDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.CourseDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.MessageDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.NotificationDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.ScheduleDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.ScheduleHistoryDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.UserDto

object AppRepository {
    val users = mutableStateListOf<User>()
    val messages = mutableStateListOf<Message>()
    private val localReadTimestamps = mutableSetOf<Long>()
    val notifications = mutableStateListOf<AppNotification>()
    val availabilities = mutableStateListOf<Availability>()
    val courseImports = mutableStateListOf<CourseImport>()
    val classrooms = mutableStateListOf<Classroom>()
    val availabilityDrafts = mutableStateMapOf<String, Map<String, Set<String>>>()
    val scheduleHistory = mutableStateListOf<ScheduleChange>()
    var schedulingPhase by mutableStateOf("PHASE_1")
    val commonCourseSlots = mutableStateMapOf<String, Set<String>>()

    fun recomputeCommonCourseSlots() {
        val result = mutableMapOf<String, MutableSet<String>>()
        users.forEach { user ->
            DAYS.forEach { day ->
                user.schedule[day]?.forEach { (slot, course) ->
                    if (course != null && course.department == "COMMON") {
                        result.getOrPut(day) { mutableSetOf() }.add(slot)
                    }
                }
            }
        }
        commonCourseSlots.clear()
        result.forEach { (day, slots) -> commonCourseSlots[day] = slots }
    }

    fun syncUsers(userDtos: List<UserDto>) {
        users.clear()
        userDtos.forEach { dto ->
            users.add(User(
                username = dto.username,
                password = "",
                role = if (dto.role == "ADMIN") UserRole.ADMIN else UserRole.INSTRUCTOR,
                fullName = dto.fullName,
                email = dto.email ?: "",
                department = dto.department ?: "",
                avatarUri = dto.avatarUrl
            ))
        }
    }

    fun syncHistory(dtos: List<ScheduleHistoryDto>) {
        scheduleHistory.clear()
        dtos.forEach { dto ->
            scheduleHistory.add(ScheduleChange(
                changedBy = dto.changedBy,
                timestamp = dto.changedAt ?: System.currentTimeMillis(),
                instructorUsername = dto.instructorUsername,
                instructorFullName = dto.instructorFullName,
                day = dto.day,
                timeSlot = dto.timeSlot,
                previousCourse = dto.previousCourseCode?.let { CourseImport(code = it, name = "", lecturer = "", department = "", email = "") },
                newCourse = dto.newCourseCode?.let { CourseImport(code = it, name = "", lecturer = "", department = "", email = "") }
            ))
        }
    }

    fun syncClassrooms(classroomDtos: List<ClassroomDto>) {
        classrooms.clear()
        classroomDtos.forEach { dto ->
            classrooms.add(Classroom(id = dto.id, roomCode = dto.roomCode, capacity = dto.capacity))
        }
    }

    fun markMessageRead(timestamp: Long) {
        localReadTimestamps.add(timestamp)
        val idx = messages.indexOfFirst { it.timestamp == timestamp }
        if (idx != -1) messages[idx] = messages[idx].copy(isRead = true)
    }

    fun markAllMessagesReadFrom(sender: String) {
        messages.indices
            .filter { messages[it].sender == sender && !messages[it].isRead }
            .forEach { messages[it] = messages[it].copy(isRead = true) }
    }

    fun syncMessages(messageDtos: List<MessageDto>, withUser: String) {
        messages.removeAll { m -> (m.sender == withUser || m.recipient == withUser) }
        messageDtos.forEach { dto ->
            val ts = dto.createdAt ?: System.currentTimeMillis()
            messages.add(Message(
                sender = dto.senderUsername,
                recipient = dto.recipientUsername,
                content = dto.content,
                isRead = dto.isRead || localReadTimestamps.contains(ts),
                timestamp = ts
            ))
        }
    }

    fun syncAllMessages(messageDtos: List<MessageDto>) {
        messages.clear()
        messageDtos.forEach { dto ->
            val ts = dto.createdAt ?: System.currentTimeMillis()
            messages.add(Message(
                sender = dto.senderUsername,
                recipient = dto.recipientUsername,
                content = dto.content,
                isRead = dto.isRead || localReadTimestamps.contains(ts),
                timestamp = ts
            ))
        }
    }

    fun syncCourses(courseDtos: List<CourseDto>) {
        courseImports.clear()
        val coursesByLecturer = mutableMapOf<String, MutableList<CourseImport>>()

        courseDtos.forEach { dto ->
            if (dto.duration != -1) {
                val course = CourseImport(
                    code = dto.code,
                    name = dto.name,
                    lecturer = dto.lecturerUsername ?: "",
                    department = dto.department,
                    email = dto.email,
                    duration = dto.duration,
                    classroomId = dto.classroomId,
                    semester = dto.semester,
                    studentCount = dto.studentCount,
                    priority = dto.priority
                )
                courseImports.add(course)
                dto.lecturerUsername?.let { username ->
                    coursesByLecturer.getOrPut(username) { mutableListOf() }.add(course)
                }
            }
        }

        users.forEachIndexed { index, user ->
            val userCourses = coursesByLecturer[user.username] ?: mutableListOf()
            users[index] = user.copy(courses = userCourses)
        }
    }

    fun syncSchedule(username: String, flatSlots: Map<String, CourseDto?>) {
        val index = users.indexOfFirst { it.username == username }
        if (index == -1) return
        val newSchedule = mutableStateMapOf<String, SnapshotStateMap<String, CourseImport?>>()
        DAYS.forEach { day ->
            val inner = mutableStateMapOf<String, CourseImport?>()
            TIME_SLOTS.forEach { slot -> inner[slot] = null }
            newSchedule[day] = inner
        }
        flatSlots.forEach { (key, dto) ->
            val underscoreIdx = key.indexOf('_')
            if (underscoreIdx > 0) {
                val day = key.substring(0, underscoreIdx)
                val slot = key.substring(underscoreIdx + 1)
                if (day in DAYS && slot in TIME_SLOTS) {
                    newSchedule[day]?.set(slot, dto?.let { d ->
                        CourseImport(code = d.code, name = d.name, lecturer = d.lecturerUsername ?: "",
                            department = d.department, email = d.email, duration = d.duration,
                            classroomId = d.classroomId, semester = d.semester, studentCount = d.studentCount,
                            priority = d.priority)
                    })
                }
            }
        }
        users[index] = users[index].copy(schedule = newSchedule)
        recomputeCommonCourseSlots()
    }

    fun syncAvailabilities(dtos: List<AvailabilityDto>) {
        availabilities.clear()
        dtos.forEach { dto ->
            if (dto.slots.isNotEmpty()) {
                availabilities.add(Availability(
                    instructorName = dto.instructorUsername,
                    slots = dto.slots.mapValues { it.value.toSet() }
                ))
            }
        }
    }

    fun syncNotifications(dtos: List<NotificationDto>) {
        notifications.clear()
        dtos.forEach { dto ->
            notifications.add(AppNotification(
                id = dto.id ?: System.currentTimeMillis().toString(),
                text = dto.text,
                isRead = dto.isRead,
                recipientName = dto.recipientUsername
            ))
        }
    }
}
