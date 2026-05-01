package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GenericPage(title: String) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Welcome to $title", style = MaterialTheme.typography.headlineMedium)
    }
}
