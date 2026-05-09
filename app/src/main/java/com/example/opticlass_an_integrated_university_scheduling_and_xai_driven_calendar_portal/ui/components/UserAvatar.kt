package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UserAvatar(
    fullName: String,
    username: String,
    avatarUri: String? = null,
    size: Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor(username)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = userInitials(fullName),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.35f).sp
        )

        if (avatarUri != null) {
            if (avatarUri.startsWith("http")) {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val context = LocalContext.current
                val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = avatarUri) {
                    value = withContext(Dispatchers.IO) {
                        try {
                            if (avatarUri.startsWith("data:")) {
                                val base64 = avatarUri.substringAfter(",")
                                val bytes = Base64.decode(base64, Base64.DEFAULT)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } else {
                                context.contentResolver.openInputStream(Uri.parse(avatarUri))?.use { stream ->
                                    BitmapFactory.decodeStream(stream)
                                }
                            }
                        } catch (e: Exception) { null }
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}
