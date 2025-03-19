package utils

import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
actual class MetaSecretApi {
    actual companion object {
        @OptIn(ExperimentalForeignApi::class)
        actual fun initialize(dbPath: String): Boolean {
            return memScoped {
                val cPath = dbPath.cstr.ptr
                val result = meta_secret_init(cPath)
                result == 0
            }
        }

        @OptIn(ExperimentalForeignApi::class)
        actual fun joinCluster(candidateId: String): JoinStatus {
            return memScoped {
                val cCandidateId = candidateId.cstr.ptr
                val statusCode = meta_secret_join_cluster(cCandidateId)
                when (statusCode) {
                    0 -> JoinStatus.SUCCESS
                    1 -> JoinStatus.PENDING
                    2 -> JoinStatus.DECLINED
                    3 -> JoinStatus.ALREADY_MEMBER
                    else -> JoinStatus.ERROR
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun meta_secret_init(dbPath: CPointer<ByteVar>): Int {
    return metaSecretInit(dbPath)
}

@OptIn(ExperimentalForeignApi::class)
private fun meta_secret_join_cluster(candidateId: CPointer<ByteVar>): Int {
    return metaSecretJoinCluster(candidateId)
}

@OptIn(ExperimentalForeignApi::class)
private external fun metaSecretInit(dbPath: CPointer<ByteVar>): Int

@OptIn(ExperimentalForeignApi::class)
private external fun metaSecretJoinCluster(candidateId: CPointer<ByteVar>): Int