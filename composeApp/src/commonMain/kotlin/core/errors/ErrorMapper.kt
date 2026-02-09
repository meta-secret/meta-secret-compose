package core.errors

import core.StringProviderInterface

class ErrorMapper(
    private val stringProvider: StringProviderInterface
) {
    fun mapExceptionToAppError(exception: Exception): AppError {
        return when (exception) {
            is IllegalStateException -> AppError.ValidationError(stringProvider.errorValidation())
            is IllegalArgumentException -> AppError.ValidationError(stringProvider.errorValidation())
            else -> {
                val message = exception.message ?: "Unknown error"
                when {
                    message.contains("network", ignoreCase = true) ||
                    message.contains("connection", ignoreCase = true) ||
                    message.contains("timeout", ignoreCase = true) -> AppError.NetworkError(stringProvider.errorNetwork())
                    message.contains("parse", ignoreCase = true) ||
                    message.contains("json", ignoreCase = true) ||
                    message.contains("serialization", ignoreCase = true) -> AppError.ParseError(stringProvider.errorParse())
                    message.contains("ffi", ignoreCase = true) ||
                    message.contains("native", ignoreCase = true) ||
                    message.contains("jni", ignoreCase = true) -> AppError.FfiError(stringProvider.errorInternal())
                    else -> AppError.UnknownError(stringProvider.errorUnknownPrefix() + message)
                }
            }
        }
    }
    
    fun getUserFriendlyMessage(error: AppError): String {
        return error.userFriendlyMessage
    }
}
