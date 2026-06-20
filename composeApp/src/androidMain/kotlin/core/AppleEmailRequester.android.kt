package core

class AppleEmailRequesterAndroid : AppleEmailRequesterInterface {
    override suspend fun requestAppleEmail(): AppleEmailAuthResult {
        return AppleEmailAuthResult.Error("Apple sign-in is not supported on Android")
    }
}
