package ui.screenContent

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import models.appInternalModels.DeviceStatus

class DeviceContentCellTest {

    @Test
    fun pendingRemovalMemberUsesPlainClickableRow() {
        assertFalse(
            shouldEnableDeviceRemovalSwipe(
                modelId = "target",
                currentDeviceId = "current",
                effectiveStatus = DeviceStatus.Member,
                isRemovalPending = true,
            ),
        )
    }

    @Test
    fun ordinaryOtherMemberKeepsRemovalSwipe() {
        assertTrue(
            shouldEnableDeviceRemovalSwipe(
                modelId = "other",
                currentDeviceId = "current",
                effectiveStatus = DeviceStatus.Member,
                isRemovalPending = false,
            ),
        )
    }

    @Test
    fun currentDeviceNeverExposesRemovalSwipe() {
        assertFalse(
            shouldEnableDeviceRemovalSwipe(
                modelId = "current",
                currentDeviceId = "current",
                effectiveStatus = DeviceStatus.Current,
                isRemovalPending = false,
            ),
        )
    }
}
