package core

interface BackupCoordinatorInterface {
    fun ensureBackupDestinationSelected()
    suspend fun restoreIfNeeded()
    suspend fun backupIfChanged()
    fun clearAllBackups()
    suspend fun hasDatabaseFile(): Boolean
}