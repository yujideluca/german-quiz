package dev.yuji.quiz.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val BACKUP_FORMAT = 1

/** Everything needed to continue studying on another phone: card states, answer history, goal and theme. */
@Serializable
data class ProgressBackup(
    val format: Int = BACKUP_FORMAT,
    val exportedAtMillis: Long,
    val appVersion: String,
    val progress: Map<String, CardProgress>,
    val history: Map<Long, Int>,
    val dailyGoal: Int,
    val themeMode: String? = null,
)

sealed interface BackupParse {
    data class Ok(val backup: ProgressBackup) : BackupParse
    data class Invalid(val reason: String) : BackupParse
}

private val backupJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

fun encodeBackup(backup: ProgressBackup): String = backupJson.encodeToString(ProgressBackup.serializer(), backup)

/** Reads a backup file. Entries that cannot be trusted are dropped instead of failing the whole import. */
fun decodeBackup(text: String): BackupParse {
    val backup = runCatching { backupJson.decodeFromString(ProgressBackup.serializer(), text) }
        .getOrElse { return BackupParse.Invalid("This is not a German Quiz progress file.") }
    if (backup.format > BACKUP_FORMAT) {
        return BackupParse.Invalid("This file was made by a newer version of the app. Update the app first.")
    }
    return BackupParse.Ok(
        backup.copy(
            progress = backup.progress.filter { (id, p) -> id == p.cardId },
            history = backup.history.filterValues { it > 0 },
            dailyGoal = clampDailyGoal(backup.dailyGoal),
        )
    )
}
