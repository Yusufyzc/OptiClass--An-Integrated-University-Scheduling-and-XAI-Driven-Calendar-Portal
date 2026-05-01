package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.CourseDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.RetrofitClient

@Composable
fun MySchedulePage(userName: String, viewModel: AppViewModel) {
    // API'den gelecek veriler
    var isLoading by remember { mutableStateOf(true) }
    var scheduleMap by remember { mutableStateOf<Map<String, CourseDto>>(emptyMap()) }
    var classroomsMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // ID -> roomCode eşleşmesi

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val token = "Bearer ${viewModel.authToken}"

            // 1. Sınıfları çekelim (Derslik kodlarını göstermek için)
            val classRes = RetrofitClient.instance.getClassrooms(token)
            if (classRes.isSuccessful) {
                classroomsMap = classRes.body()?.associate { it.id to it.roomCode } ?: emptyMap()
            }

            // 2. Haftalık programı çekelim
            val schedRes = RetrofitClient.instance.getSchedule(token, userName)
            if (schedRes.isSuccessful) {
                val rawSlots = schedRes.body()?.slots ?: emptyMap()
                val computedSchedule = mutableMapOf<String, CourseDto>()

                rawSlots.forEach { (key, course) ->
                    if (course != null) {
                        // Orijinal dersi haritaya ekle (Örn key: "Mon_08:00 AM")
                        computedSchedule[key] = course

                        // Eğer ders süresi 1'den büyükse, altındaki saatleri doldur (-1 duration ile)
                        if (course.duration > 1) {
                            val parts = key.split("_")
                            if (parts.size == 2) {
                                val day = parts[0]
                                val time = parts[1]
                                val startIndex = TIME_SLOTS.indexOf(time)

                                if (startIndex != -1) {
                                    for (i in 1 until course.duration) {
                                        if (startIndex + i < TIME_SLOTS.size) {
                                            val nextTime = TIME_SLOTS[startIndex + i]
                                            // Continuation (Uzatma) slotu olarak kopyasını oluştur
                                            computedSchedule["${day}_${nextTime}"] = course.copy(duration = -1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                scheduleMap = computedSchedule
            }
        } catch (e: Exception) {
            Log.e("MySchedulePage", "Program veya sınıflar çekilirken hata oluştu", e)
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Weekly Schedule", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val scheduleScrollState = rememberScrollState()
            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scheduleScrollState)) {
                Column(modifier = Modifier.border(1.dp, Color.Gray)) {
                    // TABLO BAŞLIĞI (Günler)
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

                    // TABLO GÖVDESİ (Saatler ve Dersler)
                    LazyColumn(modifier = Modifier.heightIn(max = 600.dp)) {
                        items(TIME_SLOTS) { slot ->
                            Row(modifier = Modifier.height(IntrinsicSize.Min).border(0.5.dp, Color.LightGray)) {
                                Box(modifier = Modifier.width(80.dp).padding(8.dp).background(Color(0xFFF5F5F5))) {
                                    Text(slot, fontSize = 10.sp)
                                }
                                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.LightGray))

                                DAYS.forEach { day ->
                                    // Backend formatına uygun key ile çekiyoruz
                                    val key = "${day}_${slot}"
                                    val course = scheduleMap[key]
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
                                        if (!isContinuation && course != null) {
                                            // Sınıf ID'sini Room Code'a çevirip yazdırıyoruz
                                            val roomCode = course.classroomId?.let { classroomsMap[it] }
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(course.code, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                                if (roomCode != null) {
                                                    Text(roomCode, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f), textAlign = TextAlign.Center)
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
}