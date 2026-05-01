package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.util.Log
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
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.MessageDto
import com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal.network.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatBox(currentUserName: String, targetUserName: String, viewModel: AppViewModel) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var chatMessages by remember { mutableStateOf<List<MessageDto>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var isSending by remember { mutableStateOf(false) }

    // Polling: Sayfa açık kaldığı sürece her 3 saniyede bir mesajları çeker
    LaunchedEffect(Unit) {
        val token = "Bearer ${viewModel.authToken}"
        while (isActive) {
            try {
                // targetUserName'i query (with) olarak gönderiyoruz
                val response = RetrofitClient.instance.getMessages(token, targetUserName)
                if (response.isSuccessful) {
                    val msgs = response.body() ?: emptyList()

                    if (chatMessages != msgs) {
                        chatMessages = msgs

                        // Okunmamış mesajları API'de 'Okundu' olarak işaretle
                        msgs.forEach { m ->
                            if (m.recipientUsername == currentUserName && !m.isRead) {
                                m.id?.let { id ->
                                    RetrofitClient.instance.markMessageRead(token, id)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatBox", "Mesajlar çekilirken hata oluştu", e)
            }
            delay(3000) // 3 saniye bekle ve döngüyü tekrarla
        }
    }

    // Yeni mesaj geldiğinde listeyi en alta kaydır
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
                val isMe = msg.senderUsername == currentUserName
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

                        // Long tipi timestamp'i okunabilir saate çeviriyoruz
                        val timeStr = msg.createdAt?.let {
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
                        } ?: ""

                        Text(
                            timeStr,
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
            IconButton(
                enabled = !isSending && text.isNotBlank(),
                onClick = {
                    scope.launch {
                        isSending = true
                        try {
                            val msgDto = MessageDto(
                                senderUsername = currentUserName,
                                recipientUsername = targetUserName,
                                content = text
                            )
                            val token = "Bearer ${viewModel.authToken}"
                            val response = RetrofitClient.instance.sendMessage(token, msgDto)
                            if (response.isSuccessful) {
                                text = "" // Başarılıysa input'u temizle
                            }
                        } catch (e: Exception) {
                            Log.e("ChatBox", "Mesaj gönderilirken hata oluştu", e)
                        } finally {
                            isSending = false
                        }
                    }
                }
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}