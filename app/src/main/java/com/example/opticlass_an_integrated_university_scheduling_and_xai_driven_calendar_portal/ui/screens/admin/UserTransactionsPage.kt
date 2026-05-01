package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.util.Log
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.RetrofitClient
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.UserCreateDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.UserDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.UserUpdateDto
import kotlinx.coroutines.launch

@Composable
fun UserTransactionsPage(currentUserName: String = "", viewModel: AppViewModel) {
    val scope = rememberCoroutineScope()

    // UI State
    var users by remember { mutableStateOf<List<UserDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Dialog State
    var showAddDialog by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<UserDto?>(null) }
    var userToEdit by remember { mutableStateOf<UserDto?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val refreshUsers = {
        scope.launch {
            isLoading = true
            try {
                val token = "Bearer ${viewModel.authToken}"
                val response = RetrofitClient.instance.getUsers(token)
                if (response.isSuccessful) {
                    users = response.body() ?: emptyList()
                } else {
                    Log.e("UserTransactions", "Error fetching users: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("UserTransactions", "Network Error fetching users", e)
            } finally {
                isLoading = false
            }
        }
    }

    // Sayfa açıldığında API'den kullanıcıları çek
    LaunchedEffect(Unit) {
        refreshUsers()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("User Transactions", style = MaterialTheme.typography.headlineMedium)
                Row {
                    IconButton(onClick = { refreshUsers() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    Button(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add User")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("All Users (${users.size})", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Spacer(Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(users) { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                // Not: Avatar uri'si DB'de yoksa varsayılan gösterilir
                                UserAvatar(fullName = user.fullName, username = user.username, avatarUri = null)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.fullName, fontWeight = FontWeight.Bold)
                                    Text("@${user.username} · ${user.role}", fontSize = 12.sp, color = Color.Gray)
                                    if (!user.email.isNullOrBlank()) {
                                        Text(user.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                                IconButton(onClick = { userToEdit = user }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                                }

                                val canDelete = user.username != "admin" &&
                                        (user.role != "ADMIN" || currentUserName == "admin")
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
        }
    }

    if (showAddDialog) {
        AddUserDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { dto ->
                scope.launch {
                    try {
                        val token = "Bearer ${viewModel.authToken}"
                        val response = RetrofitClient.instance.addUser(token, dto)
                        if (response.isSuccessful) {
                            refreshUsers()
                        } else {
                            snackbarHostState.showSnackbar("Failed to add user.")
                        }
                    } catch (e: Exception) {
                        Log.e("UserTransactions", "Error adding user", e)
                        snackbarHostState.showSnackbar("Network error.")
                    }
                }
                showAddDialog = false
            }
        )
    }

    if (userToEdit != null) {
        EditUserDialog(
            user = userToEdit!!,
            onDismiss = { userToEdit = null },
            onSave = { dto ->
                scope.launch {
                    try {
                        val token = "Bearer ${viewModel.authToken}"
                        val response = RetrofitClient.instance.updateUser(token, userToEdit!!.username, dto)
                        if (response.isSuccessful) {
                            refreshUsers()
                        } else {
                            snackbarHostState.showSnackbar("Failed to update user.")
                        }
                    } catch (e: Exception) {
                        Log.e("UserTransactions", "Error updating user", e)
                        snackbarHostState.showSnackbar("Network error.")
                    }
                }
                userToEdit = null
            }
        )
    }

    if (userToDelete != null) {
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Delete User") },
            text = { Text("Are you sure you want to delete \"${userToDelete!!.fullName}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    val target = userToDelete!!.username
                    scope.launch {
                        try {
                            val token = "Bearer ${viewModel.authToken}"
                            val response = RetrofitClient.instance.deleteUser(token, target)
                            if (response.isSuccessful) {
                                refreshUsers()
                            } else {
                                snackbarHostState.showSnackbar("Failed to delete user.")
                            }
                        } catch (e: Exception) {
                            Log.e("UserTransactions", "Error deleting user", e)
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
}

@Composable
fun AddUserDialog(onDismiss: () -> Unit, onAdd: (UserCreateDto) -> Unit) {
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("INSTRUCTOR") }
    var errorMsg by remember { mutableStateOf("") }

    val roles = listOf("ADMIN", "INSTRUCTOR")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New User") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = department, onValueChange = { department = it }, label = { Text("Department") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Role:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    roles.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = role == r, onClick = { role = r })
                            Text(r, fontSize = 13.sp)
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
                val trimmedUsername = username.trim().lowercase()
                when {
                    trimmedUsername.isBlank() -> errorMsg = "Username is required"
                    fullName.isBlank() -> errorMsg = "Full name is required"
                    password.length < 6 -> errorMsg = "Min 6 chars"
                    else -> onAdd(
                        UserCreateDto(
                            username = trimmedUsername,
                            passwordHash = sha256(password), // Android tarafında hashlenip yollanıyor
                            role = role,
                            fullName = fullName.trim(),
                            email = email.trim(),
                            department = department.trim()
                        )
                    )
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditUserDialog(user: UserDto, onDismiss: () -> Unit, onSave: (UserUpdateDto) -> Unit) {
    var fullName by remember { mutableStateOf(user.fullName) }
    var email by remember { mutableStateOf(user.email ?: "") }
    var department by remember { mutableStateOf(user.department ?: "") }
    var role by remember { mutableStateOf(user.role) }
    var errorMsg by remember { mutableStateOf("") }

    val roles = listOf("ADMIN", "INSTRUCTOR")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit User: ${user.username}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = department, onValueChange = { department = it }, label = { Text("Department") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Role:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(8.dp))
                    roles.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = role == r, onClick = { role = r })
                            Text(r, fontSize = 13.sp)
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
                if (fullName.isBlank()) {
                    errorMsg = "Full name is required"
                } else {
                    onSave(
                        UserUpdateDto(
                            role = role,
                            fullName = fullName.trim(),
                            email = email.trim(),
                            department = department.trim()
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}