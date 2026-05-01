package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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
fun DataImportPage(
    snackbarHostState: SnackbarHostState,
    onImport: (List<CourseImport>, (List<Pair<String, String>>) -> Unit) -> Unit = { _, _ -> },
    onDeleteCourse: (String, (Boolean) -> Unit) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var previewList by remember { mutableStateOf<List<CourseImport>>(emptyList()) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var newCredentials by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var courseToDelete by remember { mutableStateOf<CourseImport?>(null) }
    var showSavedCourses by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            if (previewList.isNotEmpty()) {
                pendingImportUri = uri
            } else {
                scope.launch {
                    isImporting = true
                    val result = importExcelData(context, uri)
                    isImporting = false
                    if (result != null) {
                        previewList = result
                        snackbarHostState.showSnackbar("Excel data imported! Review and save below.")
                    } else {
                        snackbarHostState.showSnackbar("Failed to parse Excel file. Check format.")
                    }
                }
            }
        }
    }

    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Replace Current Preview?") },
            text = { Text("You have ${previewList.size} unsaved item(s) in the preview. Loading a new file will discard them. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImportUri!!
                    pendingImportUri = null
                    scope.launch {
                        isImporting = true
                        val result = importExcelData(context, uri)
                        isImporting = false
                        if (result != null) {
                            previewList = result
                            snackbarHostState.showSnackbar("Excel data imported! Review and save below.")
                        } else {
                            snackbarHostState.showSnackbar("Failed to parse Excel file. Check format.")
                        }
                    }
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Course Data Import", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
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
                        listOf("Code", "Name", "Lecturer", "Dept", "Email").forEach {
                            Text(it, modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp)) {
                        listOf("C101", "Intro...", "John D.", "CS", "john@...").forEach {
                            Text(it, modifier = Modifier.weight(1f), fontSize = 9.sp, textAlign = TextAlign.Center, color = Color.Gray)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Instructor accounts are automatically created. Username is derived from the instructor's name; password is a random 6-character code shown once after saving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            SavedCoursesSection(onDeleteCourse = { course -> courseToDelete = course })
            Spacer(Modifier.height(12.dp))

            if (previewList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No records to display.\nTap the + button to select an Excel file.",
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Preview (${previewList.size} items)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = {
                            val listToSave = previewList
                            previewList = emptyList()
                            onImport(listToSave) { created ->
                                if (created.isNotEmpty()) newCredentials = created
                                else scope.launch { snackbarHostState.showSnackbar("All instructors and courses saved!") }
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save All")
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(previewList) { course ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                course.code,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(course.name, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                        Spacer(Modifier.width(4.dp))
                                        Text(course.lecturer, fontSize = 13.sp, color = Color.DarkGray)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                        Spacer(Modifier.width(4.dp))
                                        Text(course.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                                IconButton(onClick = { previewList = previewList - course }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Discard", tint = MaterialTheme.colorScheme.error)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable {
                                            previewList = previewList - course
                                            onImport(listOf(course)) { created ->
                                                if (created.isNotEmpty()) newCredentials = created
                                                else scope.launch { snackbarHostState.showSnackbar("Saved ${course.lecturer}") }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { if (!isImporting) filePickerLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            shape = CircleShape,
            containerColor = if (isImporting) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(8.dp)
        ) {
            if (isImporting) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Add, contentDescription = "Select Excel File", tint = Color.White)
            }
        }
    }

    if (newCredentials.isNotEmpty()) {
        CredentialsDialog(credentials = newCredentials, onDismiss = { newCredentials = emptyList() })
    }

    if (courseToDelete != null) {
        AlertDialog(
            onDismissRequest = { courseToDelete = null },
            title = { Text("Delete Course") },
            text = { Text("\"${courseToDelete!!.code} - ${courseToDelete!!.name}\" silinecek. Emin misin?") },
            confirmButton = {
                TextButton(onClick = {
                    val code = courseToDelete!!.code
                    courseToDelete = null
                    onDeleteCourse(code) { success ->
                        scope.launch {
                            snackbarHostState.showSnackbar(if (success) "Course deleted." else "Failed to delete.")
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { courseToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SavedCoursesSection(
    onDeleteCourse: (CourseImport) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val savedCourses = AppRepository.courseImports

    Card(
        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = androidx.compose.ui.Modifier.padding(12.dp)) {
            Row(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Saved Courses (${savedCourses.size})",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
            if (expanded) {
                Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                if (savedCourses.isEmpty()) {
                    Text("No saved courses.", color = Color.Gray, fontSize = 13.sp)
                } else {
                    savedCourses.forEach { course ->
                        Row(
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(4.dp),
                                modifier = androidx.compose.ui.Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    course.code,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = androidx.compose.ui.Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                                Text(course.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(course.lecturer, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                            }
                            IconButton(onClick = { onDeleteCourse(course) }, modifier = androidx.compose.ui.Modifier.size(32.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = androidx.compose.ui.Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

private suspend fun importExcelData(context: Context, uri: Uri): List<CourseImport>? {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext null

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

                if (code.isNotEmpty() && name.isNotEmpty() && isValidEmail(email)) {
                    importedList.add(CourseImport(code, name, lecturer, department, email))
                }
            }

            workbook.close()
            inputStream.close()
            importedList
        } catch (e: Exception) {
            Log.e("ExcelImport", "Error: ${e.message}")
            null
        }
    }
}
