package core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import models.appInternalModels.RestoreData
import testutils.FakeNotificationCoordinator
import testutils.FakeStringProvider

class AlertCoordinatorTest {

    private fun newCoordinator() = AlertCoordinator(
        notificationCoordinator = FakeNotificationCoordinator(),
        stringProvider = FakeStringProvider()
    )

    @Test
    fun testShowRecoveryRequestDoesNotReplaceVisibleUnresolvedClaim() {
        val coordinator = newCoordinator()

        coordinator.showRecoveryRequest(RestoreData(claimId = "claim1", secretId = "secret1"))
        coordinator.showRecoveryRequest(RestoreData(claimId = "claim2", secretId = "secret2"))

        val visible = coordinator.recoveryRequestAlert.value
        assertTrue(visible is RecoveryRequestAlertState.Visible)
        assertEquals("claim1", visible.restoreData.claimId)
    }

    @Test
    fun testShowRecoveryRequestDoesNotQueueDuplicateClaimId() {
        val coordinator = newCoordinator()

        coordinator.showRecoveryRequest(RestoreData(claimId = "claim1", secretId = "secret1"))
        coordinator.showRecoveryRequest(RestoreData(claimId = "claim1", secretId = "secret1"))
        coordinator.dismissRecoveryRequest()

        assertEquals(RecoveryRequestAlertState.Hidden, coordinator.recoveryRequestAlert.value)
    }

    @Test
    fun testDismissRecoveryRequestCallsDismissHandlerForVisibleClaim() {
        val coordinator = newCoordinator()
        var dismissedClaimId: String? = null
        coordinator.setRecoveryRequestDismissHandler { dismissedClaimId = it.claimId }

        coordinator.showRecoveryRequest(RestoreData(claimId = "claim1", secretId = "secret1"))
        coordinator.dismissRecoveryRequest()

        assertEquals("claim1", dismissedClaimId)
        assertEquals(RecoveryRequestAlertState.Hidden, coordinator.recoveryRequestAlert.value)
    }
}
