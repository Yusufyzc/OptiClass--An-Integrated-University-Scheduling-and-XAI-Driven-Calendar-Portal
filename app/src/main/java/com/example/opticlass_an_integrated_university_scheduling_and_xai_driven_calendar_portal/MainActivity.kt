package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.ui.theme.OptiClassAn_Integrated_University_Scheduling_and_XAIDriven_Calendar_PortalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.security.MessageDigest
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image

// ── Data Models ──────────────────────────────────────────────────────────────

data class Message(
    val sender: String,
    val recipient: String,
    val content: String,
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class AppNotification(
    val id: String,
    val text: String,
    val isRead: Boolean = false,
    val recipientName: String = ""
)

data class Availability(
    val instructorName: String,
    val slots: Map<String, Set<String>>
)

data class CourseImport(
    val code: String,
    val name: String,
    val lecturer: String,
    val department: String,
    val email: String,
    val duration: Int = 1,  // 1-3 hours; -1 = continuation slot marker
    val classroomId: String? = null
)

data class Classroom(
    val id: String,
    val roomCode: String,
    val capacity: Int,
    val department: String
)

data class ScheduleChange(
    val changedBy: String,
    val timestamp: Long = System.currentTimeMillis(),
    val instructorUsername: String,
    val instructorFullName: String,
    val day: String,
    val timeSlot: String,
    val previousCourse: CourseImport?,
    val newCourse: CourseImport?
)

data class User(
    val username: String,          // Base64 encoded
    val password: String,          // SHA-256 hash
    val role: UserRole,
    val fullName: String,
    val email: String,
    val avatarUri: String? = null,
    val department: String = "",
    val mustChangePassword: Boolean = false,
    val courses: MutableList<CourseImport> = mutableListOf(),
    val schedule: SnapshotStateMap<String, SnapshotStateMap<String, CourseImport?>> = mutableStateMapOf()
)

// ── Global In-Memory State ────────────────────────────────────────────────────

val globalMessages = mutableStateListOf<Message>()
val globalNotifications = mutableStateListOf<AppNotification>()
val globalAvailabilities = mutableStateListOf<Availability>()
val globalCourseImports = mutableStateListOf<CourseImport>()
val globalUsers = mutableStateListOf(
    User(encodeUsername("admin"), sha256("admin123"), UserRole.ADMIN, "System Admin", "admin@opticlass.com"),
    User(encodeUsername("berkay"), sha256("berkay123"), UserRole.INSTRUCTOR, "Berkay", "berkay@example.com", mustChangePassword = true)
)
val globalClassrooms = mutableStateListOf<Classroom>()
val globalAvailabilityDrafts = mutableStateMapOf<String, Map<String, Set<String>>>()
val globalScheduleHistory = mutableStateListOf<ScheduleChange>()

// ── Activity ──────────────────────────────────────────────────────────────────

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

// ── Enums ─────────────────────────────────────────────────────────────────────

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
    MY_LECTURES("My Lectures", Icons.Default.Menu, roleRestriction = UserRole.INSTRUCTOR),
    MY_AVAILABILITY("My Availability", Icons.Default.Edit, roleRestriction = UserRole.INSTRUCTOR),
    NOTIFICATIONS("Notifications", Icons.Default.Notifications, roleRestriction = UserRole.INSTRUCTOR, showInSidebar = false),
    DATA_IMPORT("Data Import", Icons.Default.KeyboardArrowUp, roleRestriction = UserRole.ADMIN),
    USER_TRANSACTIONS("User Transactions", Icons.Default.Person, roleRestriction = UserRole.ADMIN),
    UPDATE_CALENDAR("Update Calendar", Icons.Default.Edit, roleRestriction = UserRole.ADMIN),
    INSTRUCTOR_AVAILABILITY("Instructor Availability", Icons.AutoMirrored.Filled.List, roleRestriction = UserRole.ADMIN),
    CLASSROOMS("Classrooms", Icons.Default.School, roleRestriction = UserRole.ADMIN),
    SETTINGS("Settings", Icons.Default.Settings),
}

// ── Constants ─────────────────────────────────────────────────────────────────
val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
val TIME_SLOTS = listOf("08:00 AM", "09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM", "01:00 PM", "02:00 PM", "03:00 PM", "04:00 PM", "05:00 PM")

// ── Helpers ───────────────────────────────────────────────────────────────────

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

fun isValidEmail(email: String): Boolean =
    email.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

fun encodeUsername(plain: String): String =
    Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

fun decodeUsername(encoded: String): String =
    try { String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8) }
    catch (e: Exception) { encoded }

private val avatarPalette = listOf(
    Color(0xFF1976D2), Color(0xFF388E3C), Color(0xFFD32F2F),
    Color(0xFF7B1FA2), Color(0xFFF57C00), Color(0xFF0097A7),
    Color(0xFFC2185B), Color(0xFF5D4037)
)

fun avatarColor(username: String): Color =
    avatarPalette[(username.hashCode() and 0x7FFFFFFF) % avatarPalette.size]

fun userInitials(fullName: String): String {
    val parts = fullName.trim().split(" ").filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "?"
    }
}

@Composable
fun UserAvatar(
    fullName: String,
    username: String,
    avatarUri: String? = null,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = avatarUri) {
        value = avatarUri?.let { uriStr ->
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } catch (e: Exception) { null }
            }
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (bitmap == null) avatarColor(username) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = userInitials(fullName),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.35f).sp
            )
        }
    }
}

// ── Login ─────────────────────────────────────────────────────────────────────

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
                val user = globalUsers.find {
                    decodeUsername(it.username).equals(username.trim(), ignoreCase = true) &&
                    it.password == sha256(password)
                }
                if (user != null) {
                    onLoginSuccess(user.role, user.username)
                } else {
                    errorMessage = "Invalid username or password"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login")
        }
    }
}

// ── App Root ──────────────────────────────────────────────────────────────────

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
        val currentUser = globalUsers.find { it.username == currentUserName }
        if (currentUser?.mustChangePassword == true) {
            ForceChangePasswordScreen(
                encodedUsername = currentUserName,
                onPasswordChanged = { /* state recompose will pick up mustChangePassword = false */ }
            )
        } else {
            MainScaffold(
                role = userRole,
                userName = currentUserName,
                onLogout = { isLoggedIn = false }
            )
        }
    }
}

@Composable
fun ForceChangePasswordScreen(encodedUsername: String, onPasswordChanged: () -> Unit) {
    val user = globalUsers.find { it.username == encodedUsername } ?: return
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        UserAvatar(fullName = user.fullName, username = encodedUsername, avatarUri = user.avatarUri, size = 72.dp)
        Spacer(Modifier.height(16.dp))
        Text("Welcome, ${user.fullName}!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "You must set a new password before continuing.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("New Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm New Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (errorMsg.isNotEmpty()) {
            Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                when {
                    newPassword.length < 6 -> errorMsg = "Password must be at least 6 characters"
                    newPassword != confirmPassword -> errorMsg = "Passwords do not match"
                    else -> {
                        val index = globalUsers.indexOfFirst { it.username == encodedUsername }
                        if (index != -1) {
                            globalUsers[index] = globalUsers[index].copy(
                                password = sha256(newPassword),
                                mustChangePassword = false
                            )
                        }
                        onPasswordChanged()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Set New Password")
        }
    }
}

// ── Main Scaffold ─────────────────────────────────────────────────────────────

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
    // FIX: filter by current user's notifications only
    val unreadNotifCount = globalNotifications.count { !it.isRead && it.recipientName == userName }

    var lastMessageCount by remember { mutableIntStateOf(globalMessages.size) }
    LaunchedEffect(globalMessages.size) {
        if (globalMessages.size > lastMessageCount) {
            val lastMsg = globalMessages.last()
            if (lastMsg.recipient == userName) {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "New message from ${decodeUsername(lastMsg.sender)}",
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
                }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("No") }
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
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Navigation")
                        }
                    },
                    actions = {
                        if (role == UserRole.INSTRUCTOR) {
                            BadgedBox(
                                badge = { if (unreadMsgCount > 0) Badge { Text(unreadMsgCount.toString()) } }
                            ) {
                                IconButton(onClick = { currentDestination = AppDestinations.NOTIFICATIONS }) {
                                    Icon(Icons.Default.Email, contentDescription = "Messages")
                                }
                            }
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
                                    // FIX: show only this user's notifications
                                    val myNotifications = globalNotifications.filter { it.recipientName == userName }
                                    if (myNotifications.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No notifications") },
                                            onClick = { showNotificationMenu = false }
                                        )
                                    } else {
                                        myNotifications.forEach { notif ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        notif.text,
                                                        fontWeight = if (notif.isRead) FontWeight.Normal else FontWeight.Bold
                                                    )
                                                },
                                                onClick = {
                                                    showNotificationMenu = false
                                                    // FIX: find by id, not by index, to avoid off-by-one after filter
                                                    val idx = globalNotifications.indexOfFirst { it.id == notif.id }
                                                    if (idx != -1) globalNotifications[idx] = notif.copy(isRead = true)
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
                    role == UserRole.ADMIN && currentDestination == AppDestinations.MAIN_PAGE ->
                        AdminMainPage(userName)
                    role == UserRole.INSTRUCTOR && currentDestination == AppDestinations.MAIN_PAGE ->
                        InstructorMainPage(userName, onNavigate = { currentDestination = it }, onShowNotifications = { showNotificationMenu = true })
                    role == UserRole.INSTRUCTOR && currentDestination == AppDestinations.NOTIFICATIONS ->
                        ChatBox(userName, encodeUsername("admin"))
                    currentDestination == AppDestinations.MY_AVAILABILITY ->
                        MyAvailabilityPage(userName, snackbarHostState)
                    currentDestination == AppDestinations.MY_LECTURES ->
                        MyLecturesPage(userName)
                    currentDestination == AppDestinations.MY_SCHEDULE ->
                        MySchedulePage(userName)
                    currentDestination == AppDestinations.INSTRUCTOR_AVAILABILITY ->
                        InstructorAvailabilityAdminPage()
                    currentDestination == AppDestinations.DATA_IMPORT ->
                        DataImportPage(snackbarHostState)
                    currentDestination == AppDestinations.USER_TRANSACTIONS ->
                        UserTransactionsPage(userName)
                    currentDestination == AppDestinations.UPDATE_CALENDAR ->
                        UpdateCalendarPage(snackbarHostState, userName)
                    currentDestination == AppDestinations.CLASSROOMS ->
                        ClassroomsPage(snackbarHostState)
                    currentDestination == AppDestinations.SETTINGS ->
                        SettingsPage(userName)
                    else -> GenericPage(currentDestination.label)
                }
            }
        }
    }
}

// ── My Lectures ───────────────────────────────────────────────────────────────

@Composable
fun MyLecturesPage(userName: String) {
    val user = globalUsers.find { it.username == userName }
    val lectures = user?.courses ?: emptyList()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Lectures", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (lectures.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("You have no assigned lectures yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(lectures) { lecture ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        lecture.code,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(lecture.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Department: ${lecture.department}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

// ── Update Calendar ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCalendarPage(snackbarHostState: SnackbarHostState, currentAdminUser: String) {
    var expanded by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<User?>(null) }
    val instructors = globalUsers.filter { it.role == UserRole.INSTRUCTOR }
    val scope = rememberCoroutineScope()
    var isDirty by remember { mutableStateOf(false) }
    var pendingUserSelect by remember { mutableStateOf<User?>(null) }

    var selectedCourseToAssign by remember { mutableStateOf<CourseImport?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var selectedDuration by remember { mutableIntStateOf(1) }
    var selectedClassroom by remember { mutableStateOf<Classroom?>(null) }
    var classroomDropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Update Calendar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { showHistoryDialog = true }) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("History")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text("Select Instructor:", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedUser?.fullName ?: "Select an instructor",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                instructors.forEach { user ->
                    DropdownMenuItem(
                        text = { Text(user.fullName) },
                        onClick = {
                            if (isDirty && selectedUser != null && selectedUser != user) {
                                pendingUserSelect = user
                                expanded = false
                            } else {
                                selectedUser = user
                                expanded = false
                                selectedCourseToAssign = null
                                selectedClassroom = null
                                isDirty = false
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedUser != null) {
            val user = selectedUser!!

            val availability = globalAvailabilities.find { it.instructorName == user.username }
            val availableSlots = availability?.slots ?: emptyMap()

            val draftSchedule = remember(user.username) {
                val map = mutableStateMapOf<String, SnapshotStateMap<String, CourseImport?>>()
                DAYS.forEach { day ->
                    val innerMap = mutableStateMapOf<String, CourseImport?>()
                    TIME_SLOTS.forEach { slot -> innerMap[slot] = user.schedule[day]?.get(slot) }
                    map[day] = innerMap
                }
                map
            }

            Text("Assigned Courses (Tap to select, then tap grid to assign):", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(user.courses) { course ->
                    CourseItemSelectable(
                        course = course,
                        isSelected = selectedCourseToAssign == course,
                        onClick = { selectedCourseToAssign = if (selectedCourseToAssign == course) null else course }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Scheduling Grid:", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            SchedulingGridEnhanced(
                DAYS, TIME_SLOTS, availableSlots, draftSchedule, selectedCourseToAssign,
                onCellClick = { day, slot ->
                    if (selectedCourseToAssign != null) {
                        showAssignDialog = day to slot
                        selectedDuration = 1
                    }
                },
                onSlotCleared = { isDirty = true }
            )

            // Assign dialog: duration picker + conflict/availability warnings
            if (showAssignDialog != null) {
                val (reqDay, reqSlot) = showAssignDialog!!
                val startIdx = TIME_SLOTS.indexOf(reqSlot)
                val targetSlots = (0 until selectedDuration).map { TIME_SLOTS.getOrNull(startIdx + it) }
                val outOfBounds = targetSlots.any { it == null }
                val validSlots = targetSlots.filterNotNull()
                val conflictSlots = validSlots.drop(1).filter { s ->
                    val existing = draftSchedule[reqDay]?.get(s)
                    existing != null && existing.duration != -1
                }
                val unavailableSlots = validSlots.filter { s ->
                    availableSlots[reqDay]?.contains(s) != true
                }
                // Derslik çift rezervasyon kontrolü
                val classroomConflict = selectedClassroom != null && validSlots.any { s ->
                    globalUsers.any { u ->
                        u.schedule[reqDay]?.get(s)?.classroomId == selectedClassroom!!.id
                    }
                }

                AlertDialog(
                    onDismissRequest = { showAssignDialog = null },
                    title = { Text("Assign: ${selectedCourseToAssign?.code}") },
                    text = {
                        Column {
                            Text("Slot: $reqDay $reqSlot", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Duration (hours):", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 2, 3).forEach { d ->
                                    FilterChip(
                                        selected = selectedDuration == d,
                                        onClick = { selectedDuration = d },
                                        label = { Text("${d}s") }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Classroom (optional):", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            ExposedDropdownMenuBox(
                                expanded = classroomDropdownExpanded,
                                onExpandedChange = { classroomDropdownExpanded = !classroomDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedClassroom?.roomCode ?: "No classroom",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classroomDropdownExpanded) },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    isError = classroomConflict
                                )
                                ExposedDropdownMenu(
                                    expanded = classroomDropdownExpanded,
                                    onDismissRequest = { classroomDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("No classroom") },
                                        onClick = { selectedClassroom = null; classroomDropdownExpanded = false }
                                    )
                                    globalClassrooms.forEach { room ->
                                        DropdownMenuItem(
                                            text = { Text("${room.roomCode} — ${room.department}") },
                                            onClick = { selectedClassroom = room; classroomDropdownExpanded = false }
                                        )
                                    }
                                }
                            }
                            if (classroomConflict) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "${selectedClassroom!!.roomCode} is already booked at this time.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (outOfBounds) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Not enough slots for a ${selectedDuration}-hour block starting at this time.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (unavailableSlots.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Instructor not available at: ${unavailableSlots.joinToString(", ")}",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (conflictSlots.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Already occupied: ${conflictSlots.joinToString(", ")}. Assign anyway?",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !outOfBounds && !classroomConflict,
                            onClick = {
                                val course = selectedCourseToAssign!!.copy(
                                    duration = selectedDuration,
                                    classroomId = selectedClassroom?.id
                                )
                                validSlots.forEachIndexed { index, s ->
                                    draftSchedule[reqDay]?.set(s, if (index == 0) course else course.copy(duration = -1))
                                }
                                isDirty = true
                                showAssignDialog = null
                            }
                        ) {
                            Text(if (conflictSlots.isNotEmpty() || unavailableSlots.isNotEmpty()) "Assign Anyway" else "Confirm")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAssignDialog = null }) { Text("Cancel") }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        DAYS.forEach { day -> TIME_SLOTS.forEach { slot -> draftSchedule[day]?.set(slot, null) } }
                        selectedCourseToAssign = null
                        isDirty = true
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Schedule")
                }
                Button(
                    onClick = {
                        val now = System.currentTimeMillis()
                        DAYS.forEach { day ->
                            TIME_SLOTS.forEach { slot ->
                                val prev = user.schedule[day]?.get(slot)
                                val next = draftSchedule[day]?.get(slot)
                                if (prev?.code != next?.code) {
                                    globalScheduleHistory.add(
                                        0,
                                        ScheduleChange(
                                            changedBy = currentAdminUser,
                                            timestamp = now,
                                            instructorUsername = user.username,
                                            instructorFullName = user.fullName,
                                            day = day,
                                            timeSlot = slot,
                                            previousCourse = prev,
                                            newCourse = next
                                        )
                                    )
                                }
                            }
                        }
                        user.schedule.clear()
                        draftSchedule.forEach { (day, slots) ->
                            val innerMap = mutableStateMapOf<String, CourseImport?>()
                            innerMap.putAll(slots)
                            user.schedule[day] = innerMap
                        }
                        isDirty = false
                        selectedCourseToAssign = null
                        globalNotifications.add(
                            AppNotification(
                                id = System.currentTimeMillis().toString(),
                                text = "Admin updated your weekly schedule. Please check 'My Schedule'.",
                                recipientName = user.username
                            )
                        )
                        scope.launch { snackbarHostState.showSnackbar("Schedule updated and notification sent!") }
                    }
                ) {
                    Text("Save & Notify Instructor")
                }
            }
        }
    }

    if (pendingUserSelect != null) {
        AlertDialog(
            onDismissRequest = { pendingUserSelect = null },
            title = { Text("Unsaved Changes") },
            text = { Text("You have unsaved changes for ${selectedUser?.fullName}. Discard them and switch to ${pendingUserSelect!!.fullName}?") },
            confirmButton = {
                TextButton(onClick = {
                    selectedUser = pendingUserSelect
                    selectedCourseToAssign = null
                    isDirty = false
                    pendingUserSelect = null
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUserSelect = null }) { Text("Keep Editing") }
            }
        )
    }

    // History dialog
    if (showHistoryDialog) {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Schedule History") },
            text = {
                if (globalScheduleHistory.isEmpty()) {
                    Text("No changes recorded yet.")
                } else {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(max = 400.dp)
                    ) {
                        globalScheduleHistory.forEach { change ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        "${sdf.format(Date(change.timestamp))} — ${change.changedBy}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${change.instructorFullName} · ${change.day} ${change.timeSlot}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val prevText = change.previousCourse?.code ?: "—"
                                    val nextText = change.newCourse?.code ?: "Removed"
                                    Text(
                                        "$prevText → $nextText",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (change.newCourse == null)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) { Text("Close") }
            }
        )
    }
}

// ── Selectable Course Card ────────────────────────────────────────────────────

@Composable
fun CourseItemSelectable(course: CourseImport, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(120.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(course.code, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isSelected) Color.White else Color.Unspecified)
            Text(course.name, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center, color = if (isSelected) Color.White else Color.Unspecified)
        }
    }
}

// ── Scheduling Grid ───────────────────────────────────────────────────────────

@Composable
fun SchedulingGridEnhanced(
    days: List<String>,
    timeSlots: List<String>,
    availableSlots: Map<String, Set<String>>,
    draftSchedule: MutableMap<String, SnapshotStateMap<String, CourseImport?>>,
    selectedCourse: CourseImport?,
    onCellClick: (String, String) -> Unit,
    onSlotCleared: () -> Unit = {}
) {
    val gridScrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(gridScrollState)) {
    Column(modifier = Modifier.border(1.dp, Color.LightGray)) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min)
                .background(MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Box(modifier = Modifier.width(80.dp).padding(8.dp)) {
                Text("Time", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.Gray))
            days.forEach { day ->
                Box(modifier = Modifier.width(65.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                    Text(day, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
        Column {
            timeSlots.forEach { slot ->
                Row(modifier = Modifier.height(IntrinsicSize.Min).border(0.5.dp, Color.LightGray)) {
                    Box(modifier = Modifier.width(80.dp).padding(8.dp).background(Color(0xFFF9F9F9))) {
                        Text(slot, fontSize = 9.sp)
                    }
                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.LightGray))
                    days.forEach { day ->
                        val isAvailable = availableSlots[day]?.contains(slot) == true
                        val scheduledCourse = draftSchedule[day]?.get(slot)
                        val isContinuation = scheduledCourse?.duration == -1

                        Box(
                            modifier = Modifier
                                .width(65.dp)
                                .height(50.dp)
                                .border(0.5.dp, Color.LightGray)
                                .background(
                                    when {
                                        isContinuation -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        scheduledCourse != null -> MaterialTheme.colorScheme.primaryContainer
                                        isAvailable -> Color.Green.copy(alpha = 0.1f)
                                        else -> Color.Red.copy(alpha = 0.05f)
                                    }
                                )
                                .clickable(enabled = !isContinuation) { onCellClick(day, slot) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isContinuation) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .height(3.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                )
                            } else if (scheduledCourse != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        scheduledCourse.code,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    IconButton(
                                        onClick = {
                                            val slotIndex = timeSlots.indexOf(slot)
                                            draftSchedule[day]?.set(slot, null)
                                            for (i in 1 until scheduledCourse.duration) {
                                                val nextIdx = slotIndex + i
                                                if (nextIdx < timeSlots.size) {
                                                    val nextSlot = timeSlots[nextIdx]
                                                    if (draftSchedule[day]?.get(nextSlot)?.duration == -1) {
                                                        draftSchedule[day]?.set(nextSlot, null)
                                                    }
                                                }
                                            }
                                            onSlotCleared()
                                        },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Remove", tint = Color.Red)
                                    }
                                }
                            } else if (!isAvailable) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    } // horizontalScroll Box
}

// ── My Schedule ───────────────────────────────────────────────────────────────

@Composable
fun MySchedulePage(userName: String) {
    val user = globalUsers.find { it.username == userName } ?: return

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Weekly Schedule", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        val scheduleScrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scheduleScrollState)) {
            Column(modifier = Modifier.border(1.dp, Color.Gray)) {
                Row(modifier = Modifier.height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.primaryContainer)) {
                    Box(modifier = Modifier.width(80.dp).padding(8.dp)) {
                        Text("Time", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.Gray))
                    DAYS.forEach { day ->
                        Box(modifier = Modifier.width(65.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                            Text(day, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 600.dp)) {
                    items(TIME_SLOTS) { slot ->
                        Row(modifier = Modifier.height(IntrinsicSize.Min).border(0.5.dp, Color.LightGray)) {
                            Box(modifier = Modifier.width(80.dp).padding(8.dp).background(Color(0xFFF5F5F5))) {
                                Text(slot, fontSize = 10.sp)
                            }
                            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.LightGray))
                            DAYS.forEach { day ->
                                val course = user.schedule[day]?.get(slot)
                                val isContinuation = course?.duration == -1
                                Box(
                                    modifier = Modifier
                                        .width(65.dp)
                                        .height(50.dp)
                                        .border(0.5.dp, Color.LightGray)
                                        .background(
                                            when {
                                                isContinuation -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                                course != null -> MaterialTheme.colorScheme.secondaryContainer
                                                else -> Color.Transparent
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!isContinuation && course != null) {
                                        val roomCode = course.classroomId?.let { id ->
                                            globalClassrooms.find { it.id == id }?.roomCode
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(course.code, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                            if (roomCode != null) {
                                                Text(roomCode, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Data Import ───────────────────────────────────────────────────────────────

@Composable
fun DataImportPage(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            if (globalCourseImports.isNotEmpty()) {
                pendingImportUri = uri
            } else {
                scope.launch {
                    isImporting = true
                    val success = importExcelData(context, uri)
                    isImporting = false
                    if (success) {
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
            text = { Text("You have ${globalCourseImports.size} unsaved item(s) in the preview. Loading a new file will discard them. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImportUri!!
                    pendingImportUri = null
                    scope.launch {
                        isImporting = true
                        val success = importExcelData(context, uri)
                        isImporting = false
                        if (success) {
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
                        "Instructor accounts are automatically created using the email prefix as username and 'username123' as password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            if (globalCourseImports.isEmpty()) {
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
                    Text("Preview (${globalCourseImports.size} items)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = {
                            val listToSave = globalCourseImports.toList()
                            listToSave.forEach { saveCourseImport(it) }
                            globalCourseImports.clear()
                            scope.launch { snackbarHostState.showSnackbar("All instructors and courses saved!") }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Done, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save All")
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(globalCourseImports) { course ->
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
                                IconButton(onClick = { globalCourseImports.remove(course) }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Discard", tint = MaterialTheme.colorScheme.error)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable {
                                            saveCourseImport(course)
                                            globalCourseImports.remove(course)
                                            scope.launch { snackbarHostState.showSnackbar("Saved ${course.lecturer}") }
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
}

// ── Business Logic ────────────────────────────────────────────────────────────

fun saveCourseImport(course: CourseImport) {
    val plainUsername = course.email.substringBefore("@").lowercase()
    val encodedUn = encodeUsername(plainUsername)
    val existingIndex = globalUsers.indexOfFirst { it.username == encodedUn }

    if (existingIndex != -1) {
        val existingUser = globalUsers[existingIndex]
        if (existingUser.courses.none { it.code == course.code }) {
            val updatedCourses = existingUser.courses.toMutableList().also { it.add(course) }
            globalUsers[existingIndex] = existingUser.copy(courses = updatedCourses)
        }
    } else {
        globalUsers.add(
            User(
                username = encodedUn,
                password = sha256("${plainUsername}123"),
                role = UserRole.INSTRUCTOR,
                fullName = course.lecturer,
                email = course.email,
                mustChangePassword = true,
                courses = mutableListOf(course)
            )
        )
    }
}

private suspend fun importExcelData(context: Context, uri: Uri): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext false

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

            withContext(Dispatchers.Main) {
                globalCourseImports.clear()
                globalCourseImports.addAll(importedList)
            }

            workbook.close()
            inputStream.close()
            true
        } catch (e: Exception) {
            Log.e("ExcelImport", "Error: ${e.message}")
            false
        }
    }
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

            if (rows.hasNext()) rows.next() // skip header

            val list = mutableListOf<Classroom>()
            var index = 0
            while (rows.hasNext()) {
                val row = rows.next()
                val roomCode = formatter.formatCellValue(row.getCell(0)).trim()
                val department = formatter.formatCellValue(row.getCell(1)).trim()
                if (roomCode.isNotEmpty() && department.isNotEmpty()) {
                    list.add(Classroom(id = "room_${System.currentTimeMillis()}_${index++}", roomCode = roomCode, capacity = 0, department = department))
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

// ── Classrooms ────────────────────────────────────────────────────────────────

@Composable
fun ClassroomsPage(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var previewList by remember { mutableStateOf<List<Classroom>>(emptyList()) }
    var selectedDepartment by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

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
            Text("Classrooms", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
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
                        listOf("Classroom Name", "Department").forEach {
                            Text(it, modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp, end = 4.dp)) {
                        listOf("A101", "Computer Science").forEach {
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
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(classroom.roomCode, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(classroom.department, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
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
            Icon(Icons.Default.Add, contentDescription = "Import Classrooms")
        }
    }
}

// ── My Availability ───────────────────────────────────────────────────────────

@Composable
fun MyAvailabilityPage(instructorName: String, snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()

    val initialDraft = globalAvailabilityDrafts[instructorName] ?: emptyMap()
    val selectedSlots = remember {
        val map = mutableStateMapOf<String, MutableSet<String>>()
        DAYS.forEach { day -> map[day] = initialDraft[day]?.toMutableSet() ?: mutableSetOf() }
        map
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Your Availability", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        AvailabilityTable(
            days = DAYS,
            timeSlots = TIME_SLOTS,
            selectedSlots = selectedSlots,
            isReadOnly = false
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = {
                globalAvailabilityDrafts[instructorName] = selectedSlots.mapValues { it.value.toSet() }
                scope.launch { snackbarHostState.showSnackbar("Draft saved.") }
            }) { Text("Save Changes") }
            Button(onClick = {
                val availability = Availability(instructorName, selectedSlots.mapValues { it.value.toSet() })
                globalAvailabilities.removeAll { it.instructorName == instructorName }
                globalAvailabilities.add(availability)
                globalAvailabilityDrafts[instructorName] = availability.slots
                scope.launch { snackbarHostState.showSnackbar("Availability sent to admin!") }
            }) { Text("Send to Admin") }
        }
    }
}

// ── Availability Table (read-only or editable) ────────────────────────────────

@Composable
fun AvailabilityTable(
    days: List<String>,
    timeSlots: List<String>,
    selectedSlots: MutableMap<String, MutableSet<String>>,
    isReadOnly: Boolean = false
) {
    val horizontalScrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(horizontalScrollState)) {
        Column(modifier = Modifier.border(1.dp, Color.Gray)) {
            Row(modifier = Modifier.height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.primaryContainer)) {
                Box(modifier = Modifier.width(80.dp).padding(8.dp)) {
                    Text("Time", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.Gray))
                days.forEach { day ->
                    Box(
                        modifier = Modifier
                            .width(65.dp)
                            .padding(8.dp)
                            .then(
                                if (!isReadOnly) Modifier.clickable {
                                    val current = selectedSlots[day] ?: mutableSetOf()
                                    selectedSlots[day] = if (current.size == timeSlots.size) mutableSetOf() else timeSlots.toMutableSet()
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (!isReadOnly) MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                    }
                }
            }
            LazyColumn(modifier = Modifier.heightIn(max = 500.dp)) {
                items(timeSlots) { slot ->
                    Row(modifier = Modifier.height(IntrinsicSize.Min).border(0.5.dp, Color.LightGray)) {
                        Box(modifier = Modifier.width(80.dp).padding(8.dp).background(Color(0xFFF5F5F5))) {
                            Text(slot, fontSize = 10.sp)
                        }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.LightGray))
                        days.forEach { day ->
                            val isSelected = selectedSlots[day]?.contains(slot) == true
                            Box(
                                modifier = Modifier
                                    .width(65.dp).height(40.dp).border(0.5.dp, Color.LightGray)
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
}

// ── Instructor Availability (Admin view) ──────────────────────────────────────

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
                        // FIX: show instructor's full name, fallback to username
                        val instructor = globalUsers.find { it.username == availability.instructorName }
                        val displayName = instructor?.fullName ?: availability.instructorName
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedInstructor = availability.instructorName },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "@${availability.instructorName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val availability = globalAvailabilities.find { it.instructorName == selectedInstructor }
            if (availability != null) {
                val instructor = globalUsers.find { it.username == availability.instructorName }
                TextButton(onClick = { selectedInstructor = null }) { Text("< Back to list") }
                Text(
                    "Availability for ${instructor?.fullName ?: availability.instructorName}",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                val readOnlySlots = remember(availability.instructorName) {
                    availability.slots.mapValues { it.value.toMutableSet() }.toMutableMap()
                }
                AvailabilityTable(
                    days = DAYS,
                    timeSlots = TIME_SLOTS,
                    selectedSlots = readOnlySlots,
                    isReadOnly = true
                )
            }
        }
    }
}

// ── Admin Main Page (Inbox) ───────────────────────────────────────────────────

@Composable
fun AdminMainPage(adminName: String) {
    var selectedUser by remember { mutableStateOf<String?>(null) }
    var showNewChatDialog by remember { mutableStateOf(false) }

    // FIX: use adminName parameter instead of hardcoded "admin"
    val conversationUsers = globalMessages
        .filter { it.recipient == adminName || it.sender == adminName }
        .map { if (it.sender == adminName) it.recipient else it.sender }
        .distinct()
        .filter { it != adminName }

    // Summary calculations
    val unassignedInstructors = globalUsers.filter { user ->
        user.role == UserRole.INSTRUCTOR &&
        user.schedule.values.all { day -> day.values.all { it == null } }
    }
    val assignedCourseCodes = globalUsers.flatMap { user ->
        user.schedule.values.flatMap { day -> day.values.filterNotNull().map { it.code } }
    }.toSet()
    val unassignedCourses = globalCourseImports.filter { it.code !in assignedCourseCodes }
    val bookedSlotsPerRoom = globalUsers.flatMap { user ->
        user.schedule.entries.flatMap { (day, dayMap) ->
            dayMap.entries.mapNotNull { (slot, course) ->
                course?.classroomId?.let { Triple(it, day, slot) }
            }
        }
    }.groupBy { it.first }
    val availableClassrooms = globalClassrooms.filter { room ->
        (bookedSlotsPerRoom[room.id]?.size ?: 0) < DAYS.size * TIME_SLOTS.size
    }

    var showUnassignedInstructors by remember { mutableStateOf(false) }
    var showUnassignedCourses by remember { mutableStateOf(false) }
    var showAvailableClassrooms by remember { mutableStateOf(false) }

    if (selectedUser == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text("Inbox", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))

                // 3 özet kart
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Unassigned\nInstructors",
                        value = unassignedInstructors.size.toString(),
                        icon = Icons.Default.Person,
                        onClick = { showUnassignedInstructors = true }
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Unassigned\nCourses",
                        value = unassignedCourses.size.toString(),
                        icon = Icons.Default.Menu,
                        onClick = { showUnassignedCourses = true }
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Available\nClassrooms",
                        value = availableClassrooms.size.toString(),
                        icon = Icons.Default.School,
                        onClick = { showAvailableClassrooms = true }
                    )
                }

                Spacer(Modifier.height(16.dp))

                if (conversationUsers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No conversations yet.\nTap + to start a new chat.", textAlign = TextAlign.Center, color = Color.Gray)
                    }
                } else {
                    LazyColumn {
                        items(conversationUsers) { user ->
                            val unread = globalMessages.count { it.sender == user && it.recipient == adminName && !it.isRead }
                            val instructor = globalUsers.find { it.username == user }
                            val lastMsg = globalMessages
                                .filter {
                                    (it.sender == user && it.recipient == adminName) ||
                                    (it.sender == adminName && it.recipient == user)
                                }
                                .maxByOrNull { it.timestamp }
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    .clickable { selectedUser = user }
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    UserAvatar(
                                        fullName = instructor?.fullName ?: user,
                                        username = user,
                                        avatarUri = instructor?.avatarUri
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(instructor?.fullName ?: user, fontWeight = FontWeight.Medium)
                                        if (lastMsg != null) {
                                            Text(
                                                lastMsg.content,
                                                fontSize = 12.sp,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else {
                                            Text("@${decodeUsername(user)}", fontSize = 12.sp, color = Color.Gray)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (unread > 0) Badge { Text(unread.toString()) }
                                        if (lastMsg != null) {
                                            Text(formatTimestamp(lastMsg.timestamp), fontSize = 10.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // FIX: FAB to start a new conversation with an instructor not yet in inbox
            FloatingActionButton(
                onClick = { showNewChatDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Chat", tint = Color.White)
            }
        }

        if (showUnassignedInstructors) {
            AlertDialog(
                onDismissRequest = { showUnassignedInstructors = false },
                title = { Text("Unassigned Instructors") },
                text = {
                    if (unassignedInstructors.isEmpty()) {
                        Text("All instructors have at least one course assigned.")
                    } else {
                        LazyColumn {
                            items(unassignedInstructors) { u ->
                                Text("• ${u.fullName}", modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showUnassignedInstructors = false }) { Text("Close") } }
            )
        }

        if (showUnassignedCourses) {
            AlertDialog(
                onDismissRequest = { showUnassignedCourses = false },
                title = { Text("Unassigned Courses") },
                text = {
                    if (unassignedCourses.isEmpty()) {
                        Text("All courses have been assigned to a slot.")
                    } else {
                        LazyColumn {
                            items(unassignedCourses) { c ->
                                Text("• ${c.code} — ${c.name}", modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showUnassignedCourses = false }) { Text("Close") } }
            )
        }

        if (showAvailableClassrooms) {
            AlertDialog(
                onDismissRequest = { showAvailableClassrooms = false },
                title = { Text("Available Classrooms") },
                text = {
                    if (availableClassrooms.isEmpty()) {
                        Text("No classrooms imported yet.")
                    } else {
                        LazyColumn {
                            items(availableClassrooms) { room ->
                                val booked = bookedSlotsPerRoom[room.id]?.size ?: 0
                                val total = DAYS.size * TIME_SLOTS.size
                                Text("• ${room.roomCode} (${total - booked}/$total free)", modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showAvailableClassrooms = false }) { Text("Close") } }
            )
        }

        if (showNewChatDialog) {
            val instructors = globalUsers.filter { it.role == UserRole.INSTRUCTOR && it.username !in conversationUsers }
            AlertDialog(
                onDismissRequest = { showNewChatDialog = false },
                title = { Text("New Conversation") },
                text = {
                    if (instructors.isEmpty()) {
                        Text("All instructors already have an active conversation.")
                    } else {
                        LazyColumn {
                            items(instructors) { instructor ->
                                TextButton(
                                    onClick = {
                                        selectedUser = instructor.username
                                        showNewChatDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(instructor.fullName, fontWeight = FontWeight.Medium)
                                        Text("@${instructor.username}", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNewChatDialog = false }) { Text("Cancel") }
                }
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            TextButton(onClick = { selectedUser = null }) { Text("< Back") }
            ChatBox(adminName, selectedUser!!)
        }
    }
}

// ── Chat Box ──────────────────────────────────────────────────────────────────

@Composable
fun ChatBox(currentUserName: String, targetUserName: String) {
    var text by remember { mutableStateOf("") }
    // FIX: scroll to latest message automatically
    val listState = rememberLazyListState()

    LaunchedEffect(globalMessages.size) {
        globalMessages.indices.forEach { i ->
            val m = globalMessages[i]
            if (m.sender == targetUserName && m.recipient == currentUserName && !m.isRead) {
                globalMessages[i] = m.copy(isRead = true)
            }
        }
    }

    val chatMessages = globalMessages.filter {
        (it.sender == currentUserName && it.recipient == targetUserName) ||
        (it.sender == targetUserName && it.recipient == currentUserName)
    }

    // FIX: auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Chat with ${decodeUsername(targetUserName)}", style = MaterialTheme.typography.titleLarge)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp)
        ) {
            items(chatMessages) { msg ->
                val isMe = msg.sender == currentUserName
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(msg.content, modifier = Modifier.padding(8.dp))
                        }
                        // FIX: show message timestamp
                        Text(
                            formatTimestamp(msg.timestamp),
                            fontSize = 10.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") }
            )
            IconButton(onClick = {
                if (text.isNotBlank()) {
                    globalMessages.add(Message(currentUserName, targetUserName, text))
                    text = ""
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

// ── Generic Fallback Page ─────────────────────────────────────────────────────

@Composable
fun GenericPage(title: String) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Welcome to $title", style = MaterialTheme.typography.headlineMedium)
    }
}

// ── User Transactions ─────────────────────────────────────────────────────────

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
        Text("All Users (${globalUsers.size})", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(globalUsers) { user ->
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
                        // superuser silinemez; başka bir admin yalnızca superuser tarafından silinebilir
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
                globalUsers.add(newUser)
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
                    globalUsers.removeAll { it.username == target }
                    // FIX: clean up all data belonging to the deleted user
                    globalMessages.removeAll { it.sender == target || it.recipient == target }
                    globalAvailabilities.removeAll { it.instructorName == target }
                    globalAvailabilityDrafts.remove(target)
                    globalNotifications.removeAll { it.recipientName == target }
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
                val index = globalUsers.indexOfFirst { it.username == updated.username }
                if (index != -1) globalUsers[index] = updated
                userToEdit = null
            }
        )
    }
}

// ── Add / Edit User Dialogs ───────────────────────────────────────────────────

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
                    globalUsers.any { decodeUsername(it.username).equals(trimmedUsername, ignoreCase = true) } -> errorMsg = "Username already exists"
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
                // Şifre alanı yok — şifre yalnızca kullanıcının kendi Settings ekranından değiştirilebilir
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
                        val plainUn = decodeUsername(user.username)
                        AlertDialog(
                            onDismissRequest = { showResetConfirm = false },
                            title = { Text("Reset Password") },
                            text = { Text("The password for ${user.fullName} will be reset to \"${plainUn}123\". Are you sure?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showResetConfirm = false
                                    onSave(user.copy(
                                        fullName = fullName.trim(),
                                        email = email.trim(),
                                        role = role,
                                        password = sha256("${plainUn}123"),
                                        mustChangePassword = true
                                    ))
                                }) { Text("Yes, Reset", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
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

// ── Instructor Main Page ──────────────────────────────────────────────────────

@Composable
fun InstructorMainPage(userName: String, onNavigate: (AppDestinations) -> Unit = {}, onShowNotifications: () -> Unit = {}) {
    val user = globalUsers.find { it.username == userName } ?: return
    val unreadMessages = globalMessages.count { it.recipient == userName && !it.isRead }
    // FIX: filter notifications by current user
    val unreadNotifs = globalNotifications.count { !it.isRead && it.recipientName == userName }
    val availabilitySubmitted = globalAvailabilities.any { it.instructorName == userName }

    val dayMap = mapOf(2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri")
    val todayKey = dayMap[java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)]
    val todayClasses = if (todayKey != null) {
        TIME_SLOTS.mapNotNull { slot ->
            val course = user.schedule[todayKey]?.get(slot)
            if (course != null && course.duration != -1) slot to course else null
        }
    } else emptyList()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Welcome, ${user.fullName}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // FIX: summary cards are now tappable and navigate to the relevant page
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Courses",
                value = user.courses.size.toString(),
                icon = Icons.Default.Menu,
                onClick = { onNavigate(AppDestinations.MY_LECTURES) }
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Messages",
                value = unreadMessages.toString(),
                icon = Icons.Default.Email,
                onClick = { onNavigate(AppDestinations.NOTIFICATIONS) }
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Alerts",
                value = unreadNotifs.toString(),
                icon = Icons.Default.Notifications,
                onClick = onShowNotifications
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (availabilitySubmitted)
                    Color.Green.copy(alpha = 0.1f)
                else
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            )
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (availabilitySubmitted) Icons.Default.Check else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (availabilitySubmitted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (availabilitySubmitted) "Availability submitted to admin"
                    else "Availability not submitted yet — go to 'My Availability'",
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Text("Today's Classes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (todayClasses.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (todayKey == null) "No classes on weekends" else "No classes scheduled for today",
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(todayClasses) { (slot, course) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(slot, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(80.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(course.code, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(course.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Summary Card ──────────────────────────────────────────────────────────────

@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null
) {
    // FIX: card is clickable when an onClick is provided
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

// ── Settings ──────────────────────────────────────────────────────────────────

@Composable
fun SettingsPage(userName: String) {
    val user = globalUsers.find { it.username == userName }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val index = globalUsers.indexOfFirst { it.username == userName }
            if (index != -1) globalUsers[index] = globalUsers[index].copy(avatarUri = uri.toString())
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                if (user != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar tıklanınca galeriden fotoğraf seçilir
                        Box(contentAlignment = Alignment.BottomEnd) {
                            UserAvatar(
                                fullName = user.fullName,
                                username = user.username,
                                avatarUri = user.avatarUri,
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
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    user.role.name,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    if (user.email.isNotBlank()) {
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

    if (showChangePasswordDialog && user != null) {
        ChangePasswordDialog(
            user = user,
            onDismiss = { showChangePasswordDialog = false },
            onSave = { hashedPassword ->
                val index = globalUsers.indexOfFirst { it.username == userName }
                if (index != -1) globalUsers[index] = globalUsers[index].copy(
                    password = hashedPassword,
                    mustChangePassword = false
                )
                showChangePasswordDialog = false
            }
        )
    }
}

@Composable
fun ChangePasswordDialog(user: User, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = {
                when {
                    sha256(currentPassword) != user.password -> errorMsg = "Current password is incorrect"
                    newPassword.length < 6 -> errorMsg = "New password must be at least 6 characters"
                    newPassword != confirmPassword -> errorMsg = "Passwords do not match"
                    sha256(newPassword) == user.password -> errorMsg = "New password must be different from current"
                    else -> onSave(sha256(newPassword))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
