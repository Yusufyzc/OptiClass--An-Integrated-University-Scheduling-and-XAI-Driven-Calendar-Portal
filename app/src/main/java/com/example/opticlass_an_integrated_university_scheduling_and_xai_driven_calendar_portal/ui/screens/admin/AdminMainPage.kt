package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AdminMainPage(adminName: String) {
    var selectedUser by remember { mutableStateOf<String?>(null) }
    var showNewChatDialog by remember { mutableStateOf(false) }

    val conversationUsers = globalMessages
        .filter { it.recipient == adminName || it.sender == adminName }
        .map { if (it.sender == adminName) it.recipient else it.sender }
        .distinct()
        .filter { it != adminName }

    val unassignedInstructors = globalUsers.filter { user ->
        user.role == UserRole.INSTRUCTOR &&
        user.schedule.values.all { day -> day.values.all { it == null } }
    }
    val assignedCourseCodes = globalUsers.flatMap { user ->
        user.schedule.values.flatMap { day -> day.values.filterNotNull().map { it.code } }
    }.toSet()
    val allInstructorCourses = globalUsers.filter { it.role == UserRole.INSTRUCTOR }.flatMap { it.courses }
    val unassignedCourses = allInstructorCourses.filter { it.code !in assignedCourseCodes }
    val bookedSlotsPerRoom = globalUsers.flatMap { user ->
        user.schedule.entries.flatMap { (day, dayMap) ->
            dayMap.entries.mapNotNull { (slot, course) ->
                course?.classroomId?.let { Triple(it, day, slot) }
            }
        }
    }.groupBy { it.first }
    val availableClassrooms = globalClassrooms.filter { room ->
        (bookedSlotsPerRoom[room.id]?.size ?: 0) < DAYS.size * TIME_SLOTS.size
    }

    var showUnassignedInstructors by remember { mutableStateOf(false) }
    var showUnassignedCourses by remember { mutableStateOf(false) }
    var showAvailableClassrooms by remember { mutableStateOf(false) }

    if (selectedUser == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Inbox", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Unassigned\nInstructors",
                        value = unassignedInstructors.size.toString(),
                        icon = Icons.Default.Person,
                        onClick = { showUnassignedInstructors = true }
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Unassigned\nCourses",
                        value = unassignedCourses.size.toString(),
                        icon = Icons.Default.Menu,
                        onClick = { showUnassignedCourses = true }
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Available\nClassrooms",
                        value = availableClassrooms.size.toString(),
                        icon = Icons.Default.School,
                        onClick = { showAvailableClassrooms = true }
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (conversationUsers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No conversations yet.\nTap + to start a new chat.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray)
                    }
                } else {
                    LazyColumn {
                        items(conversationUsers) { user ->
                            val unread = globalMessages.count { it.sender == user && it.recipient == adminName && !it.isRead }
                            val instructor = globalUsers.find { it.username == user }
                            val lastMsg = globalMessages
                                .filter {
                                    (it.sender == user && it.recipient == adminName) ||
                                    (it.sender == adminName && it.recipient == user)
                                }
                                .maxByOrNull { it.timestamp }
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    .clickable { selectedUser = user }
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    UserAvatar(
                                        fullName = instructor?.fullName ?: user,
                                        username = user,
                                        avatarUri = instructor?.avatarUri
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(instructor?.fullName ?: user, fontWeight = FontWeight.Medium)
                                        if (lastMsg != null) {
                                            Text(
                                                lastMsg.content,
                                                fontSize = 12.sp,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else {
                                            Text("@${decodeUsername(user)}", fontSize = 12.sp, color = Color.Gray)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (unread > 0) Badge { Text(unread.toString()) }
                                        if (lastMsg != null) {
                                            Text(formatTimestamp(lastMsg.timestamp), fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { showNewChatDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Chat", tint = Color.White)
            }
        }

        if (showUnassignedInstructors) {
            AlertDialog(
                onDismissRequest = { showUnassignedInstructors = false },
                title = { Text("Unassigned Instructors") },
                text = {
                    if (unassignedInstructors.isEmpty()) {
                        Text("All instructors have at least one course assigned.")
                    } else {
                        LazyColumn {
                            items(unassignedInstructors) { u ->
                                Text("• ${u.fullName}", modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showUnassignedInstructors = false }) { Text("Close") } }
            )
        }

        if (showUnassignedCourses) {
            AlertDialog(
                onDismissRequest = { showUnassignedCourses = false },
                title = { Text("Unassigned Courses") },
                text = {
                    if (unassignedCourses.isEmpty()) {
                        Text("All courses have been assigned to a slot.")
                    } else {
                        LazyColumn {
                            items(unassignedCourses) { c ->
                                Text("• ${c.code} — ${c.name}", modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showUnassignedCourses = false }) { Text("Close") } }
            )
        }

        if (showAvailableClassrooms) {
            AlertDialog(
                onDismissRequest = { showAvailableClassrooms = false },
                title = { Text("Available Classrooms") },
                text = {
                    if (availableClassrooms.isEmpty()) {
                        Text("No classrooms imported yet.")
                    } else {
                        LazyColumn {
                            items(availableClassrooms) { room ->
                                val booked = bookedSlotsPerRoom[room.id]?.size ?: 0
                                val total = DAYS.size * TIME_SLOTS.size
                                Text("• ${room.roomCode} (${total - booked}/$total free)", modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showAvailableClassrooms = false }) { Text("Close") } }
            )
        }

        if (showNewChatDialog) {
            val instructors = globalUsers.filter { it.role == UserRole.INSTRUCTOR && it.username !in conversationUsers }
            AlertDialog(
                onDismissRequest = { showNewChatDialog = false },
                title = { Text("New Conversation") },
                text = {
                    if (instructors.isEmpty()) {
                        Text("All instructors already have an active conversation.")
                    } else {
                        LazyColumn {
                            items(instructors) { instructor ->
                                TextButton(
                                    onClick = {
                                        selectedUser = instructor.username
                                        showNewChatDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(instructor.fullName, fontWeight = FontWeight.Medium)
                                        Text("@${decodeUsername(instructor.username)}", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNewChatDialog = false }) { Text("Cancel") }
                }
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            TextButton(onClick = { selectedUser = null }) { Text("< Back") }
            ChatBox(adminName, selectedUser!!)
        }
    }
}
