package sharedData

class MetaSecretCoreServiceAndroid: MetaSecretCoreInterface {
    
    companion object {
        init {
            try {
                System.loadLibrary("metasecret_mobile")
                println("Библиотека metasecret_mobile успешно загружена")
            } catch (e: Exception) {
                println("Ошибка загрузки библиотеки: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // Внутренний класс для JNI обертки
    private object NativeLib {
        // Экспортируемый метод из Rust: sign_up
        @JvmStatic
        external fun sign_up(userName: String): String
    }
    
    override fun signUp(name: String) {
        try {
            // Вызов нативного метода через обертку
            val result = NativeLib.sign_up(name)
            println("Sign up result: $result")
        } catch (e: Exception) {
            println("Ошибка вызова нативного метода: ${e.message}")
            e.printStackTrace()
        }
    }
}