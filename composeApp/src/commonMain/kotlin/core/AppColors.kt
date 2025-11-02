package core

import androidx.compose.ui.graphics.Color

object LogTags {
    const val DEVICES_VM = "📱DevicesScreenViewModel"
    const val SPLASH_VM = "🚀SplashScreenViewModel"
    const val MAIN_VM = "🧭MainScreenViewModel"
    const val ONBOARDING_VM = "🎯OnboardingViewModel"
    const val SIGNIN_VM = "🔐SignInScreenViewModel"
    const val APP_MANAGER = "🛠️MetaSecretAppManager"
    const val SOCKET_HANDLER = "🔌MetaSecretSocketHandler"
    const val STATE_RESOLVER = "🧩MetaSecretStateResolver"
    const val ADD_SECRET_VM = "➕AddSecretViewModel"
    const val SECRETS_VM = "🔐SecretsScreenViewModel"
    const val SHOW_SECRET_VM = "👀ShowSecretViewModel"
    const val PROFILE_VM = "👤ProfileScreenViewModel"
    const val VAULT_STATS_PROVIDER = "📊VaultStatsProvider"
}

object AppColors {

    //White
    val White = Color(0xFFFFFFFF)
    val White75 = Color(0xFFFFFFFF).copy(alpha = 0.75f)
    val White50 = Color(0xFFFFFFFF).copy(alpha = 0.5f)
    val White30 = Color(0xFFFFFFFF).copy(alpha = 0.3f)
    val White10 = Color(0xFFFFFFFF).copy(alpha = 0.10f)
    val White5 = Color(0xFF263752)

    //Blue
    val ActionLink = Color(0xFF90BDFF)
    val ActionMain = Color(0xFF0368FF)
    val ActionPremium = Color(0xFF3C8AFF)

    //Red
    val RedError = Color(0xFFFF0952)

    //Black
    val TabBar = Color(0xFF232324)
    val PopUp = Color(0xFF262638)
    val TextField = Color(0xFF1D1515)
    val Black30 = Color.Black.copy(alpha = 0.3f)
    val Black60 = Color.Black.copy(alpha = 0.6f)

    //Orange
    val Warning = Color(0xFFFF9900)
}