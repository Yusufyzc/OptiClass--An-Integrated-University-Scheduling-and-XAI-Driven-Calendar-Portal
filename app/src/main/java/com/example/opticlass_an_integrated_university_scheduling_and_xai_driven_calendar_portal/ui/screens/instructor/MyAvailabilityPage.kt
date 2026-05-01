package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.util.Log
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
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.AvailabilityDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.RetrofitClient
import kotlinx.coroutines.launch

@Composable
fun MyAvailabilityPage(userName: String, snackbarHostState: SnackbarHostState, viewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // State'i MutableMap<String, Set<String>> olarak tutalım, tablo ile uyumlu olsun
    val selectedSlots = remember { mutableStateMapOf<String, Set<String>>() }

    LaunchedEffect(Unit) {
        try {
            val response = RetrofitClient.instance.getAvailability(viewModel.authToken, userName)
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.slots.forEach { (day, slots) ->
                    selectedSlots[day] = slots.toSet()
                }
            }
        } catch (e: Exception) {
            Log.e("MyAvailability", "Veri çekilirken hata: ", e)
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Availability", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Tabloyu göster
            AvailabilityTable(
                days = DAYS,
                timeSlots = TIME_SLOTS,
                selectedSlots = selectedSlots
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        try {
                            val dto = AvailabilityDto(
                                instructorUsername = userName,
                                slots = selectedSlots.mapValues { it.value.toList() }
                            )
                            val response = RetrofitClient.instance.submitAvailability(viewModel.authToken, userName, dto)

                            if (response.isSuccessful) {
                                snackbarHostState.showSnackbar("Availability successfully saved!")
                                Log.d("MyAvailability", "Veri başarıyla kaydedildi")
                            } else {
                                snackbarHostState.showSnackbar("Error saving data: ${response.code()}")
                            }
                        } catch (e: Exception) {
                            Log.e("MyAvailability", "Kaydedilirken hata: ", e)
                            snackbarHostState.showSnackbar("Network error!")
                        } finally {
                            isSaving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text("Save Availability")
                }
            }
        }
    }
}

@Composable
fun AvailabilityTable(
    days: List<String>,
    timeSlots: List<String>,
    selectedSlots: MutableMap<String, Set<String>>,
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
                            .width(85.dp)
                            .padding(8.dp)
                            .then(
                                if (!isReadOnly) Modifier.clickable {
                                    val current = selectedSlots[day] ?: emptySet()
                                    selectedSlots[day] = if (current.size == timeSlots.size) emptySet() else timeSlots.toSet()
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
                                    .width(85.dp).height(40.dp).border(0.5.dp, Color.LightGray)
                                    .background(if (isSelected) Color.Green.copy(alpha = 0.3f) else Color.Transparent)
                                    .clickable(enabled = !isReadOnly) {
                                        val currentSet = selectedSlots[day] ?: emptySet()
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
