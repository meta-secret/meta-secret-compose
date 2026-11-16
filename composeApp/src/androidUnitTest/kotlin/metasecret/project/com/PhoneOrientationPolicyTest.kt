package metasecret.project.com

import android.content.pm.ActivityInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PhoneOrientationPolicyTest {
    @Test fun testApplyPhonePortraitAndRepeatedConfigurationIsNoOp() {
        val policy = PhoneOrientationPolicy()
        val requests = mutableListOf<Int>()
        policy.apply(393, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requests::add)
        policy.apply(393, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requests::add)
        assertEquals(listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT), requests)
    }

    @Test fun testApplySmallestWidthBoundaryUsesPhoneBelow600Only() {
        val phone = mutableListOf<Int>()
        val tablet = mutableListOf<Int>()
        PhoneOrientationPolicy().apply(599, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, phone::add)
        PhoneOrientationPolicy().apply(600, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, tablet::add)
        assertEquals(listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT), phone)
        assertTrue(tablet.isEmpty())
    }

    @Test fun testApplyTabletAndUndefinedWidthLeaveCurrentRequestUntouched() {
        val requests = mutableListOf<Int>()
        val policy = PhoneOrientationPolicy()
        policy.apply(800, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, requests::add)
        policy.apply(0, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requests::add)
        policy.apply(-1, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requests::add)
        assertTrue(requests.isEmpty())
    }

    @Test fun testApplyDoesNotClaimPreexistingPortrait() {
        val requests = mutableListOf<Int>()
        val policy = PhoneOrientationPolicy()
        policy.apply(393, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requests::add)
        policy.apply(800, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requests::add)
        assertTrue(requests.isEmpty())
    }

    @Test fun testApplyReleasesOnlyOwnedPortraitOnceOnLargeScreen() {
        val requests = mutableListOf<Int>()
        val policy = PhoneOrientationPolicy()
        policy.apply(393, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requests::add)
        policy.apply(800, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requests::add)
        policy.apply(800, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requests::add)
        assertEquals(listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED), requests)
    }

    @Test fun testApplyForeignReplacementIsNeverClearedOrAdopted() {
        val requests = mutableListOf<Int>()
        val policy = PhoneOrientationPolicy()
        policy.apply(393, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requests::add)
        policy.apply(800, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, requests::add)
        policy.apply(800, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requests::add)
        assertEquals(listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT), requests)
    }

    @Test fun testApplyFailedAcquisitionCreatesNoReleaseAuthority() {
        val policy = PhoneOrientationPolicy()
        assertFailsWith<IllegalStateException> {
            policy.apply(393, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) { error("Native setter unavailable") }
        }
        val requests = mutableListOf<Int>()
        policy.apply(800, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requests::add)
        assertTrue(requests.isEmpty())
    }

    @Test fun testApplyFailedReleasePreservesAuthorityAndFailure() {
        val policy = PhoneOrientationPolicy()
        val requests = mutableListOf<Int>()
        policy.apply(393, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, requests::add)
        assertFailsWith<IllegalStateException> {
            policy.apply(800, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) { error("Native release unavailable") }
        }
        policy.apply(800, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, requests::add)
        assertEquals(listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED), requests)
    }
}
