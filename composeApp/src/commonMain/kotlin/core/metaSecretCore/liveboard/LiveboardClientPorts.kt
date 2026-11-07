package core.metaSecretCore.liveboard

import core.BiometricAuthenticatorInterface
import core.NotificationCoordinatorInterface
import core.StringProviderInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface

/** Required ports of the same keyed client composition; none creates native authority. */
class LiveboardClientPorts(
    val privacy: LiveboardPrivacyOwner,
    val auth: BiometricAuthenticatorInterface,
    val socket: MetaSecretSocketHandlerInterface,
    val notifications: NotificationCoordinatorInterface,
    val strings: StringProviderInterface,
)
