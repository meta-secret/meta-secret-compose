package models.appInternalModels

// TODO: #44 Localize all errors
enum class AppErrors(val value: String) {
    CreateLocalError("CreateLocalError"),
    CredsGenerationError("CredsGenerationError"),
    SignUpError("SignUpError")
}