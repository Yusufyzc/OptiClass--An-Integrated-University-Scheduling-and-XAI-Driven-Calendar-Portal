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
fun UserTransactionsPage(currentUserName: String = "") {
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
                            Text("@${decodeUsername(user.username)} · ${user.role.name}", fontSize = 12.sp, color = Color.Gray)
                            if (user.email.isNotBlank()) {
                                Text(user.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        IconButton(onClick = { userToEdit = user }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                        }
                        val canDelete = decodeUsername(user.username) != "admin" &&
                            (user.role != UserRole.ADMIN || decodeUsername(currentUserName) == "admin")
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
            onAdd = { newUser ->
                AppRepository.users.add(newUser)
                showAddDialog = false
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
                    AppRepository.users.removeAll { it.username == target }
                    AppRepository.messages.removeAll { it.sender == target || it.recipient == target }
                    AppRepository.availabilities.removeAll { it.instructorName == target }
                    AppRepository.availabilityDrafts.remove(target)
                    AppRepository.notifications.removeAll { it.recipientName == target }
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
            onSave = { updated ->
                val index = AppRepository.users.indexOfFirst { it.username == updated.username }
                if (index != -1) AppRepository.users[index] = updated
                userToEdit = null
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
                credentials.forEach { (username, password) ->
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun AddUserDialog(onDismiss: () -> Unit, onAdd: (User) -> Unit) {
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.INSTRUCTOR) }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New User") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
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
            TextButton(onClick = {
                val trimmedUsername = username.trim()
                when {
                    trimmedUsername.isBlank() -> errorMsg = "Username is required"
                    AppRepository.users.any { decodeUsername(it.username).equals(trimmedUsername, ignoreCase = true) } -> errorMsg = "Username already exists"
                    fullName.isBlank() -> errorMsg = "Full name is required"
                    email.isNotBlank() && !isValidEmail(email) -> errorMsg = "Enter a valid email address"
                    password.length < 6 -> errorMsg = "Password must be at least 6 characters"
                    else -> onAdd(User(
                        username = encodeUsername(trimmedUsername.lowercase()),
                        password = sha256(password),
                        role = role,
                        fullName = fullName.trim(),
                        email = email.trim(),
                        mustChangePassword = role == UserRole.INSTRUCTOR
                    ))
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditUserDialog(user: User, onDismiss: () -> Unit, onSave: (User) -> Unit) {
    var fullName by remember { mutableStateOf(user.fullName) }
    var email by remember { mutableStateOf(user.email) }
    var role by remember { mutableStateOf(user.role) }
    var errorMsg by remember { mutableStateOf("") }
    var showResetConfirm by remember { mutableStateOf(false) }
    var resetCredential by remember { mutableStateOf<Pair<String, String>?>(null) }
    val isSuperuser = decodeUsername(user.username) == "admin"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit User") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(fullName = user.fullName, username = user.username, avatarUri = user.avatarUri, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("@${decodeUsername(user.username)}", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
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
                        Text("Reset Password to Default")
                    }

                    if (showResetConfirm) {
                        AlertDialog(
                            onDismissRequest = { showResetConfirm = false },
                            title = { Text("Reset Password") },
                            text = { Text("A new random password will be generated for ${user.fullName}. The new password will be shown once. Are you sure?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showResetConfirm = false
                                    val newPass = generatePassword()
                                    val plainUn = decodeUsername(user.username)
                                    onSave(user.copy(
                                        fullName = fullName.trim(),
                                        email = email.trim(),
                                        role = role,
                                        password = sha256(newPass),
                                        mustChangePassword = true
                                    ))
                                    resetCredential = plainUn to newPass
                                }) { Text("Yes, Reset", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }

                    resetCredential?.let { cred ->
                        CredentialsDialog(credentials = listOf(cred), onDismiss = { resetCredential = null; onDismiss() })
                    }
                }
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    fullName.isBlank() -> errorMsg = "Full name is required"
                    else -> onSave(user.copy(fullName = fullName.trim(), email = email.trim(), role = role))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
