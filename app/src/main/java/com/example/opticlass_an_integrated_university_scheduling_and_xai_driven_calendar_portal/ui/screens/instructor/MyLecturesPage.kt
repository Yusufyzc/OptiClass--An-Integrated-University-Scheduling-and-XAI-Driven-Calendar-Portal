package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.CourseDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.RetrofitClient

@Composable
fun MyLecturesPage(userName: String, viewModel: AppViewModel) {
    // API'den gelecek dersleri tutacağımız state
    var lectures by remember { mutableStateOf<List<CourseDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Sayfa açıldığında dersleri API'den çek
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val token = "Bearer ${viewModel.authToken}"
            // Tüm dersleri getir
            val response = RetrofitClient.instance.getCourses(token)

            if (response.isSuccessful) {
                val allCourses = response.body() ?: emptyList()
                // Sadece sisteme giriş yapan eğitmene (userName) ait olanları filtrele
                lectures = allCourses.filter { it.lecturerUsername == userName }
            } else {
                Log.e("MyLecturesPage", "API Hatası: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e("MyLecturesPage", "Dersler çekilirken hata oluştu", e)
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Lectures", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            // Veri yüklenirken dönen animasyon
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (lectures.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("You have no assigned lectures yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(lectures) { lecture ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        lecture.code,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(lecture.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Department: ${lecture.department}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}