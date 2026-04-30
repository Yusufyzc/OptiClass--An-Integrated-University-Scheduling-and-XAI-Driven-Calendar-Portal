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
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

@Composable
fun ClassroomsPage(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var previewList by remember { mutableStateOf<List<Classroom>>(emptyList()) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

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
                        scope.launch { snackbarHostState.showSnackbar("${result.size} classrooms loaded. Review and save.") }
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Wrong file format. Expected: Classroom Code | Capacity") }
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
                            scope.launch { snackbarHostState.showSnackbar("${result.size} classrooms loaded. Review and save.") }
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("Wrong file format. Expected: Classroom Code | Capacity") }
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
                            Text(it, modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp)) {
                        listOf("A101", "40").forEach {
                            Text(it, modifier = Modifier.weight(1f), fontSize = 9.sp, textAlign = TextAlign.Center, color = Color.Gray)
                        }
                    }
                }
            }

            if (previewList.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Preview (${previewList.size} items)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = {
                            val toAdd = previewList.filter { new ->
                                AppRepository.classrooms.none { it.roomCode == new.roomCode }
                            }
                            AppRepository.classrooms.addAll(toAdd)
                            val skipped = previewList.size - toAdd.size
                            previewList = emptyList()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (skipped > 0) "${toAdd.size} saved, $skipped duplicate(s) skipped."
                                    else "${toAdd.size} classrooms saved."
                                )
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save All")
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(previewList) { classroom ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(classroom.roomCode, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    if (classroom.capacity > 0) {
                                        Text("Capacity: ${classroom.capacity}", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                                IconButton(onClick = { previewList = previewList - classroom }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Discard", tint = MaterialTheme.colorScheme.error)
                                }
                                IconButton(
                                    onClick = {
                                        val isDuplicate = AppRepository.classrooms.any { it.roomCode == classroom.roomCode }
                                        previewList = previewList - classroom
                                        if (isDuplicate) {
                                            scope.launch { snackbarHostState.showSnackbar("'${classroom.roomCode}' already exists, skipped.") }
                                        } else {
                                            AppRepository.classrooms.add(classroom)
                                            scope.launch { snackbarHostState.showSnackbar("'${classroom.roomCode}' saved.") }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            if (AppRepository.classrooms.isEmpty() && previewList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(Modifier.height(16.dp))
                        Text("No classrooms yet.\nTap + to import from Excel.", textAlign = TextAlign.Center, color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(AppRepository.classrooms.toList()) { classroom ->
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
                                        if (classroom.capacity > 0) {
                                            Text("Capacity: ${classroom.capacity}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                    }
                                }
                                IconButton(onClick = { AppRepository.classrooms.remove(classroom) }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isImporting) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
            onAdd = { classroom ->
                if (AppRepository.classrooms.any { it.roomCode.equals(classroom.roomCode, ignoreCase = true) }) {
                    scope.launch { snackbarHostState.showSnackbar("Room code '${classroom.roomCode}' already exists.") }
                } else {
                    AppRepository.classrooms.add(classroom)
                    scope.launch { snackbarHostState.showSnackbar("Classroom '${classroom.roomCode}' added.") }
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddClassroomDialog(onDismiss: () -> Unit, onAdd: (Classroom) -> Unit) {
    var roomCode by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Classroom") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = roomCode, onValueChange = { roomCode = it }, label = { Text("Room Code (e.g. A101)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = capacity, onValueChange = { capacity = it }, label = { Text("Capacity") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    roomCode.isBlank() -> errorMsg = "Room code is required"
                    else -> onAdd(
                        Classroom(
                            id = "room_${System.currentTimeMillis()}",
                            roomCode = roomCode.trim().uppercase(),
                            capacity = capacity.toIntOrNull() ?: 0
                        )
                    )
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private suspend fun importClassroomData(context: Context, uri: Uri): List<Classroom>? {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext null

            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            val rows = sheet.iterator()
            val formatter = DataFormatter()

            if (!rows.hasNext()) return@withContext null
            val headerRow = rows.next()
            val nonEmptyCols = (0 until headerRow.lastCellNum).count {
                formatter.formatCellValue(headerRow.getCell(it)).trim().isNotEmpty()
            }
            val h0 = formatter.formatCellValue(headerRow.getCell(0)).trim().lowercase()
            val h1 = formatter.formatCellValue(headerRow.getCell(1)).trim().lowercase()
            val validHeader = nonEmptyCols == 2 &&
                              (h0 == "classroom code" || h0 == "room code") &&
                              h1 == "capacity"
            if (!validHeader) return@withContext null

            val list = mutableListOf<Classroom>()
            var index = 0
            while (rows.hasNext()) {
                val row = rows.next()
                val roomCode = formatter.formatCellValue(row.getCell(0)).trim()
                val capacityStr = formatter.formatCellValue(row.getCell(1)).trim()
                if (roomCode.isNotEmpty()) {
                    val capacity = capacityStr.toIntOrNull() ?: 0
                    list.add(Classroom(id = "room_${System.currentTimeMillis()}_${index++}", roomCode = roomCode, capacity = capacity))
                }
            }
            workbook.close()
            inputStream.close()
            list
        } catch (e: Exception) {
            Log.e("ClassroomImport", "Error: ${e.message}")
            null
        }
    }
}
