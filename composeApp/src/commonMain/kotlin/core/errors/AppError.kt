package core.errors

sealed class AppError(val userFriendlyMessage: String) {
    class NetworkError(message: String) : AppError(message)
    class FfiError(message: String) : AppError(message)
    class ParseError(message: String) : AppError(message)
    class ValidationError(message: String) : AppError(message)
    class UnknownError(message: String) : AppError(message)
}
