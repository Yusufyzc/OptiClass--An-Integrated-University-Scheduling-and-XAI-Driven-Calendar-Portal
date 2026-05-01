package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.RetrofitClient
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.UserCreateDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

@Composable
fun DataImportPage(snackbarHostState: SnackbarHostState, viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var newCredentials by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                val success = importExcelData(context, uri)
                isImporting = false
                if (success) {
                    snackbarHostState.showSnackbar("Excel data imported! Review and save below.")
                } else {
                    snackbarHostState.showSnackbar("Failed to parse Excel file.")
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Course Data Import", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(16.dp))

            if (AppRepository.courseImports.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(Modifier.height(16.dp))
                        Text("Tap + to select an Excel file.", color = Color.Gray)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Preview (${AppRepository.courseImports.size} items)", fontWeight = FontWeight.Bold)
                    Button(onClick = {
                        scope.launch {
                            isImporting = true
                            val credentials = mutableListOf<Pair<String, String>>()
                            AppRepository.courseImports.toList().forEach { course ->
                                val pass = generatePassword()
                                val un = course.email.substringBefore("@")
                                try {
                                    val response = RetrofitClient.instance.addUser(
                                        viewModel.authToken,
                                        UserCreateDto(
                                            username = un,
                                            passwordHash = sha256(pass),
                                            role = "INSTRUCTOR",
                                            fullName = course.lecturer,
                                            email = course.email,
                                            department = course.department
                                        )
                                    )
                                    if (response.isSuccessful) credentials.add(un to pass)
                                } catch (e: Exception) { Log.e("Import", "Error", e) }
                            }
                            AppRepository.courseImports.clear()
                            newCredentials = credentials
                            isImporting = false
                            snackbarHostState.showSnackbar("Import completed.")
                        }
                    }) {
                        Text("Save All & Create Accounts")
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppRepository.courseImports) { course ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(course.name, fontWeight = FontWeight.Bold)
                                    Text(course.lecturer, fontSize = 12.sp)
                                }
                                IconButton(onClick = { AppRepository.courseImports.remove(course) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add")
        }
    }

    if (newCredentials.isNotEmpty()) {
        CredentialsDialog(credentials = newCredentials, onDismiss = { newCredentials = emptyList() })
    }
}

private suspend fun importExcelData(context: Context, uri: Uri): Boolean {
    return withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext false
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            val rows = sheet.iterator()
            val formatter = DataFormatter()
            if (rows.hasNext()) rows.next()
            val importedList = mutableListOf<CourseImport>()
            while (rows.hasNext()) {
                val row = rows.next()
                val code = formatter.formatCellValue(row.getCell(0)).trim()
                val name = formatter.formatCellValue(row.getCell(1)).trim()
                val lecturer = formatter.formatCellValue(row.getCell(2)).trim()
                val department = formatter.formatCellValue(row.getCell(3)).trim()
                val email = formatter.formatCellValue(row.getCell(4)).trim()
                if (code.isNotEmpty() && name.isNotEmpty()) {
                    importedList.add(CourseImport(code, name, lecturer, department, email))
                }
            }
            withContext(Dispatchers.Main) {
                AppRepository.courseImports.clear()
                AppRepository.courseImports.addAll(importedList)
            }
            workbook.close()
            true
        } catch (e: Exception) { 
            false 
        } finally {
            inputStream?.close()
        }
    }
}
