package core

interface BackupCoordinatorInterface {
    fun ensureBackupDestinationSelected()
    fun restoreIfNeeded()
    fun backupIfChanged()
    fun clearAllBackups()
}