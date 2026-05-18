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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.XAISuggestionResponseDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCalendarPage(
    snackbarHostState: SnackbarHostState,
    currentAdminUser: String,
    onSaveSchedule: (username: String, draft: Map<String, SnapshotStateMap<String, CourseImport?>>, historyEntries: List<ScheduleChange>) -> Unit = { _, _, _ -> },
    onSendNotification: (recipientUsername: String, text: String) -> Unit = { _, _ -> },
    onSetSchedulingPhase: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
    onSuggestSchedule: (username: String, courseCode: String, classroomId: String?, duration: Int, suggestionType: String, onResult: (XAISuggestionResponseDto?) -> Unit) -> Unit = { _, _, _, _, _, _ -> }
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<User?>(null) }
    val currentPhasePriority = AppRepository.currentPhasePriority
    val totalPhases = AppRepository.totalPhases
    val currentPhaseIndex = AppRepository.currentPhaseIndex
    val isLastPhase = totalPhases > 0 && currentPhaseIndex >= totalPhases - 1
    val instructors = if (AppRepository.phasePriorities.isEmpty()) {
        AppRepository.users.filter { it.role == UserRole.INSTRUCTOR }
    } else {
        AppRepository.users.filter { user ->
            user.role == UserRole.INSTRUCTOR && user.courses.any { c -> c.priority == currentPhasePriority }
        }
    }
    val scope = rememberCoroutineScope()
    var isDirty by remember { mutableStateOf(false) }
    var pendingUserSelect by remember { mutableStateOf<User?>(null) }

    var selectedCourseToAssign by remember { mutableStateOf<CourseImport?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showPhaseConfirmDialog by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var suggestionLoading by remember { mutableStateOf(false) }
    var suggestionResult by remember { mutableStateOf<XAISuggestionResponseDto?>(null) }
    var expandedSuggestionIndex by remember { mutableIntStateOf(-1) }
    var showSuggestionDialog by remember { mutableStateOf(false) }
    var assignmentMode by remember { mutableStateOf<String?>(null) }  // "lecture" | "lab" | null
    var selectedClassroom by remember { mutableStateOf<Classroom?>(null) }
    var suggestionClassroomDropdownExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(selectedCourseToAssign) { assignmentMode = null; selectedClassroom = null }
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

        if (totalPhases > 0 && !isLastPhase) {
            val phaseNum = currentPhaseIndex + 1
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                border = BorderStroke(1.dp, Color(0xFFFF6F00))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Phase $phaseNum of $totalPhases: Priority $currentPhasePriority", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFE65100))
                        Text("Only instructors with priority-$currentPhasePriority courses are listed.", fontSize = 11.sp, color = Color(0xFFBF360C))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { showPhaseConfirmDialog = true },
                        border = BorderStroke(1.dp, Color(0xFFE65100)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Phase ${phaseNum + 1} →", color = Color(0xFFE65100), fontSize = 12.sp)
                    }
                }
            }
        } else if (totalPhases > 0) {
            val phaseNum = currentPhaseIndex + 1
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                border = BorderStroke(1.dp, Color(0xFF2E7D32))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Phase $phaseNum of $totalPhases: Priority $currentPhasePriority (Final Phase)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1B5E20))
                        Text("All priority groups are being scheduled.", fontSize = 11.sp, color = Color(0xFF2E7D32))
                    }
                }
            }
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

            val prevPriorities = AppRepository.previousPhasePriorities
            val semesterBlockedSlots: Set<Pair<String, String>> = if (selectedCourseToAssign == null) emptySet()
            else {
                val blocked = mutableSetOf<Pair<String, String>>()
                val newCourse = selectedCourseToAssign!!
                DAYS.forEach { day ->
                    TIME_SLOTS.forEach { slot ->
                        val hasConflict = AppRepository.users.filter { it.username != user.username }.any { u ->
                            val slotCourse = u.schedule[day]?.get(slot)
                            if (slotCourse == null) false
                            else if (slotCourse.semester != newCourse.semester) false
                            else if (slotCourse.priority in prevPriorities) true
                            else slotCourse.department == newCourse.department
                        }
                        if (hasConflict) blocked.add(day to slot)
                    }
                }
                blocked
            }

            Text("Assigned Courses (Tap to select, then tap grid to assign):", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            val sortedCourses = user.courses.sortedWith(
                compareByDescending<CourseImport> { it.priority }.thenByDescending { it.studentCount }
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sortedCourses) { course ->
                    CourseItemSelectable(
                        course = course,
                        isSelected = selectedCourseToAssign == course,
                        onClick = { selectedCourseToAssign = if (selectedCourseToAssign == course) null else course }
                    )
                }
            }

            if (selectedCourseToAssign != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val isLabMode = assignmentMode == "lab"
                val course = selectedCourseToAssign!!

                // Lecture / Lab mode selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val lectureSelected = assignmentMode == "lecture"
                    OutlinedButton(
                        onClick = { assignmentMode = if (lectureSelected) null else "lecture" },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, if (lectureSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (lectureSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = if (lectureSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Lecture (${course.lectureHours}h)", fontSize = 12.sp, maxLines = 1)
                    }
                    if (course.labHours > 0) {
                        val labSelected = assignmentMode == "lab"
                        OutlinedButton(
                            onClick = { assignmentMode = if (labSelected) null else "lab" },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, if (labSelected) Color(0xFF6A1B9A) else MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (labSelected) Color(0xFF6A1B9A).copy(alpha = 0.12f) else Color.Transparent,
                                contentColor = if (labSelected) Color(0xFF6A1B9A) else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("Lab (${course.labHours}h)", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (assignmentMode != null)
                        "Tap a grid cell to assign a ${if (isLabMode) course.labHours else course.lectureHours}h $assignmentMode block"
                    else
                        "Select Lecture or Lab to start manual assignment",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                val suggestionEligibleClassrooms = AppRepository.classrooms.filter { room ->
                    course.studentCount == 0 || room.capacity >= course.studentCount
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        OutlinedButton(onClick = { suggestionClassroomDropdownExpanded = true }) {
                            Text(selectedClassroom?.roomCode ?: "Any room", fontSize = 12.sp, maxLines = 1)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = suggestionClassroomDropdownExpanded,
                            onDismissRequest = { suggestionClassroomDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Any classroom") },
                                onClick = { selectedClassroom = null; suggestionClassroomDropdownExpanded = false }
                            )
                            suggestionEligibleClassrooms.forEach { room ->
                                DropdownMenuItem(
                                    text = { Text("${room.roomCode} (cap: ${room.capacity})") },
                                    onClick = { selectedClassroom = room; suggestionClassroomDropdownExpanded = false }
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val type = if (isLabMode) "lab" else "lecture"
                            val dur = when {
                                isLabMode && course.labHours > 0 -> course.labHours
                                course.lectureHours > 0 -> course.lectureHours
                                else -> 1
                            }
                            suggestionLoading = true
                            onSuggestSchedule(user.username, course.code, selectedClassroom?.id, dur, type) { result ->
                                suggestionResult = result
                                expandedSuggestionIndex = -1
                                suggestionLoading = false
                                showSuggestionDialog = result != null
                                if (result == null) {
                                    scope.launch { snackbarHostState.showSnackbar("Could not generate suggestion. Check availability data.") }
                                }
                            }
                        },
                        enabled = !suggestionLoading,
                        border = BorderStroke(1.dp, if (isLabMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isLabMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (suggestionLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(when {
                            suggestionLoading -> "Analyzing..."
                            isLabMode -> "✨ Suggest Lab Slot"
                            else -> "✨ Suggest Best Slot"
                        })
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Scheduling Grid:", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            SchedulingGridEnhanced(
                DAYS, TIME_SLOTS, availableSlots, draftSchedule, selectedCourseToAssign,
                semesterBlockedSlots = semesterBlockedSlots,
                onCellClick = { day, slot ->
                    if (selectedCourseToAssign != null && assignmentMode != null) {
                        showAssignDialog = day to slot
                    }
                },
                onSlotCleared = { isDirty = true }
            )

            if (showAssignDialog != null) {
                val (reqDay, reqSlot) = showAssignDialog!!
                val startIdx = TIME_SLOTS.indexOf(reqSlot)
                val appliedDuration = when {
                    assignmentMode == "lab" && (selectedCourseToAssign?.labHours ?: 0) > 0 -> selectedCourseToAssign!!.labHours
                    (selectedCourseToAssign?.lectureHours ?: 0) > 0 -> selectedCourseToAssign!!.lectureHours
                    else -> 1
                }
                val targetSlots = (0 until appliedDuration).map { TIME_SLOTS.getOrNull(startIdx + it) }
                val outOfBounds = targetSlots.any { it == null }
                val validSlots = targetSlots.filterNotNull()
                val firstSlotOccupied = draftSchedule[reqDay]?.get(reqSlot) != null
                val conflictSlots = validSlots.drop(1).filter { s ->
                    val existing = draftSchedule[reqDay]?.get(s)
                    existing != null && existing.duration != -1
                }
                val unavailableSlots = validSlots.filter { s ->
                    availableSlots[reqDay]?.contains(s) != true
                }
                val classroomConflict = selectedClassroom != null &&
                    !selectedClassroom!!.roomCode.equals("ONLINE", ignoreCase = true) &&
                    validSlots.any { s ->
                        AppRepository.users.filter { it.username != user.username }.any { u ->
                            u.schedule[reqDay]?.get(s)?.classroomId == selectedClassroom!!.id
                        }
                    }
                val semesterConflict = selectedCourseToAssign != null && validSlots.any { s ->
                    val newCourse = selectedCourseToAssign!!
                    AppRepository.users.filter { it.username != user.username }.any { u ->
                        val slotCourse = u.schedule[reqDay]?.get(s)
                        if (slotCourse == null) return@any false
                        if (slotCourse.semester != newCourse.semester) return@any false
                        if (slotCourse.priority in prevPriorities) return@any true
                        slotCourse.department == newCourse.department
                    }
                }
                val minCapacity = selectedCourseToAssign?.studentCount ?: 0
                val eligibleClassrooms = if (minCapacity > 0)
                    AppRepository.classrooms.filter { it.capacity >= minCapacity }
                else
                    AppRepository.classrooms

                fun isClassroomOccupied(room: Classroom): Boolean =
                    !room.roomCode.equals("ONLINE", ignoreCase = true) && validSlots.any { s ->
                        AppRepository.users.filter { it.username != user.username }.any { u ->
                            u.schedule[reqDay]?.get(s)?.classroomId == room.id
                        }
                    }

                AlertDialog(
                    onDismissRequest = { showAssignDialog = null },
                    title = { Text("Assign ${if (assignmentMode == "lab") "Lab" else "Lecture"}: ${selectedCourseToAssign?.code}") },
                    text = {
                        Column {
                            Text("Slot: $reqDay  ·  $reqSlot", style = MaterialTheme.typography.bodyMedium)
                            Text("Duration: ${appliedDuration}h ${assignmentMode ?: ""} block", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Classroom:", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            ExposedDropdownMenuBox(
                                expanded = classroomDropdownExpanded,
                                onExpandedChange = { classroomDropdownExpanded = !classroomDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedClassroom?.let { "${it.roomCode} (cap: ${it.capacity})" } ?: "No classroom",
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
                                    if (minCapacity > 0 && eligibleClassrooms.size < AppRepository.classrooms.size) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "${AppRepository.classrooms.size - eligibleClassrooms.size} room(s) hidden (capacity < $minCapacity)",
                                                    fontSize = 10.sp, color = Color.Gray
                                                )
                                            },
                                            enabled = false,
                                            onClick = {}
                                        )
                                    }
                                    eligibleClassrooms.forEach { room ->
                                        val occupied = isClassroomOccupied(room)
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "${room.roomCode}  (cap: ${room.capacity})",
                                                        color = if (occupied) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else Color.Unspecified
                                                    )
                                                    if (occupied) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("(occupied)", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            },
                                            enabled = !occupied,
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
                                    "Not enough slots for a ${appliedDuration}h ${assignmentMode ?: ""} block starting at this time.",
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
                            if (firstSlotOccupied) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "This slot already has a course. Deselect the course, then tap the slot to remove it.",
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
                            if (semesterConflict) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val newCourse = selectedCourseToAssign!!
                                val conflictDesc = if (prevPriorities.isNotEmpty())
                                    "A higher-priority phase course is already scheduled at this time for Semester ${newCourse.semester}."
                                else
                                    "A Semester ${newCourse.semester} course from ${newCourse.department} is already scheduled at this time."
                                Text(
                                    conflictDesc,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !outOfBounds && !classroomConflict && !semesterConflict && !firstSlotOccupied,
                            onClick = {
                                val course = selectedCourseToAssign!!.copy(
                                    duration = appliedDuration,
                                    classroomId = selectedClassroom?.id
                                )
                                validSlots.forEachIndexed { index, s ->
                                    draftSchedule[reqDay]?.set(s, if (index == 0) course else course.copy(duration = -1))
                                }
                                isDirty = true
                                showAssignDialog = null
                                assignmentMode = if (assignmentMode == "lecture" && (selectedCourseToAssign?.labHours ?: 0) > 0) "lab" else null
                            }
                        ) {
                            Text(when {
                                firstSlotOccupied -> "Occupied"
                                conflictSlots.isNotEmpty() || unavailableSlots.isNotEmpty() -> "Assign Anyway"
                                else -> "Confirm"
                            })
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
                        val historyEntries = mutableListOf<ScheduleChange>()
                        DAYS.forEach { day ->
                            TIME_SLOTS.forEach { slot ->
                                val prev = user.schedule[day]?.get(slot)
                                val next = draftSchedule[day]?.get(slot)
                                if (prev?.code != next?.code) {
                                    val entry = ScheduleChange(
                                        changedBy = currentAdminUser,
                                        timestamp = now,
                                        instructorUsername = user.username,
                                        instructorFullName = user.fullName,
                                        day = day,
                                        timeSlot = slot,
                                        previousCourse = prev,
                                        newCourse = next
                                    )
                                    historyEntries.add(entry)
                                    AppRepository.scheduleHistory.add(0, entry)
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
                        if (assignmentMode != "lab") selectedCourseToAssign = null
                        onSaveSchedule(user.username, draftSchedule, historyEntries)
                        onSendNotification(user.username, "Admin updated your weekly schedule. Please check 'My Schedule'.")
                        scope.launch { snackbarHostState.showSnackbar("Schedule updated and notification sent!") }
                    }
                ) {
                    Text("Save & Notify Instructor")
                }
            }

            if (showSuggestionDialog && suggestionResult != null) {
                val result = suggestionResult!!
                AlertDialog(
                    onDismissRequest = { showSuggestionDialog = false },
                    title = {
                        Column {
                            Text(
                                if (result.suggestionType == "lab") "XAI Suggestion — Lab" else "XAI Suggestion — Lecture",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${result.courseName} (${result.courseCode})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    text = {
                        if (result.suggestions.isEmpty()) {
                            Text("No suggestions available. Ensure instructor availability is submitted.")
                        } else {
                            Column(
                                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    result.algorithmNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                result.suggestions.forEachIndexed { idx, suggestion ->
                                    val isExpanded = expandedSuggestionIndex == idx
                                    val appliedDuration = when {
                                        result.suggestionType == "lab" && (selectedCourseToAssign?.labHours ?: 0) > 0 -> selectedCourseToAssign!!.labHours
                                        (selectedCourseToAssign?.lectureHours ?: 0) > 0 -> selectedCourseToAssign!!.lectureHours
                                        else -> 1
                                    }
                                    val slotIdx = TIME_SLOTS.indexOf(suggestion.timeSlot)
                                    val slotsNeeded = if (slotIdx >= 0)
                                        (0 until appliedDuration).mapNotNull { TIME_SLOTS.getOrNull(slotIdx + it) }
                                    else emptyList()
                                    val applyDraftConflict = slotsNeeded.any { s ->
                                        draftSchedule[suggestion.day]?.get(s) != null
                                    }
                                    val scoreColor = when {
                                        suggestion.score >= 0.7f -> Color(0xFF2E7D32)
                                        suggestion.score >= 0.4f -> Color(0xFFF57C00)
                                        else -> MaterialTheme.colorScheme.error
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            expandedSuggestionIndex = if (isExpanded) -1 else idx
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isExpanded)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        "Option ${idx + 1}: ${suggestion.day}  ·  ${suggestion.timeSlot}",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleSmall
                                                    )
                                                    if (suggestion.classroomCode != null) {
                                                        Text(
                                                            "Room: ${suggestion.classroomCode}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "${(suggestion.score * 100).toInt()}%",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = scoreColor
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Icon(
                                                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            if (isExpanded) {
                                                Spacer(Modifier.height(8.dp))
                                                HorizontalDivider()
                                                Spacer(Modifier.height(8.dp))
                                                // Apply button — header'ın hemen altında, her zaman görünür
                                                Button(
                                                    enabled = !applyDraftConflict,
                                                    onClick = {
                                                        val course = selectedCourseToAssign!!.copy(
                                                            duration = appliedDuration,
                                                            classroomId = suggestion.classroomId
                                                        )
                                                        if (slotIdx >= 0) {
                                                            for (k in 0 until appliedDuration) {
                                                                val s = TIME_SLOTS.getOrNull(slotIdx + k) ?: break
                                                                draftSchedule[suggestion.day]?.set(s, if (k == 0) course else course.copy(duration = -1))
                                                            }
                                                            isDirty = true
                                                            selectedClassroom = AppRepository.classrooms.find { it.id == suggestion.classroomId }
                                                        }
                                                        if (result.suggestionType == "lecture" && (selectedCourseToAssign?.labHours ?: 0) > 0) assignmentMode = "lab"
                                                        showSuggestionDialog = false
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (applyDraftConflict) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary
                                                    )
                                                ) {
                                                    Icon(
                                                        if (applyDraftConflict) Icons.Default.Warning else Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        if (applyDraftConflict) "Slot Occupied" else "Apply Option ${idx + 1}",
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(Modifier.height(10.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Match Score", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(90.dp))
                                                    LinearProgressIndicator(
                                                        progress = { suggestion.score },
                                                        modifier = Modifier.weight(1f).height(8.dp),
                                                        color = scoreColor
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("${(suggestion.score * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(Modifier.height(12.dp))
                                                Text("Decision Path", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                Spacer(Modifier.height(6.dp))
                                                suggestion.path.forEach { node ->
                                                    val nodeIcon = when (node.result) {
                                                        "pass" -> "✓"
                                                        "fail_hard" -> "✗"
                                                        "partial" -> "◑"
                                                        "info" -> "ℹ"
                                                        else -> "·"
                                                    }
                                                    val nodeColor = when (node.result) {
                                                        "pass" -> Color(0xFF2E7D32)
                                                        "fail_hard" -> MaterialTheme.colorScheme.error
                                                        "partial" -> Color(0xFFF57C00)
                                                        "info" -> Color(0xFF1565C0)
                                                        else -> Color.Gray
                                                    }
                                                    val badge = when {
                                                        node.isHard -> " [required]"
                                                        node.scoreContribution > 0f -> " [+${(node.scoreContribution * 100).toInt()}%]"
                                                        node.weight > 0f -> " [+0%]"
                                                        else -> ""
                                                    }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                        verticalAlignment = Alignment.Top
                                                    ) {
                                                        Text(
                                                            nodeIcon,
                                                            color = nodeColor,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.width(18.dp),
                                                            fontSize = 14.sp
                                                        )
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Row {
                                                                Text(node.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                                Text(badge, fontSize = 10.sp, color = Color.Gray)
                                                            }
                                                            Text(node.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.height(10.dp))
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        suggestion.summary,
                                                        modifier = Modifier.padding(10.dp),
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showSuggestionDialog = false }) { Text("Close") }
                    }
                )
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

    if (showPhaseConfirmDialog) {
        val nextPhaseNum = currentPhaseIndex + 2
        val nextPhase = "PHASE_$nextPhaseNum"
        val nextPriority = if (nextPhaseNum - 1 < AppRepository.phasePriorities.size)
            AppRepository.phasePriorities[nextPhaseNum - 1] else -1
        val unassignedCourses = AppRepository.courseImports.filter { course ->
            course.priority == currentPhasePriority &&
            (!course.lectureAssigned || (course.labHours > 0 && course.labAssigned == false))
        }
        AlertDialog(
            onDismissRequest = { showPhaseConfirmDialog = false },
            title = { Text("Switch to Phase $nextPhaseNum") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 400.dp)) {
                    Text(
                        if (nextPriority != -1)
                            "Phase ${currentPhaseIndex + 1} (priority $currentPhasePriority) assignments will be finalized. Phase $nextPhaseNum will cover priority-$nextPriority courses. This cannot be undone. Continue?"
                        else
                            "Switching to Phase $nextPhaseNum. This cannot be undone. Continue?"
                    )
                    if (unassignedCourses.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Unassigned courses (${unassignedCourses.size})",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelMedium)
                                }
                                Spacer(Modifier.height(6.dp))
                                unassignedCourses.forEach { course ->
                                    val instructor = AppRepository.users.find { it.username == course.lecturer }
                                    val missing = buildString {
                                        if (!course.lectureAssigned) append("lecture")
                                        if (course.labHours > 0 && course.labAssigned == false) {
                                            if (isNotEmpty()) append(" + ")
                                            append("lab")
                                        }
                                    }
                                    Text(
                                        "• ${course.code} — ${instructor?.fullName ?: course.lecturer} ($missing not assigned)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPhaseConfirmDialog = false
                    onSetSchedulingPhase(nextPhase) { success ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (success) "Switched to Phase $nextPhaseNum."
                                else "Switch failed, please try again."
                            )
                        }
                    }
                }) { Text("Switch", color = Color(0xFFE65100)) }
            },
            dismissButton = {
                TextButton(onClick = { showPhaseConfirmDialog = false }) { Text("Cancel") }
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
    val isCommon = course.department == "COMMON"
    Card(
        modifier = Modifier.width(130.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected && isCommon -> Color(0xFFE65100)
                isSelected -> MaterialTheme.colorScheme.primary
                isCommon -> Color(0xFFFFF3E0)
                else -> MaterialTheme.colorScheme.primaryContainer
            }
        ),
        border = if (isCommon && !isSelected) BorderStroke(1.dp, Color(0xFFFF6F00)) else null
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                course.code, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                color = if (isSelected) Color.White else if (isCommon) Color(0xFFE65100) else Color.Unspecified
            )
            Text(
                course.name, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center,
                color = if (isSelected) Color.White else Color.Unspecified
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (isSelected) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text(
                        "Term ${course.semester}", fontSize = 8.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (course.studentCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(8.dp),
                            tint = if (isSelected) Color.White else Color.Gray)
                        Text(" ${course.studentCount}", fontSize = 8.sp,
                            color = if (isSelected) Color.White else Color.Gray)
                    }
                }
            }
            if (course.lectureHours > 0 || course.labHours > 0) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (course.lectureHours > 0) {
                        Surface(
                            color = if (isSelected) Color.White.copy(alpha = 0.2f) else Color(0xFF1565C0).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                "L:${course.lectureHours}h", fontSize = 8.sp,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                color = if (isSelected) Color.White else Color(0xFF1565C0)
                            )
                        }
                    }
                    if (course.labHours > 0) {
                        Surface(
                            color = if (isSelected) Color.White.copy(alpha = 0.2f) else Color(0xFF6A1B9A).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                "Lab:${course.labHours}h", fontSize = 8.sp,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                color = if (isSelected) Color.White else Color(0xFF6A1B9A)
                            )
                        }
                    }
                }
            }
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
    semesterBlockedSlots: Set<Pair<String, String>> = emptySet(),
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
                            val isSemesterBlocked = semesterBlockedSlots.contains(day to slot)

                            Box(
                                modifier = Modifier
                                    .width(65.dp)
                                    .height(50.dp)
                                    .border(
                                        width = if (isSemesterBlocked) 1.dp else 0.5.dp,
                                        color = if (isSemesterBlocked) Color.Red else Color.LightGray
                                    )
                                    .background(
                                        when {
                                            isSemesterBlocked -> Color.Red.copy(alpha = 0.15f)
                                            isContinuation -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            scheduledCourse != null -> MaterialTheme.colorScheme.primaryContainer
                                            isAvailable -> Color.Green.copy(alpha = 0.1f)
                                            else -> Color.Red.copy(alpha = 0.05f)
                                        }
                                    )
                                    .clickable(enabled = !isContinuation && !isSemesterBlocked) {
                                        if (scheduledCourse != null && selectedCourse == null) {
                                            val slotIndex = timeSlots.indexOf(slot)
                                            draftSchedule[day]?.set(slot, null)
                                            for (i in 1 until scheduledCourse.duration) {
                                                val nextSlot = timeSlots.getOrNull(slotIndex + i) ?: break
                                                if (draftSchedule[day]?.get(nextSlot)?.duration == -1) {
                                                    draftSchedule[day]?.set(nextSlot, null)
                                                }
                                            }
                                            onSlotCleared()
                                        } else {
                                            onCellClick(day, slot)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSemesterBlocked) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.Red.copy(alpha = 0.7f)
                                    )
                                } else {
                                if (isContinuation) {
                                    val roomCode = scheduledCourse!!.classroomId?.let { id ->
                                        AppRepository.classrooms.find { it.id == id }?.roomCode
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            scheduledCourse.code,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                        if (roomCode != null) {
                                            Text(
                                                roomCode,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                } else if (scheduledCourse != null) {
                                    val roomCode = scheduledCourse.classroomId?.let { id ->
                                        AppRepository.classrooms.find { it.id == id }?.roomCode
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    ) {
                                        Text(
                                            scheduledCourse.code,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            scheduledCourse.name,
                                            fontSize = 7.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                        if (roomCode != null) {
                                            Text(
                                                roomCode,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                            )
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
                                } // end else (!isSemesterBlocked)
                            }
                        }
                    }
                }
            }
        }
    }
}
