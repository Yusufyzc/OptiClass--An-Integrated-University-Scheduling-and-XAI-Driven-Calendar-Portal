package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ChatBox(
    currentUserName: String,
    targetUserName: String,
    onLoadMessages: (withUser: String, () -> Unit) -> Unit = { _, _ -> },
    onSendMessage: (toUser: String, content: String, (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onMarkMessagesRead: (sender: String) -> Unit = { _ -> }
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var isSending by remember { mutableStateOf(false) }

    LaunchedEffect(targetUserName) {
        onLoadMessages(targetUserName) {
            onMarkMessagesRead(targetUserName)
        }
    }

    LaunchedEffect(targetUserName) {
        while (true) {
            delay(5000)
            onLoadMessages(targetUserName) {}
        }
    }

    LaunchedEffect(AppRepository.messages.size) {
        AppRepository.messages
            .filter { it.sender == targetUserName && it.recipient == currentUserName && !it.isRead }
            .forEach { AppRepository.markMessageRead(it.timestamp) }
    }

    val chatMessages = AppRepository.messages.filter {
        (it.sender == currentUserName && it.recipient == targetUserName) ||
        (it.sender == targetUserName && it.recipient == currentUserName)
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val targetFullName = AppRepository.users.find { it.username == targetUserName }?.fullName ?: targetUserName
        Text("Chat with $targetFullName", style = MaterialTheme.typography.titleLarge)
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
                placeholder = { Text("Message...") },
                enabled = !isSending
            )
            IconButton(
                onClick = {
                    if (text.isNotBlank() && !isSending) {
                        isSending = true
                        val content = text
                        text = ""
                        onSendMessage(targetUserName, content) { isSending = false }
                    }
                },
                enabled = !isSending
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
