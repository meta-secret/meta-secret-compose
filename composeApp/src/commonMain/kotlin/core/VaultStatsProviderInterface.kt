package core

import kotlinx.coroutines.flow.StateFlow

interface VaultStatsProviderInterface {
	val secretsCount: StateFlow<Int>
	val devicesCount: StateFlow<Int>
	val vaultName: StateFlow<String?>
	suspend fun refresh()
}


