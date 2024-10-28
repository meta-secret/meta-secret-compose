package storage

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

class KeyValueStorageImpl : KeyValueStorage {
    private val settings: Settings by lazy { Settings() }
//    private val observableSettings: ObservableSettings by lazy { settings as ObservableSettings }

    override var isOnboardingCompleted: Boolean
        get() = settings.getBoolean(StorageKeys.ONBOARDING_INFO.key, defaultValue = false)
        set(value) {
            settings[StorageKeys.ONBOARDING_INFO.key] = value
        }

    override var isSignInCompleted: Boolean
        get() = settings.getBoolean(StorageKeys.SIGNIN_INFO.key, defaultValue = false)
        set(value) {
            settings[StorageKeys.SIGNIN_INFO.key] = value
        }

    // #2 - store/retrive custom types
//    override var loginInfo: LoginInfo?
//        get() = settings.decodeValueOrNull(LoginInfo.serializer(), StorageKeys.LOGIN_INFO.key)
//        set(value) {
//            if (value != null) {
//                settings.encodeValue(LoginInfo.serializer(), StorageKeys.LOGIN_INFO.key, value)
//            } else {
//                settings.remove(StorageKeys.TOKEN.key)
//            }
//        }

    // #3 - listen to token value changes
//    override val observableToken: Flow<String>
//        get() = observableSettings.getStringFlow(StorageKeys.TOKEN.key, "")

    // clean all the stored values
    override fun cleanStorage() {
        settings.clear()
    }
}