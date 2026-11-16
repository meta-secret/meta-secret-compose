package core.metaSecretCore.liveboard

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

class NativeReadEnvironment(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val ui: CoroutineDispatcher = Dispatchers.Main.immediate,
)
