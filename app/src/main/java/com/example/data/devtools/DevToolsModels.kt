package com.example.data.devtools

import java.util.Locale

/**
 * Represents a user account monitored in the developer console.
 */
data class DevUserAccount(
    val uid: String,
    val displayName: String,
    val username: String,
    val email: String?,
    val photoUrl: String? = null,
    val isDev: Boolean = false,
    val isBanned: Boolean = false,
    val totalStorageBytes: Long = 0L,
    val notesStorageBytes: Long = 0L,
    val settingsStorageBytes: Long = 0L,
    val tasksStorageBytes: Long = 0L,
    val habitsStorageBytes: Long = 0L,
    val otherStorageBytes: Long = 0L,
    val quotaBytes: Long = 10L * 1024L * 1024L, // 10 MB per user quota
    val totalFocusMinutes: Int = 0,
    val completedTasksCount: Int = 0,
    val completedHabitsCount: Int = 0,
    val achievementsCount: Int = 0,
    val lastActiveMillis: Long = System.currentTimeMillis()
) {
    val usageRatio: Float
        get() = if (quotaBytes > 0) (totalStorageBytes.toFloat() / quotaBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val remainingBytes: Long
        get() = (quotaBytes - totalStorageBytes).coerceAtLeast(0L)
}

/**
 * High-level cloud storage quota and breakdown overview for the Firebase Base.
 */
data class DevStorageOverview(
    val totalQuotaBytes: Long = 100L * 1024L * 1024L, // 100 MB quota on Base Cloud
    val totalUsedBytes: Long,
    val notesBytes: Long,
    val settingsBytes: Long,
    val tasksBytes: Long,
    val habitsBytes: Long,
    val otherBytes: Long,
    val totalAccountsCount: Int,
    val activeAccountsCount: Int,
    val bannedAccountsCount: Int,
    val devAccountsCount: Int
) {
    val freeBytes: Long
        get() = (totalQuotaBytes - totalUsedBytes).coerceAtLeast(0L)

    val usedRatio: Float
        get() = if (totalQuotaBytes > 0) (totalUsedBytes.toFloat() / totalQuotaBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val notesRatio: Float
        get() = if (totalQuotaBytes > 0) (notesBytes.toFloat() / totalQuotaBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val settingsRatio: Float
        get() = if (totalQuotaBytes > 0) (settingsBytes.toFloat() / totalQuotaBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val tasksRatio: Float
        get() = if (totalQuotaBytes > 0) (tasksBytes.toFloat() / totalQuotaBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val habitsRatio: Float
        get() = if (totalQuotaBytes > 0) (habitsBytes.toFloat() / totalQuotaBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val otherRatio: Float
        get() = if (totalQuotaBytes > 0) (otherBytes.toFloat() / totalQuotaBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Utility to format byte quantities into human-readable strings (B, KB, MB, GB).
 */
fun formatStorageBytes(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
