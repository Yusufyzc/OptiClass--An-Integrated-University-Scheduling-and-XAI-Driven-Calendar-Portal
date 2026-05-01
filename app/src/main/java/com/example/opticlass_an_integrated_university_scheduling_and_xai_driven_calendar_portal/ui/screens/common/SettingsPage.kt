package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.ChangePasswordRequest
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.RetrofitClient
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.UserDto
import kotlinx.coroutines.launch

@Composable
fun SettingsPage(userName: String, viewModel: AppViewModel) {
    var userDto by remember { mutableStateOf<UserDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }

    // Gerçek uygulamada API'ye upload edilir, şimdilik UI'da anlık göstermek için local tutuyoruz
    var localAvatarUri by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            localAvatarUri = uri.toString()
        }
    }

    // Sayfa açıldığında API'den kullanıcı verilerini çek
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val response = RetrofitClient.instance.getUsers("Bearer ${viewModel.authToken}")
            if (response.isSuccessful) {
                userDto = response.body()?.find { it.username == userName }
            }
        } catch (e: Exception) {
            Log.e("SettingsPage", "Kullanıcı verisi çekilemedi", e)
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()

                    userDto?.let { user ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                UserAvatar(
                                    fullName = user.fullName,
                                    username = user.username,
                                    avatarUri = localAvatarUri, // Güncellenmiş UI resmi
                                    size = 64.dp
                                )
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable { imagePickerLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Change photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(user.fullName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("@${decodeUsername(user.username)}", fontSize = 13.sp, color = Color.Gray)

                                val dept = user.department ?: ""
                                if (dept.isNotBlank()) {
                                    Text(dept, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        user.role,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                        if (!user.email.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                                Spacer(Modifier.width(8.dp))
                                Text(user.email, fontSize = 13.sp)
                            }
                        }
                        OutlinedButton(
                            onClick = { showChangePasswordDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Change Password")
                        }
                    }
                }
            }

            if (snackbarMessage.isNotEmpty()) {
                Text(
                    text = snackbarMessage,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("OptiClass v1.0")
                Text(
                    "University Scheduling & XAI-Driven Calendar Portal",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            userName = userName,
            viewModel = viewModel,
            onDismiss = { showChangePasswordDialog = false },
            onSuccess = {
                showChangePasswordDialog = false
                snackbarMessage = "Password successfully changed!"
            }
        )
    }
}

@Composable
fun ChangePasswordDialog(
    userName: String,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = if (isSaving) { {} } else onDismiss,
        title = { Text("Change Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = {
                    when {
                        currentPassword.isBlank() -> errorMsg = "Enter current password"
                        newPassword.length < 6 -> errorMsg = "New password must be at least 6 characters"
                        newPassword != confirmPassword -> errorMsg = "Passwords do not match"
                        newPassword == currentPassword -> errorMsg = "New password must be different from current"
                        else -> {
                            scope.launch {
                                isSaving = true
                                errorMsg = ""
                                try {
                                    val request = ChangePasswordRequest(
                                        currentPasswordHash = sha256(currentPassword),
                                        newPasswordHash = sha256(newPassword)
                                    )
                                    val token = "Bearer ${viewModel.authToken}"
                                    val response = RetrofitClient.instance.changePassword(token, userName, request)

                                    if (response.isSuccessful) {
                                        onSuccess()
                                    } else {
                                        // HTTP 400 dönerse (örneğin mevcut şifre yanlışsa) buraya düşer
                                        errorMsg = "Incorrect current password or server error."
                                    }
                                } catch (e: Exception) {
                                    Log.e("ChangePassword", "Error", e)
                                    errorMsg = "Network error occurred."
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    }
                }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            if (!isSaving) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}