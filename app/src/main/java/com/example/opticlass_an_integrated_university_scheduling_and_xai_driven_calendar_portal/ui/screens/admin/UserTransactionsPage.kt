package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UserTransactionsPage(
    currentUserName: String = "",
    onAddUser: (String, String, String, String, String, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteUser: (String, (Boolean, String?) -> Unit) -> Unit = { _, _ -> },
    onUpdateUser: (String, String, String, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _, _, _ -> },
    onResetPassword: (String, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> }
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<User?>(null) }
    var userToEdit by remember { mutableStateOf<User?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("User Transactions", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add User")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("All Users (${AppRepository.users.size})", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AppRepository.users) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(fullName = user.fullName, username = user.username, avatarUri = user.avatarUri)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.fullName, fontWeight = FontWeight.Bold)
                            Text("@${user.username} · ${user.role.name}", fontSize = 12.sp, color = Color.Gray)
                            if (user.email.isNotBlank()) {
                                Text(user.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        IconButton(onClick = { userToEdit = user }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                        }
                        val canDelete = user.username != "admin" &&
                            (user.role != UserRole.ADMIN || currentUserName == "admin")
                        if (canDelete) {
                            IconButton(onClick = { userToDelete = user }) {
                                Icon(Icons.Default.Clear, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddUserDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { username, password, role, fullName, email, department, callback ->
                onAddUser(username, password, role, fullName, email, department) { success, error ->
                    callback(success, error)
                    if (success) showAddDialog = false
                }
            }
        )
    }

    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Delete User") },
            text = { Text("Are you sure you want to delete \"${userToDelete!!.fullName}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = userToDelete!!.username
                    onDeleteUser(target) { success, _ ->
                        if (success) {
                            AppRepository.messages.removeAll { it.sender == target || it.recipient == target }
                            AppRepository.availabilities.removeAll { it.instructorName == target }
                            AppRepository.availabilityDrafts.remove(target)
                            AppRepository.notifications.removeAll { it.recipientName == target }
                        }
                    }
                    userToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (userToEdit != null) {
        EditUserDialog(
            user = userToEdit!!,
            onDismiss = { userToEdit = null },
            onSave = { username, fullName, email, role, callback ->
                onUpdateUser(username, fullName, email, role) { success, error ->
                    callback(success, error)
                    if (success) userToEdit = null
                }
            },
            onResetPassword = { username, newPassword, callback ->
                onResetPassword(username, newPassword, callback)
            }
        )
    }
}

@Composable
fun CredentialsDialog(credentials: List<Pair<String, String>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(if (credentials.size == 1) "Account Created" else "${credentials.size} Accounts Created")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Share these credentials with the instructors. The password will not be shown again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(credentials) { (username, password) ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row {
                                    Text("Username: ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(username, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                }
                                Row {
                                    Text("Password: ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(password, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun AddUserDialog(onDismiss: () -> Unit, onAdd: (String, String, String, String, String, String, (Boolean, String?) -> Unit) -> Unit) {
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.INSTRUCTOR) }
    var errorMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New User") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = department, onValueChange = { department = it }, label = { Text("Department") }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("e.g. Computer Engineering") })
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Role:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    UserRole.entries.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = role == r, onClick = { role = r })
                            Text(r.name, fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedUsername = username.trim().lowercase()
                    when {
                        trimmedUsername.isBlank() -> errorMsg = "Username is required"
                        AppRepository.users.any { it.username.equals(trimmedUsername, ignoreCase = true) } -> errorMsg = "Username already exists"
                        fullName.isBlank() -> errorMsg = "Full name is required"
                        email.isNotBlank() && !isValidEmail(email) -> errorMsg = "Enter a valid email address"
                        password.length < 6 -> errorMsg = "Password must be at least 6 characters"
                        else -> {
                            isLoading = true
                            errorMsg = ""
                            onAdd(trimmedUsername, password, role.name, fullName.trim(), email.trim(), department.trim()) { success, error ->
                                isLoading = false
                                if (!success) errorMsg = error ?: "Kullanıcı eklenemedi"
                            }
                        }
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditUserDialog(
    user: User,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, (Boolean, String?) -> Unit) -> Unit,
    onResetPassword: (String, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> }
) {
    var fullName by remember { mutableStateOf(user.fullName) }
    var email by remember { mutableStateOf(user.email) }
    var role by remember { mutableStateOf(user.role) }
    var errorMsg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var resetCredential by remember { mutableStateOf<Pair<String, String>?>(null) }
    val isSuperuser = user.username == "admin"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit User") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(fullName = user.fullName, username = user.username, avatarUri = user.avatarUri, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("@${user.username}", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                }
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (!isSuperuser) {
                    Text("Role:", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserRole.entries.forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = role == r, onClick = { role = r })
                                Text(r.name, fontSize = 13.sp)
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                    }
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Generate New Password")
                    }

                    if (showResetConfirm) {
                        AlertDialog(
                            onDismissRequest = { showResetConfirm = false },
                            title = { Text("Generate New Password") },
                            text = { Text("A new random password will be generated for ${user.fullName}. The new password will be shown once. Are you sure?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showResetConfirm = false
                                    val newPass = generatePassword()
                                    resetCredential = user.username to newPass
                                }) { Text("Yes, Generate", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }

                    resetCredential?.let { (plainUn, newPass) ->
                        CredentialsDialog(
                            credentials = listOf(plainUn to newPass),
                            onDismiss = {
                                onResetPassword(user.username, newPass) { _, _ -> }
                                resetCredential = null
                            }
                        )
                    }
                }
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        fullName.isBlank() -> errorMsg = "Full name is required"
                        else -> {
                            isLoading = true
                            errorMsg = ""
                            onSave(user.username, fullName.trim(), email.trim(), role.name) { success, error ->
                                isLoading = false
                                if (!success) errorMsg = error ?: "Güncelleme başarısız"
                            }
                        }
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
