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
import kotlinx.coroutines.launch

@Composable
fun OptiClassApp(viewModel: AppViewModel) {
    if (!viewModel.isLoggedIn) {
        LoginScreen { username, password -> viewModel.login(username, password) }
    } else {
        val currentUser = AppRepository.users.find { it.username == viewModel.currentUserName }
        if (currentUser?.mustChangePassword == true) {
            ForceChangePasswordScreen(
                encodedUsername = viewModel.currentUserName,
                onPasswordChanged = { }
            )
        } else {
            MainScaffold(
                role = viewModel.userRole,
                userName = viewModel.currentUserName,
                onLogout = { viewModel.logout() }
            )
        }
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

    val unreadMsgCount = AppRepository.messages.count { it.recipient == userName && !it.isRead }
    val unreadNotifCount = AppRepository.notifications.count { !it.isRead && it.recipientName == userName }

    var lastMessageCount by remember { mutableIntStateOf(AppRepository.messages.size) }
    LaunchedEffect(AppRepository.messages.size) {
        if (AppRepository.messages.size > lastMessageCount) {
            val lastMsg = AppRepository.messages.last()
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
        lastMessageCount = AppRepository.messages.size
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
                                                    val idx = AppRepository.notifications.indexOfFirst { it.id == notif.id }
                                                    if (idx != -1) AppRepository.notifications[idx] = notif.copy(isRead = true)
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
