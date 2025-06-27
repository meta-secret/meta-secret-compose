package models.appInternalModels

// TODO: Localize all errors
enum class AppErrors(val value: String) {
    CreateLocalError("CreateLocalError"),
    CredsGenerationError("CredsGenerationError"),
    SignUpError("SignUpError")
}