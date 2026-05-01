package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCalendarPage(snackbarHostState: SnackbarHostState, currentAdminUser: String, viewModel: AppViewModel) {
    val scope = rememberCoroutineScope()

    // Genel Veriler
    var isLoading by remember { mutableStateOf(true) }
    var instructors by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var classrooms by remember { mutableStateOf<List<ClassroomDto>>(emptyList()) }
    var scheduleHistory by remember { mutableStateOf<List<ScheduleHistoryDto>>(emptyList()) }

    // Seçili Eğitmen Verileri
    var expanded by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<UserDto?>(null) }
    var instructorCourses by remember { mutableStateOf<List<CourseDto>>(emptyList()) }
    var availableSlots by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var isDirty by remember { mutableStateOf(false) }
    var pendingUserSelect by remember { mutableStateOf<UserDto?>(null) }

    // Grid State
    val draftSchedule = remember {
        val map = mutableStateMapOf<String, SnapshotStateMap<String, CourseDto?>>()
        DAYS.forEach { map[it] = mutableStateMapOf() }
        map
    }

    // UI Kontrolleri
    var selectedCourseToAssign by remember { mutableStateOf<CourseDto?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedDuration by remember { mutableIntStateOf(1) }
    var selectedClassroom by remember { mutableStateOf<ClassroomDto?>(null) }
    var classroomDropdownExpanded by remember { mutableStateOf(false) }

    // Sayfa açıldığında Eğitmenleri ve Sınıfları çek
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val token = "Bearer ${viewModel.authToken}"
            val usersRes = RetrofitClient.instance.getUsers(token)
            if (usersRes.isSuccessful) {
                instructors = usersRes.body()?.filter { it.role == "INSTRUCTOR" } ?: emptyList()
            }
            val classRes = RetrofitClient.instance.getClassrooms(token)
            if (classRes.isSuccessful) classrooms = classRes.body() ?: emptyList()
        } catch (e: Exception) { Log.e("UpdateCalendar", "Veri çekilemedi", e) }
        finally { isLoading = false }
    }

    // Eğitmen seçildiğinde ona ait verileri çek
    LaunchedEffect(selectedUser) {
        selectedUser?.let { user ->
            try {
                val token = "Bearer ${viewModel.authToken}"

                // 1. Eğitmenin derslerini çek
                val courseRes = RetrofitClient.instance.getCourses(token)
                if (courseRes.isSuccessful) {
                    instructorCourses = courseRes.body()?.filter { it.lecturerUsername == user.username } ?: emptyList()
                }

                // 2. Eğitmenin müsaitliğini çek
                val availRes = RetrofitClient.instance.getAvailability(token, user.username)
                if (availRes.isSuccessful) availableSlots = availRes.body()?.slots ?: emptyMap()

                // 3. Eğitmenin mevcut programını çek
                val schedRes = RetrofitClient.instance.getSchedule(token, user.username)

                // Grid'i temizle
                DAYS.forEach { day -> TIME_SLOTS.forEach { slot -> draftSchedule[day]?.set(slot, null) } }

                if (schedRes.isSuccessful) {
                    val savedSlots = schedRes.body()?.slots ?: emptyMap()
                    savedSlots.forEach { (key, course) ->
                        val parts = key.split("_")
                        if (parts.size == 2) {
                            draftSchedule[parts[0]]?.set(parts[1], course)
                        }
                    }
                }
                isDirty = false
            } catch (e: Exception) { Log.e("UpdateCalendar", "Eğitmen verisi çekilemedi", e) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Update Calendar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = {
                scope.launch {
                    try {
                        val res = RetrofitClient.instance.getHistory("Bearer ${viewModel.authToken}")
                        if (res.isSuccessful) scheduleHistory = res.body() ?: emptyList()
                    } catch (e: Exception) {}
                    showHistoryDialog = true
                }
            }) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("History")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            return@Column
        }

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
                        text = { Text(user.fullName) },
                        onClick = {
                            if (isDirty && selectedUser != null && selectedUser != user) {
                                pendingUserSelect = user
                                expanded = false
                            } else {
                                selectedUser = user
                                expanded = false
                                selectedCourseToAssign = null
                                selectedClassroom = null
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedUser != null) {
            Text("Assigned Courses (Tap to select, then tap grid to assign):", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(instructorCourses) { course ->
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
                days = DAYS,
                timeSlots = TIME_SLOTS,
                availableSlots = availableSlots,
                draftSchedule = draftSchedule,
                classrooms = classrooms,
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

                AlertDialog(
                    onDismissRequest = { showAssignDialog = null },
                    title = { Text("Assign: ${selectedCourseToAssign?.code}") },
                    text = {
                        Column {
                            Text("Slot: $reqDay $reqSlot", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Duration (hours):", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 2, 3).forEach { d ->
                                    FilterChip(
                                        selected = selectedDuration == d,
                                        onClick = { selectedDuration = d },
                                        label = { Text("${d}h") }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Classroom (optional):", style = MaterialTheme.typography.labelMedium)
                            ExposedDropdownMenuBox(
                                expanded = classroomDropdownExpanded,
                                onExpandedChange = { classroomDropdownExpanded = !classroomDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedClassroom?.roomCode ?: "No classroom",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classroomDropdownExpanded) },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = classroomDropdownExpanded,
                                    onDismissRequest = { classroomDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(text = { Text("No classroom") }, onClick = { selectedClassroom = null; classroomDropdownExpanded = false })
                                    classrooms.forEach { room ->
                                        DropdownMenuItem(
                                            text = { Text(room.roomCode) },
                                            onClick = { selectedClassroom = room; classroomDropdownExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !outOfBounds,
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
                        ) { Text("Confirm") }
                    },
                    dismissButton = { TextButton(onClick = { showAssignDialog = null }) { Text("Cancel") } }
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear Schedule") }

                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val token = "Bearer ${viewModel.authToken}"
                                val username = selectedUser!!.username

                                // JSON Formatına Dönüştür
                                val flatSlots = mutableMapOf<String, CourseDto?>()
                                DAYS.forEach { day ->
                                    TIME_SLOTS.forEach { slot ->
                                        draftSchedule[day]?.get(slot)?.let { course ->
                                            flatSlots["${day}_${slot}"] = course
                                        }
                                    }
                                }

                                // API'ye Gönder
                                val schedDto = ScheduleDto(username, flatSlots)
                                val response = RetrofitClient.instance.updateSchedule(token, username, schedDto)

                                if (response.isSuccessful) {
                                    isDirty = false
                                    // Bildirim Gönder
                                    RetrofitClient.instance.sendNotification(token, NotificationDto(recipientUsername = username, text = "Admin updated your weekly schedule. Please check 'My Schedule'."))
                                    snackbarHostState.showSnackbar("Schedule saved and user notified!")
                                } else {
                                    snackbarHostState.showSnackbar("Failed to save schedule.")
                                }
                            } catch (e: Exception) {
                                Log.e("UpdateCalendar", "Kaydetme hatası", e)
                                snackbarHostState.showSnackbar("Network error.")
                            }
                        }
                    }
                ) { Text("Save & Notify Instructor") }
            }
        }
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Schedule History") },
            text = {
                if (scheduleHistory.isEmpty()) {
                    Text("No changes recorded yet.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(scheduleHistory) { change ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("By: ${change.changedBy}", style = MaterialTheme.typography.labelSmall)
                                    Text("${change.instructorFullName} · ${change.day} ${change.timeSlot}", fontWeight = FontWeight.Bold)
                                    val prevText = change.previousCourseCode ?: "—"
                                    val nextText = change.newCourseCode ?: "Removed"
                                    Text("$prevText → $nextText", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistoryDialog = false }) { Text("Close") } }
        )
    }
}

@Composable
fun CourseItemSelectable(course: CourseDto, isSelected: Boolean, onClick: () -> Unit) {
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
    availableSlots: Map<String, List<String>>,
    draftSchedule: Map<String, SnapshotStateMap<String, CourseDto?>>,
    classrooms: List<ClassroomDto>,
    onCellClick: (String, String) -> Unit,
    onSlotCleared: () -> Unit
) {
    val gridScrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(gridScrollState)) {
        Column(modifier = Modifier.border(1.dp, Color.LightGray)) {
            Row(modifier = Modifier.height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.secondaryContainer)) {
                Box(modifier = Modifier.width(80.dp).padding(8.dp)) { Text("Time", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.Gray))
                days.forEach { day ->
                    Box(modifier = Modifier.width(65.dp).padding(8.dp), contentAlignment = Alignment.Center) { Text(day, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
            }
            Column {
                timeSlots.forEach { slot ->
                    Row(modifier = Modifier.height(IntrinsicSize.Min).border(0.5.dp, Color.LightGray)) {
                        Box(modifier = Modifier.width(80.dp).padding(8.dp).background(Color(0xFFF9F9F9))) { Text(slot, fontSize = 9.sp) }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.LightGray))
                        days.forEach { day ->
                            val isAvailable = availableSlots[day]?.contains(slot) == true
                            val scheduledCourse = draftSchedule[day]?.get(slot)
                            val isContinuation = scheduledCourse?.duration == -1

                            Box(
                                modifier = Modifier
                                    .width(65.dp).height(50.dp).border(0.5.dp, Color.LightGray)
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
                                if (scheduledCourse != null && !isContinuation) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(scheduledCourse.code, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        IconButton(
                                            onClick = {
                                                val slotIndex = timeSlots.indexOf(slot)
                                                draftSchedule[day]?.set(slot, null)
                                                for (i in 1 until (scheduledCourse.duration)) {
                                                    val nextIdx = slotIndex + i
                                                    if (nextIdx < timeSlots.size) {
                                                        val nextSlot = timeSlots[nextIdx]
                                                        if (draftSchedule[day]?.get(nextSlot)?.duration == -1) draftSchedule[day]?.set(nextSlot, null)
                                                    }
                                                }
                                                onSlotCleared()
                                            },
                                            modifier = Modifier.size(16.dp)
                                        ) { Icon(Icons.Default.Clear, contentDescription = "Remove", tint = Color.Red) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
