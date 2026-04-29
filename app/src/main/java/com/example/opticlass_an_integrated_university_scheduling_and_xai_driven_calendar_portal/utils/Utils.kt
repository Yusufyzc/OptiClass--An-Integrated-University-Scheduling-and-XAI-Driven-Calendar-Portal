package com.example.opticlass_an_integrated_university_scheduling_and_xai_driven_calendar_portal

import android.util.Base64
import androidx.compose.ui.graphics.Color
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
val TIME_SLOTS = listOf("08:00 AM", "09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM", "01:00 PM", "02:00 PM", "03:00 PM", "04:00 PM", "05:00 PM")

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

private val ACADEMIC_TITLES = setOf(
    "prof.", "dr.", "doç.", "yrd.", "öğr.", "gör.", "arş.",
    "prof", "dr", "doç", "yrd", "öğr", "gör", "arş"
)

fun normalizeTurkish(str: String): String =
    str.replace('ş', 's').replace('Ş', 's')
       .replace('ç', 'c').replace('Ç', 'c')
       .replace('ü', 'u').replace('Ü', 'u')
       .replace('ğ', 'g').replace('Ğ', 'g')
       .replace('ı', 'i').replace('İ', 'i')
       .replace('ö', 'o').replace('Ö', 'o')

fun generateUsername(fullName: String): String {
    val parts = fullName.split(" ")
        .map { it.lowercase() }
        .filter { it !in ACADEMIC_TITLES }
        .map { normalizeTurkish(it) }
        .map { it.replace(Regex("[^a-z0-9]"), "") }
        .filter { it.isNotBlank() }

    val base = when {
        parts.size >= 2 -> "${parts.first()}_${parts.last()}"
        parts.size == 1 -> parts.first()
        else -> "user"
    }

    val allUsernames = globalUsers.map { decodeUsername(it.username) }
    if (base !in allUsernames) return base
    var i = 2
    while ("${base}_$i" in allUsernames) i++
    return "${base}_$i"
}

fun generatePassword(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
    return (1..6).map { chars.random() }.joinToString("")
}

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
