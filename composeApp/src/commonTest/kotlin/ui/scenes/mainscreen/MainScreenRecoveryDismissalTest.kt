package ui.scenes.mainscreen

import core.RecoveryRequestAlertState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import models.appInternalModels.RestoreData

class MainScreenRecoveryDismissalTest {
    private val oldRequest = RestoreData(claimId = "old-claim", secretId = "secret-1")
    private val currentRequest = RestoreData(claimId = "current-claim", secretId = "secret-1")

    @Test
    fun `completed old claim does not dismiss current recovery alert`() {
        val state = RecoveryRequestAlertState.Visible(currentRequest)

        assertFalse(shouldDismissRecoveryRequest(state, oldRequest.claimId))
    }

    @Test
    fun `completed current claim dismisses its visible or processing alert`() {
        assertTrue(
            shouldDismissRecoveryRequest(
                RecoveryRequestAlertState.Visible(currentRequest),
                currentRequest.claimId,
            )
        )
        assertTrue(
            shouldDismissRecoveryRequest(
                RecoveryRequestAlertState.Processing(currentRequest),
                currentRequest.claimId,
            )
        )
    }

    @Test
    fun `completed claim does not dismiss when no recovery alert is open`() {
        assertFalse(
            shouldDismissRecoveryRequest(
                RecoveryRequestAlertState.Hidden,
                currentRequest.claimId,
            )
        )
    }
}
