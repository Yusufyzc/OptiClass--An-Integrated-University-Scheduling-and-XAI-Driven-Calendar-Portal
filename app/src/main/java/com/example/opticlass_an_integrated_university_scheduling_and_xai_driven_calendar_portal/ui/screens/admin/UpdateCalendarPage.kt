package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCalendarPage(snackbarHostState: SnackbarHostState, currentAdminUser: String) {
    var expanded by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<User?>(null) }
    val instructors = AppRepository.users.filter { it.role == UserRole.INSTRUCTOR }
    val scope = rememberCoroutineScope()
    var isDirty by remember { mutableStateOf(false) }
    var pendingUserSelect by remember { mutableStateOf<User?>(null) }

    var selectedCourseToAssign by remember { mutableStateOf<CourseImport?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedDuration by remember { mutableIntStateOf(1) }
    var selectedClassroom by remember { mutableStateOf<Classroom?>(null) }
    var classroomDropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Update Calendar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { showHistoryDialog = true }) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("History")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text("Select Instructor:", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedUser?.fullName ?: "Select an instructor",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                instructors.forEach { user ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(user.fullName, modifier = Modifier.weight(1f))
                                val hasAvailability = AppRepository.availabilities.any { it.instructorName == user.username }
                                Surface(
                                    color = if (hasAvailability) Color.Green.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        if (hasAvailability) "Avail. ✓" else "No avail.",
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = if (hasAvailability) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        onClick = {
                            if (isDirty && selectedUser != null && selectedUser != user) {
                                pendingUserSelect = user
                                expanded = false
                            } else {
                                selectedUser = user
                                expanded = false
                                selectedCourseToAssign = null
                                selectedClassroom = null
                                isDirty = false
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedUser != null) {
            val user = selectedUser!!

            val availability = AppRepository.availabilities.find { it.instructorName == user.username }
            val availableSlots = availability?.slots ?: emptyMap()

            val draftSchedule = remember(user.username) {
                val map = mutableStateMapOf<String, SnapshotStateMap<String, CourseImport?>>()
                DAYS.forEach { day ->
                    val innerMap = mutableStateMapOf<String, CourseImport?>()
                    TIME_SLOTS.forEach { slot -> innerMap[slot] = user.schedule[day]?.get(slot) }
                    map[day] = innerMap
                }
                map
            }

            Text("Assigned Courses (Tap to select, then tap grid to assign):", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(user.courses) { course ->
                    CourseItemSelectable(
                        course = course,
                        isSelected = selectedCourseToAssign == course,
                        onClick = { selectedCourseToAssign = if (selectedCourseToAssign == course) null else course }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Scheduling Grid:", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            SchedulingGridEnhanced(
                DAYS, TIME_SLOTS, availableSlots, draftSchedule, selectedCourseToAssign,
                onCellClick = { day, slot ->
                    if (selectedCourseToAssign != null) {
                        showAssignDialog = day to slot
                        selectedDuration = 1
                    }
                },
                onSlotCleared = { isDirty = true }
            )

            if (showAssignDialog != null) {
                val (reqDay, reqSlot) = showAssignDialog!!
                val startIdx = TIME_SLOTS.indexOf(reqSlot)
                val targetSlots = (0 until selectedDuration).map { TIME_SLOTS.getOrNull(startIdx + it) }
                val outOfBounds = targetSlots.any { it == null }
                val validSlots = targetSlots.filterNotNull()
                val conflictSlots = validSlots.drop(1).filter { s ->
                    val existing = draftSchedule[reqDay]?.get(s)
                    existing != null && existing.duration != -1
                }
                val unavailableSlots = validSlots.filter { s ->
                    availableSlots[reqDay]?.contains(s) != true
                }
                val classroomConflict = selectedClassroom != null && validSlots.any { s ->
                    AppRepository.users.any { u ->
                        u.schedule[reqDay]?.get(s)?.classroomId == selectedClassroom!!.id
                    }
                }

                AlertDialog(
                    onDismissRequest = { showAssignDialog = null },
                    title = { Text("Assign: ${selectedCourseToAssign?.code}") },
                    text = {
                        Column {
                            Text("Slot: $reqDay $reqSlot", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Duration (hours):", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 2, 3).forEach { d ->
                                    FilterChip(
                                        selected = selectedDuration == d,
                                        onClick = { selectedDuration = d },
                                        label = { Text("${d}s") }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Classroom (optional):", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            ExposedDropdownMenuBox(
                                expanded = classroomDropdownExpanded,
                                onExpandedChange = { classroomDropdownExpanded = !classroomDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedClassroom?.roomCode ?: "No classroom",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classroomDropdownExpanded) },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    isError = classroomConflict
                                )
                                ExposedDropdownMenu(
                                    expanded = classroomDropdownExpanded,
                                    onDismissRequest = { classroomDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("No classroom") },
                                        onClick = { selectedClassroom = null; classroomDropdownExpanded = false }
                                    )
                                    AppRepository.classrooms.forEach { room ->
                                        DropdownMenuItem(
                                            text = { Text(room.roomCode) },
                                            onClick = { selectedClassroom = room; classroomDropdownExpanded = false }
                                        )
                                    }
                                }
                            }
                            if (classroomConflict) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "${selectedClassroom!!.roomCode} is already booked at this time.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (outOfBounds) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Not enough slots for a ${selectedDuration}-hour block starting at this time.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (unavailableSlots.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Instructor not available at: ${unavailableSlots.joinToString(", ")}",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (conflictSlots.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Already occupied: ${conflictSlots.joinToString(", ")}. Assign anyway?",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !outOfBounds && !classroomConflict,
                            onClick = {
                                val course = selectedCourseToAssign!!.copy(
                                    duration = selectedDuration,
                                    classroomId = selectedClassroom?.id
                                )
                                validSlots.forEachIndexed { index, s ->
                                    draftSchedule[reqDay]?.set(s, if (index == 0) course else course.copy(duration = -1))
                                }
                                isDirty = true
                                showAssignDialog = null
                            }
                        ) {
                            Text(if (conflictSlots.isNotEmpty() || unavailableSlots.isNotEmpty()) "Assign Anyway" else "Confirm")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAssignDialog = null }) { Text("Cancel") }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        DAYS.forEach { day -> TIME_SLOTS.forEach { slot -> draftSchedule[day]?.set(slot, null) } }
                        selectedCourseToAssign = null
                        isDirty = true
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Schedule")
                }
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        DAYS.forEach { day ->
                            TIME_SLOTS.forEach { slot ->
                                val prev = user.schedule[day]?.get(slot)
                                val next = draftSchedule[day]?.get(slot)
                                if (prev?.code != next?.code) {
                                    AppRepository.scheduleHistory.add(
                                        0,
                                        ScheduleChange(
                                            changedBy = currentAdminUser,
                                            timestamp = now,
                                            instructorUsername = user.username,
                                            instructorFullName = user.fullName,
                                            day = day,
                                            timeSlot = slot,
                                            previousCourse = prev,
                                            newCourse = next
                                        )
                                    )
                                }
                            }
                        }
                        user.schedule.clear()
                        draftSchedule.forEach { (day, slots) ->
                            val innerMap = mutableStateMapOf<String, CourseImport?>()
                            innerMap.putAll(slots)
                            user.schedule[day] = innerMap
                        }
                        isDirty = false
                        selectedCourseToAssign = null
                        AppRepository.notifications.add(
                            AppNotification(
                                id = System.currentTimeMillis().toString(),
                                text = "Admin updated your weekly schedule. Please check 'My Schedule'.",
                                recipientName = user.username
                            )
                        )
                        scope.launch { snackbarHostState.showSnackbar("Schedule updated and notification sent!") }
                    }
                ) {
                    Text("Save & Notify Instructor")
                }
            }
        }
    }

    if (pendingUserSelect != null) {
        AlertDialog(
            onDismissRequest = { pendingUserSelect = null },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes for ${selectedUser?.fullName}. Discard them and switch to ${pendingUserSelect!!.fullName}?") },
            confirmButton = {
                TextButton(onClick = {
                    selectedUser = pendingUserSelect
                    selectedCourseToAssign = null
                    isDirty = false
                    pendingUserSelect = null
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUserSelect = null }) { Text("Keep Editing") }
            }
        )
    }

    if (showHistoryDialog) {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Schedule History") },
            text = {
                if (AppRepository.scheduleHistory.isEmpty()) {
                    Text("No changes recorded yet.")
                } else {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(max = 400.dp)
                    ) {
                        AppRepository.scheduleHistory.forEach { change ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        "${sdf.format(Date(change.timestamp))} — ${change.changedBy}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${change.instructorFullName} · ${change.day} ${change.timeSlot}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val prevText = change.previousCourse?.code ?: "—"
                                    val nextText = change.newCourse?.code ?: "Removed"
                                    Text(
                                        "$prevText → $nextText",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (change.newCourse == null)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) { Text("Close") }
            },
            dismissButton = {
                if (AppRepository.scheduleHistory.isNotEmpty()) {
                    TextButton(
                        onClick = { AppRepository.scheduleHistory.clear(); showHistoryDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Clear History") }
                }
            }
        )
    }
}

@Composable
fun CourseItemSelectable(course: CourseImport, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(120.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(course.code, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isSelected) Color.White else Color.Unspecified)
            Text(course.name, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center, color = if (isSelected) Color.White else Color.Unspecified)
        }
    }
}

@Composable
fun SchedulingGridEnhanced(
    days: List<String>,
    timeSlots: List<String>,
    availableSlots: Map<String, Set<String>>,
    draftSchedule: MutableMap<String, SnapshotStateMap<String, CourseImport?>>,
    selectedCourse: CourseImport?,
    onCellClick: (String, String) -> Unit,
    onSlotCleared: () -> Unit = {}
) {
    val gridScrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(gridScrollState)) {
        Column(modifier = Modifier.border(1.dp, Color.LightGray)) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Box(modifier = Modifier.width(80.dp).padding(8.dp)) {
                    Text("Time", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.Gray))
                days.forEach { day ->
                    Box(modifier = Modifier.width(65.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                        Text(day, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
            Column {
                timeSlots.forEach { slot ->
                    Row(modifier = Modifier.height(IntrinsicSize.Min).border(0.5.dp, Color.LightGray)) {
                        Box(modifier = Modifier.width(80.dp).padding(8.dp).background(Color(0xFFF9F9F9))) {
                            Text(slot, fontSize = 9.sp)
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.LightGray))
                        days.forEach { day ->
                            val isAvailable = availableSlots[day]?.contains(slot) == true
                            val scheduledCourse = draftSchedule[day]?.get(slot)
                            val isContinuation = scheduledCourse?.duration == -1

                            Box(
                                modifier = Modifier
                                    .width(65.dp)
                                    .height(50.dp)
                                    .border(0.5.dp, Color.LightGray)
                                    .background(
                                        when {
                                            isContinuation -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            scheduledCourse != null -> MaterialTheme.colorScheme.primaryContainer
                                            isAvailable -> Color.Green.copy(alpha = 0.1f)
                                            else -> Color.Red.copy(alpha = 0.05f)
                                        }
                                    )
                                    .clickable(enabled = !isContinuation) { onCellClick(day, slot) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isContinuation) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.5f)
                                            .height(3.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    )
                                } else if (scheduledCourse != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            scheduledCourse.code,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        IconButton(
                                            onClick = {
                                                val slotIndex = timeSlots.indexOf(slot)
                                                draftSchedule[day]?.set(slot, null)
                                                for (i in 1 until scheduledCourse.duration) {
                                                    val nextIdx = slotIndex + i
                                                    if (nextIdx < timeSlots.size) {
                                                        val nextSlot = timeSlots[nextIdx]
                                                        if (draftSchedule[day]?.get(nextSlot)?.duration == -1) {
                                                            draftSchedule[day]?.set(nextSlot, null)
                                                        }
                                                    }
                                                }
                                                onSlotCleared()
                                            },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Clear, contentDescription = "Remove", tint = Color.Red)
                                        }
                                    }
                                } else if (!isAvailable) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
