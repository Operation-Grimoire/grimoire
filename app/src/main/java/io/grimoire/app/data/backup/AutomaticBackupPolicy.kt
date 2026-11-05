package io.grimoire.app.data.backup

internal const val AUTOMATIC_BACKUP_REGISTRY_PREFIX = "backup_owned_auto_uris:"
internal const val AUTOMATIC_BACKUP_RETENTION = 7
private const val MAX_BACKUP_ATTEMPTS = 3
private val automaticBackupName = Regex(
    "grimoire_auto_backup_\\d{4}-\\d{2}-\\d{2}_\\d{6}_\\d{3}_" +
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.json\\.gz"
)

internal fun shouldRetryBackup(retryable: Boolean, runAttemptCount: Int): Boolean =
    retryable && runAttemptCount < MAX_BACKUP_ATTEMPTS - 1

internal data class AutomaticBackupFile(val uri: String, val name: String)

internal fun automaticBackupsToDelete(
    files: List<AutomaticBackupFile>,
    createdUris: Set<String>,
    currentUri: String,
): List<String> {
    val owned = files.distinctBy { it.uri }.filter {
        it.uri in createdUris && automaticBackupName.matches(it.name)
    }
    // Never prune unless this listing includes the backup that just succeeded.
    if (owned.none { it.uri == currentUri }) return emptyList()
    return owned.filter { it.uri != currentUri }.sortedByDescending { it.name }
        .drop(AUTOMATIC_BACKUP_RETENTION - 1).map { it.uri }
}
