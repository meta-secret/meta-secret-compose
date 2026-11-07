package ui.liveboard

import core.DeviceInfoProviderInterface
import core.NotificationCoordinatorInterface
import core.metaSecretCore.MetaSecretSocketHandlerInterface
import core.metaSecretCore.liveboard.LiveboardSessionFactory
import core.metaSecretCore.liveboard.NativeCanonicalReadOwner
import core.metaSecretCore.liveboard.NativeLocalReset
import core.metaSecretCore.liveboard.NativeReadEnvironment

class LiveboardRootServices(
    val reset: NativeLocalReset,
    val socket: MetaSecretSocketHandlerInterface,
    val info: DeviceInfoProviderInterface,
    val notifications: NotificationCoordinatorInterface,
)

class LiveboardRootDependencies(
    val sessions: LiveboardSessionFactory,
    val reads: NativeCanonicalReadOwner,
    val environment: NativeReadEnvironment,
    val services: LiveboardRootServices,
    val platform: LiveboardPlatformPort,
)
