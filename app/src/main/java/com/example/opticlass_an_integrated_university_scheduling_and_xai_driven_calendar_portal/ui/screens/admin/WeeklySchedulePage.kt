package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.*
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.WeeklyScheduleResponseDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.WeeklySlotDto

private val DEPT_COLORS = listOf(
    Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF6A1B9A),
    Color(0xFFE65100), Color(0xFF00695C), Color(0xFF4527A0),
    Color(0xFFAD1457), Color(0xFF37474F)
)

private data class CellItem(
    val courseCode: String,
    val courseName: String,
    val instructorName: String,
    val department: String,
    val classroomCode: String = "",
    val isLab: Boolean = false,
    val isContinuation: Boolean = false
)

@Composable
fun WeeklySchedulePage(
    onSuggestWeekly: (phase: String, (WeeklyScheduleResponseDto?) -> Unit) -> Unit = { _, _ -> }
) {
    val phasePriorities = AppRepository.phasePriorities
    val allCourses = AppRepository.courseImports
    val allUsers = AppRepository.users

    var viewMode by rememberSaveable { mutableStateOf("phase") }
    var selectedPhaseIndex by rememberSaveable { mutableStateOf(0) }
    var selectedDept by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSemester by rememberSaveable { mutableStateOf<Int?>(null) }

    var phaseDropdownExpanded by remember { mutableStateOf(false) }
    var deptDropdownExpanded by remember { mutableStateOf(false) }
    var semesterDropdownExpanded by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var suggestionResult by remember { mutableStateOf<WeeklyScheduleResponseDto?>(null) }
    var expandedIdx by rememberSaveable { mutableStateOf(-1) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val totalPhases = if (phasePriorities.isEmpty()) 1 else phasePriorities.size
    val selectedPhase = "PHASE_${selectedPhaseIndex + 1}"
    val selectedPriority = phasePriorities.getOrNull(selectedPhaseIndex)

    val departments = remember(allCourses.size) {
        allCourses.map { it.department }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val semesters = remember(allCourses.size) {
        allCourses.map { it.semester }.distinct().sorted()
    }
    val deptColorMap = remember(departments) {
        departments.mapIndexed { i, d -> d to DEPT_COLORS[i % DEPT_COLORS.size] }.toMap()
    }

    LaunchedEffect(departments) {
        if (selectedDept == null && departments.isNotEmpty()) selectedDept = departments.first()
    }
    LaunchedEffect(semesters) {
        if (selectedSemester == null && semesters.isNotEmpty()) selectedSemester = semesters.first()
    }

    fun buildCurrentMap(): Map<String, Map<String, List<CellItem>>> {
        val map: MutableMap<String, MutableMap<String, MutableList<CellItem>>> =
            DAYS.associateWith { mutableMapOf<String, MutableList<CellItem>>() }.toMutableMap()
        allUsers.filter { it.role == UserRole.INSTRUCTOR }.forEach { user ->
            user.schedule.forEach { (day, slots) ->
                slots.forEach { (slot, course) ->
                    if (course != null) {
                        val isContinuation = course.duration == -1
                        val matches = when (viewMode) {
                            "phase" -> selectedPriority == null || course.priority == selectedPriority
                            else -> (selectedDept == null || course.department == selectedDept) &&
                                    (selectedSemester == null || course.semester == selectedSemester)
                        }
                        if (matches) {
                            map[day]?.getOrPut(slot) { mutableListOf() }?.add(
                                CellItem(
                                    courseCode = course.code,
                                    courseName = course.name,
                                    instructorName = user.fullName,
                                    department = course.department,
                                    classroomCode = AppRepository.classrooms
                                        .find { it.id == course.classroomId }?.roomCode ?: "",
                                    isContinuation = isContinuation
                                )
                            )
                        }
                    }
                }
            }
        }
        return map
    }

    val currentMap = remember(viewMode, selectedPhaseIndex, selectedDept, selectedSemester, allUsers.size) {
        buildCurrentMap()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Filter bar
        item {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // View mode tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = viewMode == "phase",
                        onClick = { viewMode = "phase"; suggestionResult = null; expandedIdx = -1 },
                        label = { Text("Phase View") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = viewMode == "dept",
                        onClick = { viewMode = "dept"; suggestionResult = null; expandedIdx = -1 },
                        label = { Text("Dept / Semester") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Dropdowns row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Phase dropdown (always shown)
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { phaseDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Phase ${selectedPhaseIndex + 1}", maxLines = 1)
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = phaseDropdownExpanded,
                            onDismissRequest = { phaseDropdownExpanded = false }
                        ) {
                            repeat(totalPhases) { idx ->
                                val pri = phasePriorities.getOrNull(idx)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (pri != null) "Phase ${idx + 1}  (priority $pri)"
                                            else "Phase ${idx + 1}"
                                        )
                                    },
                                    onClick = {
                                        selectedPhaseIndex = idx
                                        phaseDropdownExpanded = false
                                        suggestionResult = null
                                        expandedIdx = -1
                                    }
                                )
                            }
                        }
                    }

                    if (viewMode == "dept") {
                        // Dept dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { deptDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(selectedDept ?: "Dept", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = deptDropdownExpanded,
                                onDismissRequest = { deptDropdownExpanded = false }
                            ) {
                                departments.forEach { d ->
                                    DropdownMenuItem(
                                        text = { Text(d) },
                                        onClick = { selectedDept = d; deptDropdownExpanded = false }
                                    )
                                }
                            }
                        }
                        // Semester dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { semesterDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Sem ${selectedSemester ?: "?"}", maxLines = 1)
                                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = semesterDropdownExpanded,
                                onDismissRequest = { semesterDropdownExpanded = false }
                            ) {
                                semesters.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text("Semester $s") },
                                        onClick = { selectedSemester = s; semesterDropdownExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Current schedule section header
        item {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.DateRange, null, modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(
                    when (viewMode) {
                        "phase" -> "Current Schedule — Phase ${selectedPhaseIndex + 1}" +
                                (selectedPriority?.let { " (Priority $it)" } ?: "")
                        else -> "Current Schedule — ${selectedDept ?: "All Depts"} · Semester ${selectedSemester ?: "?"}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Current schedule grid
        item {
            WeeklyGrid(
                scheduleMap = currentMap,
                deptColorMap = deptColorMap,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // XAI suggestion section (phase mode only)
        if (viewMode == "phase") {
            item {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "XAI Weekly Suggestions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Generate 3 alternative schedules for Phase ${selectedPhaseIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            isLoading = true
                            errorMsg = null
                            suggestionResult = null
                            expandedIdx = -1
                            onSuggestWeekly(selectedPhase) { result ->
                                isLoading = false
                                if (result != null) suggestionResult = result
                                else errorMsg = "Could not generate suggestions. Check server connection."
                            }
                        },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (isLoading) "Generating..." else "✨ Generate")
                    }
                }

                if (errorMsg != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

                suggestionResult?.let { res ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        res.algorithmNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }

            // 3 suggestion cards
            val suggestions = suggestionResult?.suggestions ?: emptyList()
            itemsIndexed(suggestions) { idx, suggestion ->
                val isExpanded = expandedIdx == idx
                val assignedCount = suggestion.assignments.size
                val unassignedCount = suggestion.unassignedCourses.size
                val coverPct = if (assignedCount + unassignedCount > 0)
                    (assignedCount * 100) / (assignedCount + unassignedCount) else 0

                val suggestionMap = buildSuggestionMap(suggestion.assignments)

                Card(
                    onClick = { expandedIdx = if (isExpanded) -1 else idx },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
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
                                Text(suggestion.title, fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall)
                                Text(suggestion.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatBadge("$assignedCount assigned", Color(0xFF2E7D32).copy(alpha = 0.15f), Color(0xFF2E7D32))
                                    if (unassignedCount > 0) {
                                        StatBadge("$unassignedCount unassigned",
                                            MaterialTheme.colorScheme.errorContainer,
                                            MaterialTheme.colorScheme.error)
                                    }
                                    StatBadge("$coverPct% coverage",
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            Icon(
                                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        if (isExpanded) {
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(10.dp))

                            Text("Schedule Grid", fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(6.dp))
                            WeeklyGrid(
                                scheduleMap = suggestionMap,
                                deptColorMap = deptColorMap
                            )

                            if (suggestion.unassignedCourses.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Unassigned Courses (no available slot found):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(4.dp))
                                suggestion.unassignedCourses.forEach { item ->
                                    Text(
                                        "• $item",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (suggestionResult != null && suggestions.isEmpty()) {
                item {
                    Text(
                        "No suggestions could be generated. Ensure instructor availabilities are submitted.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun buildSuggestionMap(assignments: List<WeeklySlotDto>): Map<String, Map<String, List<CellItem>>> {
    val map: MutableMap<String, MutableMap<String, MutableList<CellItem>>> =
        DAYS.associateWith { mutableMapOf<String, MutableList<CellItem>>() }.toMutableMap()
    assignments.forEach { a ->
        val startIdx = TIME_SLOTS.indexOf(a.timeSlot)
        if (startIdx < 0 || a.day !in map) return@forEach
        val dur = a.duration.coerceAtLeast(1)
        repeat(dur) { k ->
            val slot = TIME_SLOTS.getOrNull(startIdx + k) ?: return@repeat
            map[a.day]!!.getOrPut(slot) { mutableListOf() }.add(
                CellItem(
                    courseCode = a.courseCode,
                    courseName = a.courseName,
                    instructorName = a.lecturerFullName,
                    department = a.department,
                    classroomCode = a.classroomCode,
                    isLab = a.isLab,
                    isContinuation = k > 0
                )
            )
        }
    }
    return map
}

@Composable
private fun WeeklyGrid(
    scheduleMap: Map<String, Map<String, List<CellItem>>>,
    deptColorMap: Map<String, Color>,
    modifier: Modifier = Modifier
) {
    val borderColor = MaterialTheme.colorScheme.outline
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    val cellWidth = 110.dp
    val slotLabelWidth = 68.dp
    val minRowHeight = 56.dp
    val chipHeightDp = 22
    val chipSpacingDp = 2

    val maxItemsPerSlot = remember(scheduleMap) {
        TIME_SLOTS.associateWith { slot ->
            DAYS.mapNotNull { day -> scheduleMap[day]?.get(slot)?.size }.maxOrNull() ?: 0
        }
    }

    Column(modifier = modifier) {
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                // Header row
                Row {
                    Box(
                        modifier = Modifier
                            .width(slotLabelWidth)
                            .height(30.dp)
                            .background(headerBg)
                            .border(1.dp, borderColor)
                    )
                    DAYS.forEach { day ->
                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .height(30.dp)
                                .background(headerBg)
                                .border(1.dp, borderColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(day, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Time slot rows
                TIME_SLOTS.forEach { slot ->
                    val itemCount = maxItemsPerSlot[slot] ?: 0
                    val rowH = maxOf(
                        minRowHeight.value.toInt(),
                        itemCount * (chipHeightDp + chipSpacingDp) + 8
                    ).dp
                    Row {
                        // Slot label
                        Box(
                            modifier = Modifier
                                .width(slotLabelWidth)
                                .height(rowH)
                                .background(headerBg)
                                .border(1.dp, borderColor)
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(slot, fontSize = 8.sp, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        DAYS.forEach { day ->
                            val items = scheduleMap[day]?.get(slot) ?: emptyList()
                            Box(
                                modifier = Modifier
                                    .width(cellWidth)
                                    .height(rowH)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, borderColor)
                                    .padding(2.dp)
                            ) {
                                if (items.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(chipSpacingDp.dp)
                                    ) {
                                        items.forEach { item ->
                                            val color = deptColorMap[item.department]
                                                ?: MaterialTheme.colorScheme.primary
                                            CourseChip(
                                                item = item,
                                                color = color
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

        // Legend
        if (deptColorMap.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                deptColorMap.entries.take(8).forEach { (dept, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(dept, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, bgColor: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 10.sp, color = textColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CourseChip(item: CellItem, color: Color) {
    val alpha = if (item.isContinuation) 0.55f else 1f
    val bgColor = if (item.isLab) color.copy(alpha = 0.25f * alpha) else color.copy(alpha = 0.15f * alpha)
    val borderColor = if (item.isLab) color.copy(alpha = 0.6f * alpha) else color.copy(alpha = 0.4f * alpha)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(3.dp))
            .border(
                width = if (item.isLab) 1.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(3.dp)
            )
            .padding(horizontal = 3.dp, vertical = 1.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.courseCode,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = color.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.isLab && !item.isContinuation) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "LAB",
                        fontSize = 6.sp,
                        fontWeight = FontWeight.Bold,
                        color = color.copy(alpha = alpha),
                        modifier = Modifier
                            .background(color.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 2.dp)
                    )
                }
            }
            if (!item.isContinuation) {
                Text(
                    item.instructorName.split(" ").firstOrNull() ?: item.instructorName,
                    fontSize = 7.sp,
                    color = color.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.classroomCode.isNotBlank()) {
                    Text(
                        item.classroomCode,
                        fontSize = 7.sp,
                        color = color.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
