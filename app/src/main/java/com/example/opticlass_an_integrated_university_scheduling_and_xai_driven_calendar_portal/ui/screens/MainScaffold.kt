package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.XAISuggestionResponseDto
import kotlinx.coroutines.launch

@Composable
fun OptiClassApp(viewModel: AppViewModel) {
    if (!viewModel.isLoggedIn) {
        LoginScreen { username, password, callback -> viewModel.login(username, password, callback) }
    } else {
        if (viewModel.mustChangePassword) {
            ForceChangePasswordScreen(
                encodedUsername = viewModel.currentUserName,
                onPasswordChanged = { viewModel.clearMustChangePassword() },
                onChangePassword = { current, new, cb -> viewModel.changePassword(current, new, cb) }
            )
        } else {
            MainScaffold(
                role = viewModel.userRole,
                userName = viewModel.currentUserName,
                onLogout = { viewModel.logout() },
                onChangePassword = { current, new, cb -> viewModel.changePassword(current, new, cb) },
                onAddUser = { u, p, r, fn, e, cb -> viewModel.addUser(u, p, r, fn, e, cb) },
                onDeleteUser = { u, cb -> viewModel.deleteUser(u, cb) },
                onUpdateUser = { u, fn, e, r, cb -> viewModel.updateUser(u, fn, e, r, cb) },
                onResetPassword = { u, np, cb -> viewModel.resetUserPassword(u, np, cb) },
                onImportCourses = { courses, cb -> viewModel.importCourseData(courses, cb) },
                onDeleteCourse = { code, cb -> viewModel.deleteCourse(code, cb) },
                onLoadMessages = { withUser, cb -> viewModel.loadMessages(withUser) { cb() } },
                onLoadAllMessages = { cb -> viewModel.loadMessages(null) { cb() } },
                onSendMessage = { to, content, cb -> viewModel.sendMessage(to, content, cb) },
                onMarkMessagesRead = { sender -> viewModel.markMessagesRead(sender) },
                onSubmitAvailability = { u, s, cb -> viewModel.submitAvailability(u, s, cb) },
                onSaveSchedule = { u, d, h -> viewModel.saveSchedule(u, d, h) },
                onMarkNotificationRead = { id -> viewModel.markNotificationRead(id) },
                onUpdateAvatar = { dataUrl, cb -> viewModel.updateAvatar(viewModel.currentUserName, dataUrl, cb) },
                onSendNotification = { r, t -> viewModel.sendNotification(r, t) },
                onAddClassroom = { c, cb -> viewModel.addClassroom(c, cb) },
                onDeleteClassroom = { id, cb -> viewModel.deleteClassroom(id, cb) },
                onImportClassrooms = { list, cb -> viewModel.importClassrooms(list, cb) },
                onRefreshInstructorData = { viewModel.refreshInstructorData() },
                onRefreshAvailabilities = { viewModel.refreshAvailabilities() },
                onSetSchedulingPhase = { phase, cb -> viewModel.setSchedulingPhase(phase, cb) },
                onSendChatBotMessage = { msg, cb -> viewModel.sendChatBotMessage(msg, cb) },
                onSuggestSchedule = { u, code, clsId, dur, type, cb -> viewModel.suggestScheduleSlot(u, code, dur, type, clsId, cb) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    role: UserRole,
    userName: String,
    onLogout: () -> Unit,
    onChangePassword: (String, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onAddUser: (String, String, String, String, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteUser: (String, (Boolean, String?) -> Unit) -> Unit = { _, _ -> },
    onUpdateUser: (String, String, String, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _, _, _ -> },
    onResetPassword: (String, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onImportCourses: (List<CourseImport>, (List<Pair<String, String>>) -> Unit) -> Unit = { _, _ -> },
    onDeleteCourse: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
    onLoadMessages: (withUser: String, () -> Unit) -> Unit = { _, _ -> },
    onLoadAllMessages: (() -> Unit) -> Unit = { _ -> },
    onSendMessage: (toUser: String, content: String, (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onMarkMessagesRead: (sender: String) -> Unit = { _ -> },
    onSubmitAvailability: (username: String, slots: Map<String, Set<String>>, (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onSaveSchedule: (username: String, draft: Map<String, androidx.compose.runtime.snapshots.SnapshotStateMap<String, CourseImport?>>, historyEntries: List<ScheduleChange>) -> Unit = { _, _, _ -> },
    onMarkNotificationRead: (id: String) -> Unit = { _ -> },
    onUpdateAvatar: (dataUrl: String, onResult: (Boolean) -> Unit) -> Unit = { _, _ -> },
    onSendNotification: (recipientUsername: String, text: String) -> Unit = { _, _ -> },
    onAddClassroom: (Classroom, (Boolean, String?) -> Unit) -> Unit = { _, _ -> },
    onDeleteClassroom: (String, (Boolean, String?) -> Unit) -> Unit = { _, _ -> },
    onImportClassrooms: (List<Classroom>, (Int, Int) -> Unit) -> Unit = { _, _ -> },
    onRefreshInstructorData: () -> Unit = {},
    onRefreshAvailabilities: () -> Unit = {},
    onSetSchedulingPhase: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
    onSendChatBotMessage: (String, (String) -> Unit) -> Unit = { _, _ -> },
    onSuggestSchedule: (username: String, courseCode: String, classroomId: String?, duration: Int, suggestionType: String, onResult: (XAISuggestionResponseDto?) -> Unit) -> Unit = { _, _, _, _, _, _ -> }
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.MAIN_PAGE) }
    var showNotificationMenu by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val unreadMsgCount = AppRepository.messages.count { it.recipient == userName && !it.isRead }
    val unreadNotifCount = AppRepository.notifications.count { !it.isRead && it.recipientName == userName }

    val latestMsgTimestamp = AppRepository.messages
        .filter { it.recipient == userName }
        .maxOfOrNull { it.timestamp } ?: 0L
    var lastKnownTimestamp by remember { mutableLongStateOf(0L) }
    LaunchedEffect(latestMsgTimestamp) {
        if (lastKnownTimestamp == 0L) {
            lastKnownTimestamp = latestMsgTimestamp
            return@LaunchedEffect
        }
        if (latestMsgTimestamp > lastKnownTimestamp) {
            val newMsg = AppRepository.messages
                .filter { it.recipient == userName && it.timestamp > lastKnownTimestamp }
                .maxByOrNull { it.timestamp }
            lastKnownTimestamp = latestMsgTimestamp
            if (newMsg != null) {
                val senderName = AppRepository.users.find { it.username == newMsg.sender }?.fullName
                    ?: newMsg.sender
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "New message from $senderName",
                        actionLabel = "View",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        currentDestination = if (role == UserRole.ADMIN) AppDestinations.MAIN_PAGE else AppDestinations.NOTIFICATIONS
                    }
                }
            }
        }
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
                                    val myNotifications = AppRepository.notifications.filter { it.recipientName == userName }
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
                                                    onMarkNotificationRead(notif.id)
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
                        AdminMainPage(
                            adminName = userName,
                            onLoadMessages = onLoadMessages,
                            onLoadAllMessages = onLoadAllMessages,
                            onSendMessage = onSendMessage,
                            onMarkMessagesRead = onMarkMessagesRead
                        )
                    role == UserRole.INSTRUCTOR && currentDestination == AppDestinations.MAIN_PAGE ->
                        InstructorMainPage(userName, onNavigate = { currentDestination = it }, onShowNotifications = { showNotificationMenu = true }, onRefresh = onRefreshInstructorData)
                    role == UserRole.INSTRUCTOR && currentDestination == AppDestinations.NOTIFICATIONS ->
                        ChatBox(
                            currentUserName = userName,
                            targetUserName = "admin",
                            onLoadMessages = onLoadMessages,
                            onSendMessage = onSendMessage,
                            onMarkMessagesRead = onMarkMessagesRead
                        )
                    currentDestination == AppDestinations.MY_AVAILABILITY ->
                        MyAvailabilityPage(
                            userName, snackbarHostState,
                            onSendMessage = onSendMessage,
                            onSubmitAvailability = onSubmitAvailability
                        )
                    currentDestination == AppDestinations.MY_LECTURES ->
                        MyLecturesPage(userName)
                    currentDestination == AppDestinations.MY_SCHEDULE ->
                        MySchedulePage(userName)
                    currentDestination == AppDestinations.INSTRUCTOR_AVAILABILITY ->
                        InstructorAvailabilityAdminPage(onRefresh = onRefreshAvailabilities)
                    currentDestination == AppDestinations.DATA_IMPORT ->
                        DataImportPage(
                            snackbarHostState,
                            onImport = onImportCourses,
                            onDeleteCourse = onDeleteCourse
                        )
                    currentDestination == AppDestinations.USER_TRANSACTIONS ->
                        UserTransactionsPage(
                            currentUserName = userName,
                            onAddUser = onAddUser,
                            onDeleteUser = onDeleteUser,
                            onUpdateUser = onUpdateUser,
                            onResetPassword = onResetPassword
                        )
                    currentDestination == AppDestinations.UPDATE_CALENDAR ->
                        UpdateCalendarPage(
                            snackbarHostState, userName,
                            onSaveSchedule = { u, d, h -> onSaveSchedule(u, d, h) },
                            onSendNotification = onSendNotification,
                            onSetSchedulingPhase = onSetSchedulingPhase,
                            onSuggestSchedule = onSuggestSchedule
                        )
                    currentDestination == AppDestinations.CLASSROOMS ->
                        ClassroomsPage(
                            snackbarHostState,
                            onAddClassroom = onAddClassroom,
                            onDeleteClassroom = onDeleteClassroom,
                            onImportClassrooms = onImportClassrooms
                        )
                    currentDestination == AppDestinations.SETTINGS ->
                        SettingsPage(userName, onChangePassword = onChangePassword, onUpdateAvatar = onUpdateAvatar)
                    currentDestination == AppDestinations.CHATBOT ->
                        ChatBotPage(onSendMessage = onSendChatBotMessage)
                    else -> GenericPage(currentDestination.label)
                }
            }
        }
    }
}
