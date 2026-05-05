package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MySchedulePage(userName: String) {
    val user = AppRepository.users.find { it.username == userName } ?: return

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Weekly Schedule", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        val scheduleScrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scheduleScrollState)) {
            Column(modifier = Modifier.border(1.dp, Color.Gray)) {
                Row(modifier = Modifier.height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.primaryContainer)) {
                    Box(modifier = Modifier.width(80.dp).padding(8.dp)) {
                        Text("Time", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.Gray))
                    DAYS.forEach { day ->
                        Box(modifier = Modifier.width(65.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                            Text(day, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 600.dp)) {
                    items(TIME_SLOTS) { slot ->
                        Row(modifier = Modifier.height(IntrinsicSize.Min).border(0.5.dp, Color.LightGray)) {
                            Box(modifier = Modifier.width(80.dp).padding(8.dp).background(Color(0xFFF5F5F5))) {
                                Text(slot, fontSize = 10.sp)
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.LightGray))
                            DAYS.forEach { day ->
                                val course = user.schedule[day]?.get(slot)
                                val isContinuation = course?.duration == -1
                                Box(
                                    modifier = Modifier
                                        .width(65.dp)
                                        .height(50.dp)
                                        .border(0.5.dp, Color.LightGray)
                                        .background(
                                            when {
                                                isContinuation -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                                course != null -> MaterialTheme.colorScheme.secondaryContainer
                                                else -> Color.Transparent
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (course != null) {
                                        val roomCode = course.classroomId?.let { id ->
                                            AppRepository.classrooms.find { it.id == id }?.roomCode
                                        }
                                        val alpha = if (isContinuation) 0.6f else 1f
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(course.code, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha))
                                            if (roomCode != null) {
                                                Text(roomCode, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha * 0.7f), textAlign = TextAlign.Center)
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
    }
}
