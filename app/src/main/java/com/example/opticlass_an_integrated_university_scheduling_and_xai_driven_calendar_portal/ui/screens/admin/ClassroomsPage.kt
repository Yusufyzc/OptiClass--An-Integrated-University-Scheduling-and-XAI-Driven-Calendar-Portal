package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.ClassroomDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.util.UUID

@Composable
fun ClassroomsPage(snackbarHostState: SnackbarHostState, viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // UI State
    var classrooms by remember { mutableStateOf<List<ClassroomDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isImporting by remember { mutableStateOf(false) }
    var previewList by remember { mutableStateOf<List<ClassroomDto>>(emptyList()) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // API'den derslikleri çeken fonksiyon
    val loadClassrooms = {
        scope.launch {
            isLoading = true
            try {
                val token = "Bearer ${viewModel.authToken}"
                val response = RetrofitClient.instance.getClassrooms(token)
                if (response.isSuccessful) {
                    classrooms = response.body() ?: emptyList()
                } else {
                    Log.e("ClassroomsPage", "Hata: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("ClassroomsPage", "Derslikler çekilirken hata", e)
            } finally {
                isLoading = false
            }
        }
    }

    // Sayfa ilk açıldığında listeyi yükle
    LaunchedEffect(Unit) {
        loadClassrooms()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            if (previewList.isNotEmpty()) {
                pendingUri = uri
            } else {
                scope.launch {
                    isImporting = true
                    val result = importClassroomData(context, uri)
                    isImporting = false
                    if (result != null) {
                        previewList = result
                        snackbarHostState.showSnackbar("${result.size} classrooms loaded. Review and save.")
                    } else {
                        snackbarHostState.showSnackbar("Failed to parse Excel file. Check format.")
                    }
                }
            }
        }
    }

    if (pendingUri != null) {
        AlertDialog(
            onDismissRequest = { pendingUri = null },
            title = { Text("Replace Preview?") },
            text = { Text("You have ${previewList.size} unsaved item(s). Loading a new file will discard them. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingUri!!
                    pendingUri = null
                    scope.launch {
                        isImporting = true
                        val result = importClassroomData(context, uri)
                        isImporting = false
                        if (result != null) {
                            previewList = result
                            snackbarHostState.showSnackbar("${result.size} classrooms loaded. Review and save.")
                        } else {
                            snackbarHostState.showSnackbar("Failed to parse Excel file. Check format.")
                        }
                    }
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { pendingUri = null }) { Text("Cancel") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Classrooms", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                OutlinedButton(onClick = { showAddDialog = true }, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Manual")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Excel Format Info Card (Department çıkarıldı)
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Expected Excel Format", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                            .padding(4.dp)
                    ) {
                        listOf("Room Code", "Capacity").forEach {
                            Text(it, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (previewList.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Preview (${previewList.size} items)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { previewList = emptyList() }) { Text("Discard") }
                                Button(onClick = {
                                    scope.launch {
                                        isImporting = true
                                        var savedCount = 0
                                        val token = "Bearer ${viewModel.authToken}"

                                        previewList.forEach { c ->
                                            try {
                                                val response = RetrofitClient.instance.addClassroom(token, c)
                                                if (response.isSuccessful) savedCount++
                                            } catch (e: Exception) {
                                                Log.e("API", "Derslik eklenemedi", e)
                                            }
                                        }

                                        // API'den güncel listeyi çek
                                        loadClassrooms()

                                        isImporting = false
                                        previewList = emptyList()
                                        snackbarHostState.showSnackbar("$savedCount classrooms saved to server.")
                                    }
                                }) { Text("Save All") }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (classrooms.isEmpty() && previewList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(Modifier.height(16.dp))
                        Text("No classrooms yet.\nFetch or tap + to import.", textAlign = TextAlign.Center, color = Color.Gray)
                        Button(onClick = { loadClassrooms() }) { Text("Refresh from Server") }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(classrooms) { classroom ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(classroom.roomCode, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text("Capacity: ${classroom.capacity}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isImporting) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        FloatingActionButton(
            onClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Upload, contentDescription = "Import Classrooms from Excel")
        }
    }

    if (showAddDialog) {
        AddClassroomDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { classroomDto ->
                scope.launch {
                    try {
                        val token = "Bearer ${viewModel.authToken}"
                        val response = RetrofitClient.instance.addClassroom(token, classroomDto)

                        if (response.isSuccessful) {
                            loadClassrooms() // Listeyi yenile
                            snackbarHostState.showSnackbar("Classroom '${classroomDto.roomCode}' added to server.")
                        } else {
                            snackbarHostState.showSnackbar("Error: ${response.code()}")
                        }
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Network error: ${e.localizedMessage}")
                    }
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddClassroomDialog(onDismiss: () -> Unit, onAdd: (ClassroomDto) -> Unit) {
    var roomCode by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Classroom") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = roomCode, onValueChange = { roomCode = it }, label = { Text("Room Code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = capacity, onValueChange = { capacity = it }, label = { Text("Capacity") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (roomCode.isBlank()) {
                    errorMsg = "Room Code is required"
                } else {
                    val dto = ClassroomDto(
                        id = UUID.randomUUID().toString(), // Benzersiz ID oluştur
                        roomCode = roomCode.uppercase(),
                        capacity = capacity.toIntOrNull() ?: 0
                    )
                    onAdd(dto)
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// Excel dosyasını direkt ClassroomDto objesine dönüştürür
private suspend fun importClassroomData(context: Context, uri: Uri): List<ClassroomDto>? {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext null
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            val rows = sheet.iterator()
            val formatter = DataFormatter()

            // Başlık satırını atla
            if (rows.hasNext()) rows.next()

            val list = mutableListOf<ClassroomDto>()
            while (rows.hasNext()) {
                val row = rows.next()
                val roomCode = formatter.formatCellValue(row.getCell(0)).trim()
                val capacityStr = formatter.formatCellValue(row.getCell(1)).trim()

                if (roomCode.isNotEmpty()) {
                    list.add(
                        ClassroomDto(
                            id = UUID.randomUUID().toString(),
                            roomCode = roomCode,
                            capacity = capacityStr.toIntOrNull() ?: 0
                        )
                    )
                }
            }
            workbook.close()
            inputStream.close()
            list
        } catch (e: Exception) {
            null
        }
    }
}