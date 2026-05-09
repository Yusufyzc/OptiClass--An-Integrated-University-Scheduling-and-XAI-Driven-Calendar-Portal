package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InstructorMainPage(userName: String, onNavigate: (AppDestinations) -> Unit = {}, onShowNotifications: () -> Unit = {}, onRefresh: () -> Unit = {}) {
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            onRefresh()
        }
    }
    val user = AppRepository.users.find { it.username == userName } ?: return
    val unreadMessages = AppRepository.messages.count { it.recipient == userName && !it.isRead }
    val unreadNotifs = AppRepository.notifications.count { !it.isRead && it.recipientName == userName }
    val availabilitySubmitted = AppRepository.availabilities.any { it.instructorName == userName }

    val dayMap = mapOf(2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri")
    val todayKey = dayMap[java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)]
    val todayClasses = if (todayKey != null) {
        TIME_SLOTS.mapNotNull { slot ->
            val course = user.schedule[todayKey]?.get(slot)
            if (course != null && course.duration != -1) slot to course else null
        }
    } else emptyList()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Welcome, ${user.fullName}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        val dept = user.department.ifBlank { user.courses.firstOrNull()?.department.orEmpty() }
        if (dept.isNotBlank()) {
            Text(dept, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Courses",
                value = user.courses.size.toString(),
                icon = Icons.Default.Menu,
                onClick = { onNavigate(AppDestinations.MY_LECTURES) }
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Messages",
                value = unreadMessages.toString(),
                icon = Icons.Default.Email,
                onClick = { onNavigate(AppDestinations.NOTIFICATIONS) }
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Alerts",
                value = unreadNotifs.toString(),
                icon = Icons.Default.Notifications,
                onClick = onShowNotifications
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (availabilitySubmitted)
                    Color.Green.copy(alpha = 0.1f)
                else
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            )
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (availabilitySubmitted) Icons.Default.Check else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (availabilitySubmitted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (availabilitySubmitted) "Availability submitted to admin"
                    else "Availability not submitted yet — go to 'My Availability'",
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Text("Today's Classes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (todayClasses.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (todayKey == null) "No classes on weekends" else "No classes scheduled for today",
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(todayClasses) { (slot, course) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(slot, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(80.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(course.code, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(course.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            }
                            val roomCode = course.classroomId?.let { id ->
                                AppRepository.classrooms.find { it.id == id }?.roomCode
                            }
                            if (roomCode != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(roomCode, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
