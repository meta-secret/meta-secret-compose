package utils

actual class MetaSecretApi {
    actual companion object {
        init {
            System.loadLibrary("meta_secret_core")
        }

        private external fun metaSecretInit(dbPath: String): Int
        private external fun metaSecretJoinCluster(candidateId: String): Int

        actual fun initialize(dbPath: String): Boolean {
            return metaSecretInit(dbPath) == 0
        }

        actual fun joinCluster(candidateId: String): JoinStatus {
            return when (metaSecretJoinCluster(candidateId)) {
                0 -> JoinStatus.SUCCESS
                1 -> JoinStatus.PENDING
                2 -> JoinStatus.DECLINED
                3 -> JoinStatus.ALREADY_MEMBER
                else -> JoinStatus.ERROR
            }
        }
    }
}