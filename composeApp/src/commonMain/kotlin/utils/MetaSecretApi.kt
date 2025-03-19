package utils

enum class JoinStatus {
    SUCCESS,
    PENDING,
    DECLINED,
    ALREADY_MEMBER,
    ERROR;

    val description: String
        get() = when (this) {
            SUCCESS -> "Success"
            PENDING -> "User is already in pending state"
            DECLINED -> "User request has been declined"
            ALREADY_MEMBER -> "User is already a member"
            ERROR -> "Error occurred"
        }
}

expect class MetaSecretApi {
    companion object {
        fun initialize(dbPath: String): Boolean
        fun joinCluster(candidateId: String): JoinStatus
    }
}