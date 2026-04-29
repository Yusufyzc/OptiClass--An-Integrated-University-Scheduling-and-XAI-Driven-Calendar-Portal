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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    var selectedDepartment by remember { mutableStateOf<String?>(null) }
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
                        scope.launch { snackbarHostState.showSnackbar("Failed to parse Excel file. Check format.") }
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
                            scope.launch { snackbarHostState.showSnackbar("Failed to parse Excel file. Check format.") }
                        }
                    }
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { pendingUri = null }) { Text("Cancel") }
            }
        )
    }

    val departments = globalClassrooms.map { it.department }.distinct().sorted()
    val filteredClassrooms = if (selectedDepartment == null) globalClassrooms.toList()
                             else globalClassrooms.filter { it.department == selectedDepartment }

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
                        listOf("Room Code", "Capacity", "Department").forEach {
                            Text(it, modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp)) {
                        listOf("A101", "40", "Computer Science").forEach {
                            Text(it, modifier = Modifier.weight(1f), fontSize = 9.sp, textAlign = TextAlign.Center, color = Color.Gray)
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
                                    val toAdd = previewList.filter { new ->
                                        globalClassrooms.none { it.roomCode == new.roomCode }
                                    }
                                    globalClassrooms.addAll(toAdd)
                                    val skipped = previewList.size - toAdd.size
                                    previewList = emptyList()
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (skipped > 0) "${toAdd.size} saved, $skipped duplicate(s) skipped."
                                            else "${toAdd.size} classrooms saved."
                                        )
                                    }
                                }) { Text("Save All") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                            previewList.forEach { classroom ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(classroom.roomCode, fontWeight = FontWeight.Medium)
                                    Text(classroom.department, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            if (departments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedDepartment == null,
                            onClick = { selectedDepartment = null },
                            label = { Text("All") }
                        )
                    }
                    items(departments) { dept ->
                        FilterChip(
                            selected = selectedDepartment == dept,
                            onClick = { selectedDepartment = if (selectedDepartment == dept) null else dept },
                            label = { Text(dept) }
                        )
                    }
                }
            }

            if (globalClassrooms.isEmpty() && previewList.isEmpty()) {
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
                    items(filteredClassrooms) { classroom ->
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
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(classroom.department, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                                }
                                IconButton(onClick = { globalClassrooms.remove(classroom) }) {
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
                if (globalClassrooms.any { it.roomCode.equals(classroom.roomCode, ignoreCase = true) }) {
                    scope.launch { snackbarHostState.showSnackbar("Room code '${classroom.roomCode}' already exists.") }
                } else {
                    globalClassrooms.add(classroom)
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
    var department by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Classroom") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = roomCode, onValueChange = { roomCode = it }, label = { Text("Room Code (e.g. A101)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = capacity, onValueChange = { capacity = it }, label = { Text("Capacity") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                OutlinedTextField(value = department, onValueChange = { department = it }, label = { Text("Department / Building") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    roomCode.isBlank() -> errorMsg = "Room code is required"
                    department.isBlank() -> errorMsg = "Department is required"
                    else -> onAdd(
                        Classroom(
                            id = "room_${System.currentTimeMillis()}",
                            roomCode = roomCode.trim().uppercase(),
                            capacity = capacity.toIntOrNull() ?: 0,
                            department = department.trim()
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

            if (rows.hasNext()) rows.next()

            val list = mutableListOf<Classroom>()
            var index = 0
            while (rows.hasNext()) {
                val row = rows.next()
                val roomCode = formatter.formatCellValue(row.getCell(0)).trim()
                val capacityStr = formatter.formatCellValue(row.getCell(1)).trim()
                val department = formatter.formatCellValue(row.getCell(2)).trim()
                if (roomCode.isNotEmpty() && department.isNotEmpty()) {
                    val capacity = capacityStr.toIntOrNull() ?: 0
                    list.add(Classroom(id = "room_${System.currentTimeMillis()}_${index++}", roomCode = roomCode, capacity = capacity, department = department))
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
