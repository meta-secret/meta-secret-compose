package sharedData

import com.metaSecret.ios.MetaSecretCoreBridge
import kotlinx.cinterop.ExperimentalForeignApi

class MetaSecretCoreServiceIos: MetaSecretCoreInterface {
    @OptIn(ExperimentalForeignApi::class)
    override fun signUp(name: String) {
        MetaSecretCoreBridge().signUp()
    }
}