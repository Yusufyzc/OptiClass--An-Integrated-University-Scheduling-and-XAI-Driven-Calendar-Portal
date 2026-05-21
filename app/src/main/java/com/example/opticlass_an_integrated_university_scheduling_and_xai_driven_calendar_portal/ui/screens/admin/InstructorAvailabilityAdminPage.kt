package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun InstructorAvailabilityAdminPage(onRefresh: () -> Unit = {}) {
    var selectedInstructor by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (selectedInstructor == null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Instructor Availabilities", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            val adminDept = AppRepository.currentUserDepartment
            val visibleAvailabilities = if (adminDept.isBlank()) {
                AppRepository.availabilities.toList()
            } else {
                AppRepository.availabilities.filter { av ->
                    AppRepository.courseImports.any { it.lecturer == av.instructorName && it.department == adminDept }
                }
            }
            if (visibleAvailabilities.isEmpty()) {
                Text("No submissions yet.")
            } else {
                LazyColumn {
                    items(visibleAvailabilities) { availability ->
                        val instructor = AppRepository.users.find { it.username == availability.instructorName }
                        val displayName = instructor?.fullName ?: availability.instructorName
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedInstructor = availability.instructorName },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "@${availability.instructorName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val availability = AppRepository.availabilities.find { it.instructorName == selectedInstructor }
            if (availability != null) {
                val instructor = AppRepository.users.find { it.username == availability.instructorName }
                TextButton(onClick = { selectedInstructor = null }) { Text("< Back to list") }
                Text(
                    "Availability for ${instructor?.fullName ?: availability.instructorName}",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                val readOnlySlots = remember(availability.instructorName) {
                    availability.slots.mapValues { it.value.toMutableSet() }.toMutableMap()
                }
                AvailabilityTable(
                    days = DAYS,
                    timeSlots = TIME_SLOTS,
                    selectedSlots = readOnlySlots,
                    isReadOnly = true
                )
            }
        }
    }
}
