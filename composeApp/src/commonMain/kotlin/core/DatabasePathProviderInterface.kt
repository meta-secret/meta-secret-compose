package core

interface DatabasePathProviderInterface {
    suspend fun getDatabaseFileName(): String?
}

