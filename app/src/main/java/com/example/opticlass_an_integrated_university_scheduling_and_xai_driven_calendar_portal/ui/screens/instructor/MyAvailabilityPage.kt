package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun MyAvailabilityPage(
    instructorName: String,
    snackbarHostState: SnackbarHostState,
    onSendMessage: (toUser: String, content: String, (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onSubmitAvailability: (username: String, slots: Map<String, Set<String>>, (Boolean) -> Unit) -> Unit = { _, _, _ -> }
) {
    val scope = rememberCoroutineScope()
    val phase = AppRepository.schedulingPhase
    val isCommonInstructor = AppRepository.users
        .find { it.username == instructorName }
        ?.courses?.any { it.department == "COMMON" } == true

    val initialDraft = AppRepository.availabilityDrafts[instructorName]
        ?: AppRepository.availabilities.find { it.instructorName == instructorName }?.slots
        ?: emptyMap()
    val selectedSlots = remember {
        val map = mutableStateMapOf<String, MutableSet<String>>()
        DAYS.forEach { day -> map[day] = initialDraft[day]?.toMutableSet() ?: mutableSetOf() }
        map
    }

    if (phase == "PHASE_1" && !isCommonInstructor) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Access Currently Locked",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Common course scheduling is in progress.\nAvailability form will open once common courses are assigned.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Your Availability", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (phase == "PHASE_2") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .background(Color(0xFFFFF3E0), shape = MaterialTheme.shapes.small)
                    .border(1.dp, Color(0xFFFF6F00), shape = MaterialTheme.shapes.small)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFFFF6F00))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Orange slots have common courses scheduled. Assigning same-semester courses at these times is not recommended.",
                    fontSize = 11.sp,
                    color = Color(0xFFBF360C)
                )
            }
        }

        AvailabilityTable(
            days = DAYS,
            timeSlots = TIME_SLOTS,
            selectedSlots = selectedSlots,
            isReadOnly = false,
            commonOccupiedSlots = AppRepository.commonCourseSlots
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val slots = selectedSlots.mapValues { it.value.toSet() }
                AppRepository.availabilityDrafts[instructorName] = slots
                onSubmitAvailability(instructorName, slots) { _ -> }
                onSendMessage("admin", "I have submitted my availability. Please review it.") { _ -> }
                scope.launch { snackbarHostState.showSnackbar("Availability sent to admin!") }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save & Send to Admin") }
    }
}

@Composable
fun AvailabilityTable(
    days: List<String>,
    timeSlots: List<String>,
    selectedSlots: MutableMap<String, MutableSet<String>>,
    isReadOnly: Boolean = false,
    commonOccupiedSlots: Map<String, Set<String>> = emptyMap()
) {
    var pendingCommonSlot by remember { mutableStateOf<Pair<String, String>?>(null) }

    val horizontalScrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(horizontalScrollState)) {
        Column(modifier = Modifier.border(1.dp, Color.Gray)) {
            Row(modifier = Modifier.height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.primaryContainer)) {
                Box(modifier = Modifier.width(80.dp).padding(8.dp)) {
                    Text("Time", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.Gray))
                days.forEach { day ->
                    Box(
                        modifier = Modifier
                            .width(65.dp)
                            .padding(8.dp)
                            .then(
                                if (!isReadOnly) Modifier.clickable {
                                    val current = selectedSlots[day] ?: mutableSetOf()
                                    selectedSlots[day] = if (current.size == timeSlots.size) mutableSetOf() else timeSlots.toMutableSet()
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (!isReadOnly) MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                    }
                }
            }
            LazyColumn(modifier = Modifier.heightIn(max = 500.dp)) {
                items(timeSlots) { slot ->
                    Row(modifier = Modifier.height(IntrinsicSize.Min).border(0.5.dp, Color.LightGray)) {
                        Box(modifier = Modifier.width(80.dp).padding(8.dp).background(Color(0xFFF5F5F5))) {
                            Text(slot, fontSize = 10.sp)
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.LightGray))
                        days.forEach { day ->
                            val isSelected = selectedSlots[day]?.contains(slot) == true
                            val isCommon = commonOccupiedSlots[day]?.contains(slot) == true
                            val bgColor = when {
                                isSelected && isCommon -> Color(0xFFFF6F00).copy(alpha = 0.35f)
                                isSelected            -> Color.Green.copy(alpha = 0.3f)
                                isCommon              -> Color(0xFFFF6F00).copy(alpha = 0.15f)
                                else                  -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .width(65.dp)
                                    .height(40.dp)
                                    .border(0.5.dp, Color.LightGray)
                                    .background(bgColor)
                                    .clickable(enabled = !isReadOnly) {
                                        if (isCommon && !isSelected) {
                                            pendingCommonSlot = day to slot
                                        } else {
                                            val currentSet = selectedSlots[day] ?: mutableSetOf()
                                            val newSet = currentSet.toMutableSet()
                                            if (isSelected) newSet.remove(slot) else newSet.add(slot)
                                            selectedSlots[day] = newSet
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    if (pendingCommonSlot != null) {
        val (pDay, pSlot) = pendingCommonSlot!!
        AlertDialog(
            onDismissRequest = { pendingCommonSlot = null },
            title = { Text("Common Course Time Slot") },
            text = {
                Text("A common course is scheduled at this time. Same-semester students will be occupied. Do you still want to mark it as available?")
            },
            confirmButton = {
                TextButton(onClick = {
                    val currentSet = selectedSlots[pDay] ?: mutableSetOf()
                    val newSet = currentSet.toMutableSet()
                    newSet.add(pSlot)
                    selectedSlots[pDay] = newSet
                    pendingCommonSlot = null
                }) { Text("Select Anyway") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCommonSlot = null }) { Text("Cancel") }
            }
        )
    }
}
