package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.ui.theme.OptiClassAn_Integrated_University_Scheduling_and_XAIDriven_Calendar_PortalTheme
import kotlinx.coroutines.launch

// Simulated Data Models
data class Message(
    val sender: String,
    val recipient: String,
    val content: String,
    val isRead: Boolean = false
)

data class AppNotification(
    val id: String,
    val text: String,
    val isRead: Boolean = false
)

data class Availability(
    val instructorName: String,
    val slots: Map<String, Set<String>> // Day -> Set of TimeSlots
)

// Global in-memory state
val globalMessages = mutableStateListOf<Message>()
val globalNotifications = mutableStateListOf<AppNotification>()
val globalAvailabilities = mutableStateListOf<Availability>()
// Persists draft changes during the session
val globalAvailabilityDrafts = mutableStateMapOf<String, Map<String, Set<String>>>()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OptiClassAn_Integrated_University_Scheduling_and_XAIDriven_Calendar_PortalTheme {
                OptiClassApp()
            }
        }
    }
}

enum class UserRole {
    ADMIN, INSTRUCTOR
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
    val roleRestriction: UserRole? = null,
    val showInSidebar: Boolean = true
) {
    MAIN_PAGE("Main Page", Icons.Default.Home),
    MY_SCHEDULE("My Schedule", Icons.Default.DateRange, roleRestriction = UserRole.INSTRUCTOR),
    MY_AVAILABILITY("My Availability", Icons.Default.Edit, roleRestriction = UserRole.INSTRUCTOR),
    NOTIFICATIONS("Notifications", Icons.Default.Notifications, roleRestriction = UserRole.INSTRUCTOR, showInSidebar = false),
    DATA_IMPORT("Data Import", Icons.Default.KeyboardArrowUp, roleRestriction = UserRole.ADMIN),
    USER_TRANSACTIONS("User Transactions", Icons.Default.Person, roleRestriction = UserRole.ADMIN),
    UPDATE_CALENDAR("Update Calendar", Icons.Default.Edit, roleRestriction = UserRole.ADMIN),
    INSTRUCTOR_AVAILABILITY("Instructor Availability", Icons.AutoMirrored.Filled.List, roleRestriction = UserRole.ADMIN),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun LoginScreen(onLoginSuccess: (UserRole, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "OptiClass Login", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                when {
                    username == "admin" && password == "admin123" -> onLoginSuccess(UserRole.ADMIN, "admin")
                    username == "berkay" && password == "berkay123" -> onLoginSuccess(UserRole.INSTRUCTOR, "berkay")
                    else -> errorMessage = "Invalid username or password"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login")
        }
    }
}

@Composable
fun OptiClassApp() {
    var isLoggedIn by remember { mutableStateOf(false) }
    var userRole by remember { mutableStateOf(UserRole.INSTRUCTOR) }
    var currentUserName by remember { mutableStateOf("") }

    if (!isLoggedIn) {
        LoginScreen { role, name ->
            userRole = role
            currentUserName = name
            isLoggedIn = true
        }
    } else {
        MainScaffold(
            role = userRole,
            userName = currentUserName,
            onLogout = { isLoggedIn = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(role: UserRole, userName: String, onLogout: () -> Unit) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.MAIN_PAGE) }
    var showNotificationMenu by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val unreadMsgCount = globalMessages.count { it.recipient == userName && !it.isRead }
    val unreadNotifCount = globalNotifications.count { !it.isRead }

    // Logic for new message notification snackbar
    var lastMessageCount by remember { mutableStateOf(globalMessages.size) }
    LaunchedEffect(globalMessages.size) {
        if (globalMessages.size > lastMessageCount) {
            val lastMsg = globalMessages.last()
            if (lastMsg.recipient == userName) {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "New message from ${lastMsg.sender}",
                        actionLabel = "View",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        currentDestination = if (role == UserRole.ADMIN) AppDestinations.MAIN_PAGE else AppDestinations.NOTIFICATIONS
                    }
                }
            }
        }
        lastMessageCount = globalMessages.size
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Confirm Logout") },
            text = { Text("Are you sure about logging out?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("No")
                }
            }
        )
    }

    val destinations = AppDestinations.entries.filter { 
        (it.roleRestriction == null || it.roleRestriction == role) && it.showInSidebar
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "OptiClass",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                HorizontalDivider()
                destinations.forEach { destination ->
                    NavigationDrawerItem(
                        label = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(destination.label)
                                if (role == UserRole.ADMIN && destination == AppDestinations.MAIN_PAGE && unreadMsgCount > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Badge { Text(unreadMsgCount.toString()) }
                                }
                            }
                        },
                        selected = destination == currentDestination,
                        onClick = {
                            currentDestination = destination
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(currentDestination.label) },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Navigation")
                        }
                    },
                    actions = {
                        if (role == UserRole.INSTRUCTOR) {
                            // Message Icon with Badge (Left of Bell)
                            BadgedBox(
                                badge = { if (unreadMsgCount > 0) Badge { Text(unreadMsgCount.toString()) } }
                            ) {
                                IconButton(onClick = { currentDestination = AppDestinations.NOTIFICATIONS }) {
                                    Icon(Icons.Default.Email, contentDescription = "Messages")
                                }
                            }
                            // Bell Icon with Badge
                            Box {
                                BadgedBox(
                                    badge = { if (unreadNotifCount > 0) Badge { Text(unreadNotifCount.toString()) } }
                                ) {
                                    IconButton(onClick = { showNotificationMenu = true }) {
                                        Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                                    }
                                }
                                DropdownMenu(
                                    expanded = showNotificationMenu,
                                    onDismissRequest = { showNotificationMenu = false },
                                    modifier = Modifier.width(280.dp)
                                ) {
                                    if (globalNotifications.isEmpty()) {
                                        DropdownMenuItem(text = { Text("No notifications") }, onClick = { showNotificationMenu = false })
                                    } else {
                                        globalNotifications.forEachIndexed { index, notif ->
                                            DropdownMenuItem(
                                                text = { Text(notif.text, fontWeight = if (notif.isRead) FontWeight.Normal else FontWeight.Bold) },
                                                onClick = { 
                                                    showNotificationMenu = false
                                                    globalNotifications[index] = notif.copy(isRead = true)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                when {
                    role == UserRole.ADMIN && currentDestination == AppDestinations.MAIN_PAGE -> AdminMainPage(userName)
                    role == UserRole.INSTRUCTOR && currentDestination == AppDestinations.NOTIFICATIONS -> ChatBox(userName, "admin")
                    currentDestination == AppDestinations.MY_AVAILABILITY -> MyAvailabilityPage(userName)
                    currentDestination == AppDestinations.INSTRUCTOR_AVAILABILITY -> InstructorAvailabilityAdminPage()
                    currentDestination == AppDestinations.USER_TRANSACTIONS -> UserTransactionsPage()
                    else -> GenericPage(currentDestination.label)
                }
            }
        }
    }
}

@Composable
fun MyAvailabilityPage(instructorName: String) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    val timeSlots = listOf("08:00 AM", "09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM", "01:00 PM", "02:00 PM", "03:00 PM", "04:00 PM", "05:00 PM")
    
    val initialDraft = globalAvailabilityDrafts[instructorName] ?: emptyMap()
    val selectedSlots = remember { 
        val map = mutableStateMapOf<String, MutableSet<String>>()
        days.forEach { day -> map[day] = initialDraft[day]?.toMutableSet() ?: mutableSetOf() }
        map
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Your Availability", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        AvailabilityTable(days, timeSlots, selectedSlots)
        
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { 
                globalAvailabilityDrafts[instructorName] = selectedSlots.mapValues { it.value.toSet() }
            }) { Text("Save Changes") }
            Button(onClick = {
                val availability = Availability(instructorName, selectedSlots.mapValues { it.value.toSet() })
                globalAvailabilities.removeAll { it.instructorName == instructorName }
                globalAvailabilities.add(availability)
                globalAvailabilityDrafts[instructorName] = availability.slots
            }) { Text("Send to Admin") }
        }
    }
}

@Composable
fun AvailabilityTable(days: List<String>, timeSlots: List<String>, selectedSlots: MutableMap<String, MutableSet<String>>, isReadOnly: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray)) {
        Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)) {
            Box(modifier = Modifier.width(80.dp).padding(8.dp)) { Text("Time", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            days.forEach { day ->
                Box(modifier = Modifier.weight(1f).padding(8.dp), contentAlignment = Alignment.Center) { Text(day, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
            items(timeSlots) { slot ->
                Row(modifier = Modifier.fillMaxWidth().border(0.5.dp, Color.LightGray)) {
                    Box(modifier = Modifier.width(80.dp).padding(8.dp).background(Color(0xFFF5F5F5))) { Text(slot, fontSize = 10.sp) }
                    days.forEach { day ->
                        val isSelected = selectedSlots[day]?.contains(slot) == true
                        Box(
                            modifier = Modifier
                                .weight(1f).height(40.dp).border(0.5.dp, Color.LightGray)
                                .background(if (isSelected) Color.Green.copy(alpha = 0.3f) else Color.Transparent)
                                .clickable(enabled = !isReadOnly) {
                                    val currentSet = selectedSlots[day] ?: mutableSetOf()
                                    val newSet = currentSet.toMutableSet()
                                    if (isSelected) newSet.remove(slot) else newSet.add(slot)
                                    selectedSlots[day] = newSet
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InstructorAvailabilityAdminPage() {
    var selectedInstructor by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (selectedInstructor == null) {
            Text("Instructor Availabilities", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            if (globalAvailabilities.isEmpty()) {
                Text("No submissions yet.")
            } else {
                LazyColumn {
                    items(globalAvailabilities) { availability ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedInstructor = availability.instructorName },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                text = availability.instructorName,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        } else {
            val availability = globalAvailabilities.find { it.instructorName == selectedInstructor }
            TextButton(onClick = { selectedInstructor = null }) {
                Text("< Back to list")
            }
            if (availability != null) {
                Text("Availability for ${availability.instructorName}", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                AvailabilityTable(
                    days = listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
                    timeSlots = listOf("08:00 AM", "09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM", "01:00 PM", "02:00 PM", "03:00 PM", "04:00 PM", "05:00 PM"),
                    selectedSlots = availability.slots.mapValues { it.value.toMutableSet() }.toMutableMap(),
                    isReadOnly = true
                )
            }
        }
    }
}

@Composable
fun AdminMainPage(adminName: String) {
    var selectedUser by remember { mutableStateOf<String?>(null) }
    val users = globalMessages.filter { it.recipient == "admin" || it.sender == "admin" }.map { if (it.sender == "admin") it.recipient else it.sender }.distinct().filter { it != "admin" }
    if (selectedUser == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Inbox", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            LazyColumn {
                items(users) { user ->
                    val unread = globalMessages.count { it.sender == user && it.recipient == "admin" && !it.isRead }
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedUser = user }) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(user, modifier = Modifier.weight(1f))
                            if (unread > 0) Badge { Text(unread.toString()) }
                        }
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            TextButton(onClick = { selectedUser = null }) { Text("< Back") }
            ChatBox(adminName, selectedUser!!)
        }
    }
}

@Composable
fun ChatBox(currentUserName: String, targetUserName: String) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(globalMessages.size) {
        globalMessages.indices.forEach { i ->
            val m = globalMessages[i]
            if (m.sender == targetUserName && m.recipient == currentUserName && !m.isRead) {
                globalMessages[i] = m.copy(isRead = true)
            }
        }
    }
    val chatMessages = globalMessages.filter { (it.sender == currentUserName && it.recipient == targetUserName) || (it.sender == targetUserName && it.recipient == currentUserName) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Chat with $targetUserName", style = MaterialTheme.typography.titleLarge)
        LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            items(chatMessages) { msg ->
                val isMe = msg.sender == currentUserName
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
                    Card(colors = CardDefaults.cardColors(containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.padding(vertical = 4.dp).widthIn(max = 280.dp)) {
                        Text(msg.content, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message...") })
            IconButton(onClick = { if (text.isNotBlank()) { globalMessages.add(Message(currentUserName, targetUserName, text)); text = "" } }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable fun GenericPage(title: String) { Column(modifier = Modifier.fillMaxSize().padding(16.dp)) { Text("Welcome to $title", style = MaterialTheme.typography.headlineMedium) } }

@Composable
fun UserTransactionsPage() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("User Transactions", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = {}) { Icon(Icons.Default.Add, null, Modifier.size(48.dp)) }; Text("Add") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = {}) { Icon(Icons.Default.Clear, null, Modifier.size(48.dp)) }; Text("Delete") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = {}) { Icon(Icons.Default.Star, null, Modifier.size(48.dp)) }; Text("Update") }
        }
    }
}
