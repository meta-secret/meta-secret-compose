package core

import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

enum class AppImage {
    AddButton,
    Android,
    Apple,
    Arrow,
    BackgroundAdd,
    BackgroundLogo,
    BackgroundMain,
    Cli,
    Close,
    CloseBlue,
    ComposeMultiplatform,
    Devices,
    DevicesLogo,
    EmailReceivedCheck,
    Executioner,
    Google,
    IconCopy,
    IconEmail,
    IconEyeOff,
    IconLock,
    IconPaste,
    Laptop,
    Logo,
    MetaSecretPicture,
    Other,
    ProfileLogo,
    SecretsLogo,
    SettingsIcon,
    ShieldL1,
    ShieldL2,
    ShieldL3,
    Tablet,
    Text,
    Trashbox,
    Warning,
    Web,
}

fun AppImage.resource(): DrawableResource = when (this) {
    AppImage.AddButton -> Res.drawable.addbutton
    AppImage.Android -> Res.drawable.android
    AppImage.Apple -> Res.drawable.apple
    AppImage.Arrow -> Res.drawable.arrow
    AppImage.BackgroundAdd -> Res.drawable.background_add
    AppImage.BackgroundLogo -> Res.drawable.background_logo
    AppImage.BackgroundMain -> Res.drawable.background_main
    AppImage.Cli -> Res.drawable.cli
    AppImage.Close -> Res.drawable.close
    AppImage.CloseBlue -> Res.drawable.close_blue
    AppImage.ComposeMultiplatform -> Res.drawable.compose_multiplatform
    AppImage.Devices -> Res.drawable.devices
    AppImage.DevicesLogo -> Res.drawable.devices_logo
    AppImage.EmailReceivedCheck -> Res.drawable.email_received_check
    AppImage.Executioner -> Res.drawable.executioner
    AppImage.Google -> Res.drawable.google
    AppImage.IconCopy -> Res.drawable.icon_copy
    AppImage.IconEmail -> Res.drawable.icon_email
    AppImage.IconEyeOff -> Res.drawable.icon_eye_off
    AppImage.IconLock -> Res.drawable.icon_lock
    AppImage.IconPaste -> Res.drawable.icon_paste
    AppImage.Laptop -> Res.drawable.laptop
    AppImage.Logo -> Res.drawable.logo
    AppImage.MetaSecretPicture -> Res.drawable.metasecretpicture
    AppImage.Other -> Res.drawable.other
    AppImage.ProfileLogo -> Res.drawable.profile_logo
    AppImage.SecretsLogo -> Res.drawable.secrets_logo
    AppImage.SettingsIcon -> Res.drawable.settings_icon
    AppImage.ShieldL1 -> Res.drawable.shield_l1
    AppImage.ShieldL2 -> Res.drawable.shield_l2
    AppImage.ShieldL3 -> Res.drawable.shield_l3
    AppImage.Tablet -> Res.drawable.tablet
    AppImage.Text -> Res.drawable.text
    AppImage.Trashbox -> Res.drawable.trashbox
    AppImage.Warning -> Res.drawable.warning
    AppImage.Web -> Res.drawable.web
}
