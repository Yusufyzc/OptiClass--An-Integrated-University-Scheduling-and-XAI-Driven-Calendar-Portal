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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.ui.theme.OptiClassAn_Integrated_University_Scheduling_and_XAIDriven_Calendar_PortalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

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

data class CourseImport(
    val code: String,
    val name: String,
    val lecturer: String,
    val department: String,
    val email: String
)

data class User(
    val username: String,
    val password: String,
    val role: UserRole,
    val fullName: String,
    val email: String,
    val courses: MutableList<CourseImport> = mutableListOf(),
    val schedule: SnapshotStateMap<String, SnapshotStateMap<String, CourseImport?>> = mutableStateMapOf()
)

// Global in-memory state
val globalMessages = mutableStateListOf<Message>()
val globalNotifications = mutableStateListOf<AppNotification>()
val globalAvailabilities = mutableStateListOf<Availability>()
val globalCourseImports = mutableStateListOf<CourseImport>()
val globalUsers = mutableStateListOf(
    User("admin", "admin123", UserRole.ADMIN, "System Admin", "admin@opticlass.com"),
    User("berkay", "berkay123", UserRole.INSTRUCTOR, "Berkay", "berkay@example.com")
)
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
    MY_LECTURES("My Lectures", Icons.Default.Menu, roleRestriction = UserRole.INSTRUCTOR),
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
                val user = globalUsers.find { it.username.equals(username, ignoreCase = true) && it.password == password }
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
    var lastMessageCount by remember { mutableIntStateOf(globalMessages.size) }
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
                    role == UserRole.INSTRUCTOR && currentDestination == AppDestinations.MAIN_PAGE -> InstructorMainPage(userName)
                    role == UserRole.INSTRUCTOR && currentDestination == AppDestinations.NOTIFICATIONS -> ChatBox(userName, "admin")
                    currentDestination == AppDestinations.MY_AVAILABILITY -> MyAvailabilityPage(userName)
                    currentDestination == AppDestinations.MY_LECTURES -> MyLecturesPage(userName)
                    currentDestination == AppDestinations.MY_SCHEDULE -> MySchedulePage(userName)
                    currentDestination == AppDestinations.INSTRUCTOR_AVAILABILITY -> InstructorAvailabilityAdminPage()
                    currentDestination == AppDestinations.DATA_IMPORT -> DataImportPage(snackbarHostState)
                    currentDestination == AppDestinations.USER_TRANSACTIONS -> UserTransactionsPage()
                    currentDestination == AppDestinations.UPDATE_CALENDAR -> UpdateCalendarPage(snackbarHostState)
                    currentDestination == AppDestinations.SETTINGS -> SettingsPage(userName)
                    else -> GenericPage(currentDestination.label)
                }
            }
        }
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateCalendarPage(snackbarHostState: SnackbarHostState) {
    var expanded by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<User?>(null) }
    val instructors = globalUsers.filter { it.role == UserRole.INSTRUCTOR }
    val scope = rememberCoroutineScope()

    var selectedCourseToAssign by remember { mutableStateOf<CourseImport?>(null) }
    
    // Warning Dialog State
    var showAvailabilityWarning by remember { mutableStateOf<Pair<String, String>?>(null) } // Day, Slot

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Update Calendar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                instructors.forEach { user ->
                    DropdownMenuItem(
                        text = { Text(user.fullName) },
                        onClick = {
                            selectedUser = user
                            expanded = false
                            selectedCourseToAssign = null
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (selectedUser != null) {
            val user = selectedUser!!
            val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
            val timeSlots = listOf("08:00 AM", "09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM", "01:00 PM", "02:00 PM", "03:00 PM", "04:00 PM", "05:00 PM")
            
            val availability = globalAvailabilities.find { it.instructorName == user.username }
            val availableSlots = availability?.slots ?: emptyMap()
            
            val draftSchedule = remember(user.username) {
                val map = mutableStateMapOf<String, SnapshotStateMap<String, CourseImport?>>()
                days.forEach { day -> 
                    val innerMap = mutableStateMapOf<String, CourseImport?>()
                    timeSlots.forEach { slot ->
                        innerMap[slot] = user.schedule[day]?.get(slot)
                    }
                    map[day] = innerMap
                }
                map
            }

            Text("Assigned Courses (Tap to select, then tap grid to assign):", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                days, timeSlots, availableSlots, draftSchedule, selectedCourseToAssign,
                onCellClick = { day, slot ->
                    if (selectedCourseToAssign != null) {
                        val isAvailable = availableSlots[day]?.contains(slot) == true
                        if (!isAvailable) {
                            showAvailabilityWarning = day to slot
                        } else {
                            draftSchedule[day]?.set(slot, selectedCourseToAssign)
                        }
                    }
                }
            )

            if (showAvailabilityWarning != null) {
                AlertDialog(
                    onDismissRequest = { showAvailabilityWarning = null },
                    title = { Text("Availability Warning") },
                    text = { Text("The instructor is not available at this time (${showAvailabilityWarning?.first} ${showAvailabilityWarning?.second}), are you sure about assigning this hour?") },
                    confirmButton = {
                        TextButton(onClick = {
                            val (d, s) = showAvailabilityWarning!!
                            draftSchedule[d]?.set(s, selectedCourseToAssign)
                            showAvailabilityWarning = null
                        }) { Text("Yes, Assign") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAvailabilityWarning = null }) { Text("Cancel") }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    user.schedule.clear()
                    draftSchedule.forEach { (day, slots) ->
                        val innerMap = mutableStateMapOf<String, CourseImport?>()
                        innerMap.putAll(slots)
                        user.schedule[day] = innerMap
                    }
                    globalNotifications.add(AppNotification(
                        id = System.currentTimeMillis().toString(),
                        text = "Admin updated your weekly schedule. Please check 'My Schedule'."
                    ))
                    scope.launch { snackbarHostState.showSnackbar("Schedule updated and notification sent!") }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save & Notify Instructor")
            }
        }
    }
}

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

@Composable
fun SchedulingGridEnhanced(
    days: List<String>, 
    timeSlots: List<String>, 
    availableSlots: Map<String, Set<String>>,
    draftSchedule: MutableMap<String, SnapshotStateMap<String, CourseImport?>>,
    selectedCourse: CourseImport?,
    onCellClick: (String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color.LightGray)) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.secondaryContainer)) {
            Box(modifier = Modifier.width(80.dp).padding(8.dp)) { Text("Time", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.Gray))
            days.forEach { day ->
                Box(modifier = Modifier.weight(1f).padding(8.dp), contentAlignment = Alignment.Center) {
                    Text(day, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            timeSlots.forEach { slot ->
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(0.5.dp, Color.LightGray)) {
                    Box(modifier = Modifier.width(80.dp).padding(8.dp).background(Color(0xFFF9F9F9))) {
                        Text(slot, fontSize = 9.sp)
                    }
                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.LightGray))
                    days.forEach { day ->
                        val isAvailable = availableSlots[day]?.contains(slot) == true
                        val scheduledCourse = draftSchedule[day]?.get(slot)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .border(0.5.dp, Color.LightGray)
                                .background(
                                    when {
                                        scheduledCourse != null -> MaterialTheme.colorScheme.primaryContainer
                                        isAvailable -> Color.Green.copy(alpha = 0.1f)
                                        else -> Color.Red.copy(alpha = 0.05f)
                                    }
                                )
                                .clickable {
                                    onCellClick(day, slot)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (scheduledCourse != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(scheduledCourse.code, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    IconButton(onClick = { draftSchedule[day]?.set(slot, null) }, modifier = Modifier.size(16.dp)) {
                                        Icon(Icons.Default.Clear, contentDescription = "Remove", tint = Color.Red)
                                    }
                                }
                            } else if (!isAvailable) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MySchedulePage(userName: String) {
    val user = globalUsers.find { it.username == userName } ?: return
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    val timeSlots = listOf("08:00 AM", "09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM", "01:00 PM", "02:00 PM", "03:00 PM", "04:00 PM", "05:00 PM")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Weekly Schedule", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray)) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.primaryContainer)) {
                Box(modifier = Modifier.width(80.dp).padding(8.dp)) { Text("Time", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.Gray))
                days.forEach { day ->
                    Box(modifier = Modifier.weight(1f).padding(8.dp), contentAlignment = Alignment.Center) { Text(day, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
                items(timeSlots) { slot ->
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(0.5.dp, Color.LightGray)) {
                        Box(modifier = Modifier.width(80.dp).padding(8.dp).background(Color(0xFFF5F5F5))) { Text(slot, fontSize = 10.sp) }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.LightGray))
                        days.forEach { day ->
                            val course = user.schedule[day]?.get(slot)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .border(0.5.dp, Color.LightGray)
                                    .background(if (course != null) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                if (course != null) {
                                    Text(course.code, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DataImportPage(snackbarHostState: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val success = importExcelData(context, uri)
                if (success) {
                    snackbarHostState.showSnackbar("Excel data imported! Review and save below.")
                } else {
                    snackbarHostState.showSnackbar("Failed to parse Excel file. Check format.")
                }
            }
        }
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
                    
                    Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)).padding(4.dp)) {
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
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
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
            onClick = { filePickerLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Select Excel File", tint = Color.White)
        }
        
    }
}

fun saveCourseImport(course: CourseImport) {
    val username = course.email.substringBefore("@").lowercase()
    val existingUser = globalUsers.find { it.username.lowercase() == username }
    
    if (existingUser != null) {
        if (existingUser.courses.none { it.code == course.code }) {
            existingUser.courses.add(course)
        }
    } else {
        val newUser = User(
            username = username,
            password = "${username}123",
            role = UserRole.INSTRUCTOR,
            fullName = course.lecturer,
            email = course.email,
            courses = mutableListOf(course)
        )
        globalUsers.add(newUser)
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
                
                if (code.isNotEmpty() && name.isNotEmpty()) {
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
        
        Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray)) {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.primaryContainer)) {
                Box(modifier = Modifier.width(80.dp).padding(8.dp)) { Text("Time", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.Gray))
                days.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp)
                            .clickable {
                                val currentSet = selectedSlots[day] ?: mutableSetOf()
                                if (currentSet.size == timeSlots.size) {
                                    selectedSlots[day] = mutableSetOf()
                                } else {
                                    selectedSlots[day] = timeSlots.toMutableSet()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) { 
                        Text(day, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) 
                    }
                }
            }
            
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                items(timeSlots) { slot ->
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(0.5.dp, Color.LightGray)) {
                        Box(modifier = Modifier.width(80.dp).padding(8.dp).background(Color(0xFFF5F5F5))) { Text(slot, fontSize = 10.sp) }
                        Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.LightGray))
                        days.forEach { day ->
                            val isSelected = selectedSlots[day]?.contains(slot) == true
                            Box(
                                modifier = Modifier
                                    .weight(1f).height(40.dp).border(0.5.dp, Color.LightGray)
                                    .background(if (isSelected) Color.Green.copy(alpha = 0.3f) else Color.Transparent)
                                    .clickable {
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
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(MaterialTheme.colorScheme.primaryContainer)) {
            Box(modifier = Modifier.width(80.dp).padding(8.dp)) { Text("Time", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.Gray))
            days.forEach { day ->
                Box(modifier = Modifier.weight(1f).padding(8.dp), contentAlignment = Alignment.Center) { Text(day, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
            items(timeSlots) { slot ->
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(0.5.dp, Color.LightGray)) {
                    Box(modifier = Modifier.width(80.dp).padding(8.dp).background(Color(0xFFF5F5F5))) { Text(slot, fontSize = 10.sp) }
                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.LightGray))
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
            if (availability != null) {
                TextButton(onClick = { selectedInstructor = null }) {
                    Text("< Back to list")
                }
                Text("Availability for ${availability.instructorName}", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                val readOnlySlots = remember(availability.instructorName) {
                    availability.slots.mapValues { it.value.toMutableSet() }.toMutableMap()
                }
                AvailabilityTable(
                    days = listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
                    timeSlots = listOf("08:00 AM", "09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM", "01:00 PM", "02:00 PM", "03:00 PM", "04:00 PM", "05:00 PM"),
                    selectedSlots = readOnlySlots,
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.fullName, fontWeight = FontWeight.Bold)
                            Text(
                                "@${user.username} · ${user.role.name}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            if (user.email.isNotBlank()) {
                                Text(user.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        IconButton(onClick = { userToEdit = user }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                        }
                        if (user.role != UserRole.ADMIN) {
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
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = fullName, onValueChange = { fullName = it },
                    label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
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
                    globalUsers.any { it.username.equals(trimmedUsername, ignoreCase = true) } -> errorMsg = "Username already exists"
                    fullName.isBlank() -> errorMsg = "Full name is required"
                    password.length < 6 -> errorMsg = "Password must be at least 6 characters"
                    else -> onAdd(User(trimmedUsername.lowercase(), password, role, fullName.trim(), email.trim()))
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
    var password by remember { mutableStateOf(user.password) }
    var errorMsg by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit User") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Username: @${user.username}", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                OutlinedTextField(
                    value = fullName, onValueChange = { fullName = it },
                    label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    fullName.isBlank() -> errorMsg = "Full name is required"
                    password.length < 6 -> errorMsg = "Password must be at least 6 characters"
                    else -> onSave(user.copy(fullName = fullName.trim(), email = email.trim(), password = password))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun InstructorMainPage(userName: String) {
    val user = globalUsers.find { it.username == userName } ?: return
    val unreadMessages = globalMessages.count { it.recipient == userName && !it.isRead }
    val unreadNotifs = globalNotifications.count { !it.isRead }
    val availabilitySubmitted = globalAvailabilities.any { it.instructorName == userName }

    val timeSlots = listOf("08:00 AM", "09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM", "01:00 PM", "02:00 PM", "03:00 PM", "04:00 PM", "05:00 PM")
    val dayMap = mapOf(2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri")
    val todayKey = dayMap[java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)]
    val todayClasses = if (todayKey != null) {
        timeSlots.mapNotNull { slot ->
            val course = user.schedule[todayKey]?.get(slot)
            if (course != null) slot to course else null
        }
    } else emptyList()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Welcome, ${user.fullName}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(modifier = Modifier.weight(1f), title = "Courses", value = user.courses.size.toString(), icon = Icons.Default.Menu)
            SummaryCard(modifier = Modifier.weight(1f), title = "Messages", value = unreadMessages.toString(), icon = Icons.Default.Email)
            SummaryCard(modifier = Modifier.weight(1f), title = "Alerts", value = unreadNotifs.toString(), icon = Icons.Default.Notifications)
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
                    if (availabilitySubmitted)
                        "Availability submitted to admin"
                    else
                        "Availability not submitted yet — go to 'My Availability'",
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
                                Text(
                                    course.name,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(modifier: Modifier = Modifier, title: String, value: String, icon: ImageVector) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
fun SettingsPage(userName: String) {
    val user = globalUsers.find { it.username == userName }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                if (user != null) {
                    Text("Name:      ${user.fullName}")
                    Text("Username:  @${user.username}")
                    Text("Role:      ${user.role.name}")
                    if (user.email.isNotBlank()) Text("Email:     ${user.email}")
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
}