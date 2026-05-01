package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.ui.screens

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
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.AppViewModel
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.CourseDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.RetrofitClient
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.UserCreateDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.security.MessageDigest

// Excel okurken Full Name kaybolmasın diye geçici veri sınıfı
data class CourseImportTemp(
    val code: String,
    val name: String,
    val lecturer: String,
    val department: String,
    val email: String
)

@Composable
fun DataImportPage(snackbarHostState: SnackbarHostState, viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isImporting by remember { mutableStateOf(false) }
    var previewList by remember { mutableStateOf<List<CourseImportTemp>>(emptyList()) }
    var newCredentials by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isImporting = true
                val result = importExcelData(context, uri)
                isImporting = false
                if (result != null) {
                    previewList = result
                    snackbarHostState.showSnackbar("${result.size} courses loaded! Review and save below.")
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

            if (previewList.isEmpty()) {
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Preview (${previewList.size} items)", fontWeight = FontWeight.Bold)
                    Button(
                        enabled = !isImporting,
                        onClick = {
                            scope.launch {
                                isImporting = true
                                val credentials = mutableListOf<Pair<String, String>>()

                                // TokenStore üzerinden token alınıyor
                                val token = "Bearer ${TokenStore.token ?: ""}"

                                // 1. Aynı e-postaya sahip eğitmenleri filtrele (aynı kişiyi 2 kere eklememek için)
                                val uniqueInstructors = previewList.distinctBy { it.email.substringBefore("@") }

                                for (course in uniqueInstructors) {
                                    if (course.email.isNotBlank()) {
                                        val pass = generateRandomPassword()
                                        val un = course.email.substringBefore("@")
                                        try {
                                            // UserDto yerine UserCreateDto kullanıyoruz
                                            val response = RetrofitClient.instance.addUser(
                                                token,
                                                UserCreateDto(
                                                    username = un,
                                                    passwordHash = sha256(pass),
                                                    role = "INSTRUCTOR",
                                                    fullName = course.lecturer,
                                                    email = course.email,
                                                    department = course.department
                                                )
                                            )
                                            // Eğer 200 dönerse kullanıcı oluşturuldu demektir, şifresini göster.
                                            if (response.isSuccessful) credentials.add(un to pass)
                                        } catch (e: Exception) { Log.e("Import", "Kullanıcı oluşturulamadı", e) }
                                    }
                                }

                                // 2. Tüm dersleri CourseDto'ya çevir
                                val coursesToImport = previewList.map { c ->
                                    val un = if (c.email.isNotBlank()) c.email.substringBefore("@") else null
                                    CourseDto(
                                        code = c.code,
                                        name = c.name,
                                        lecturerUsername = un,
                                        department = c.department,
                                        email = c.email,
                                        duration = 1,
                                        classroomId = null
                                    )
                                }

                                // 3. Dersleri topluca veritabanına gönder
                                try {
                                    val bulkRes = RetrofitClient.instance.importCourses(token, coursesToImport)
                                    if (bulkRes.isSuccessful) {
                                        previewList = emptyList() // İşlem bitti, listeyi temizle
                                        newCredentials = credentials
                                        snackbarHostState.showSnackbar("Import completed successfully.")
                                    } else {
                                        snackbarHostState.showSnackbar("Error importing courses: ${bulkRes.code()}")
                                    }
                                } catch (e: Exception) {
                                    Log.e("Import", "Toplu ders aktarım hatası", e)
                                    snackbarHostState.showSnackbar("Network error during course import.")
                                }

                                isImporting = false
                            }
                        }
                    ) {
                        Text("Save All & Create Accounts")
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(previewList) { course ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(course.code + " - " + course.name, fontWeight = FontWeight.Bold)
                                    Text(course.lecturer, fontSize = 12.sp)
                                }
                                IconButton(onClick = {
                                    previewList = previewList.filter { it != course }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add")
        }
    }

    if (newCredentials.isNotEmpty()) {
        CredentialsDialog(credentials = newCredentials, onDismiss = { newCredentials = emptyList() })
    }
}

private suspend fun importExcelData(context: Context, uri: Uri): List<CourseImportTemp>? {
    return withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext null
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            val rows = sheet.iterator()
            val formatter = DataFormatter()

            if (rows.hasNext()) rows.next() // Başlık satırını atla

            val importedList = mutableListOf<CourseImportTemp>()
            while (rows.hasNext()) {
                val row = rows.next()
                val code = formatter.formatCellValue(row.getCell(0)).trim()
                val name = formatter.formatCellValue(row.getCell(1)).trim()
                val lecturer = formatter.formatCellValue(row.getCell(2)).trim()
                val department = formatter.formatCellValue(row.getCell(3)).trim()
                val email = formatter.formatCellValue(row.getCell(4)).trim()

                if (code.isNotEmpty() && name.isNotEmpty()) {
                    importedList.add(CourseImportTemp(code, name, lecturer, department, email))
                }
            }
            workbook.close()
            importedList
        } catch (e: Exception) {
            null
        } finally {
            inputStream?.close()
        }
    }
}

@Composable
fun CredentialsDialog(credentials: List<Pair<String, String>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Accounts Created") },
        text = {
            Column {
                Text(
                    "Please save these generated passwords. They are securely hashed in the database and cannot be recovered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(credentials) { (username, password) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Username: $username", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Password: $password", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// SHA-256 Şifreleme Fonksiyonu
fun sha256(input: String): String {
    val bytes = input.toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.fold("") { str, it -> str + "%02x".format(it) }
}

// Rastgele Şifre Üretme Fonksiyonu
fun generateRandomPassword(length: Int = 8): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length).map { allowedChars.random() }.joinToString("")
}