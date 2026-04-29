package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun MyAvailabilityPage(instructorName: String, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()

    val initialDraft = AppRepository.availabilityDrafts[instructorName] ?: emptyMap()
    val selectedSlots = remember {
        val map = mutableStateMapOf<String, MutableSet<String>>()
        DAYS.forEach { day -> map[day] = initialDraft[day]?.toMutableSet() ?: mutableSetOf() }
        map
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Your Availability", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        AvailabilityTable(
            days = DAYS,
            timeSlots = TIME_SLOTS,
            selectedSlots = selectedSlots,
            isReadOnly = false
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = {
                AppRepository.availabilityDrafts[instructorName] = selectedSlots.mapValues { it.value.toSet() }
                scope.launch { snackbarHostState.showSnackbar("Draft saved.") }
            }) { Text("Save Changes") }
            Button(onClick = {
                val availability = Availability(instructorName, selectedSlots.mapValues { it.value.toSet() })
                AppRepository.availabilities.removeAll { it.instructorName == instructorName }
                AppRepository.availabilities.add(availability)
                AppRepository.availabilityDrafts[instructorName] = availability.slots
                val adminUsername = AppRepository.users.find { it.role == UserRole.ADMIN }?.username ?: encodeUsername("admin")
                AppRepository.messages.add(
                    Message(
                        sender = instructorName,
                        recipient = adminUsername,
                        content = "I have submitted my availability. Please review it."
                    )
                )
                scope.launch { snackbarHostState.showSnackbar("Availability sent to admin!") }
            }) { Text("Send to Admin") }
        }
    }
}

@Composable
fun AvailabilityTable(
    days: List<String>,
    timeSlots: List<String>,
    selectedSlots: MutableMap<String, MutableSet<String>>,
    isReadOnly: Boolean = false
) {
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
                            Box(
                                modifier = Modifier
                                    .width(65.dp).height(40.dp).border(0.5.dp, Color.LightGray)
                                    .background(if (isSelected) Color.Green.copy(alpha = 0.3f) else Color.Transparent)
                                    .clickable(enabled = !isReadOnly) {
                                        val currentSet = selectedSlots[day] ?: mutableSetOf()
                                        val newSet = currentSet.toMutableSet()
                                        if (isSelected) newSet.remove(slot) else newSet.add(slot)
                                        selectedSlots[day] = newSet
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
