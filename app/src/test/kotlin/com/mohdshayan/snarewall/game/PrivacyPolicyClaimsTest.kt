package com.mohdshayan.snarewall.game

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The published privacy policy makes claims about this app. Each one is pinned here against the
 * thing it describes, so a source change cannot quietly turn the policy into a false statement.
 */
class PrivacyPolicyClaimsTest {

    private val policy = File("../docs/privacy-policy.html").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun theNoInternetPermissionClaimMatchesTheManifest() {
        assertTrue("the policy dropped its no-internet claim", "no internet permission" in policy)
        assertFalse("the manifest now declares INTERNET", "android.permission.INTERNET" in manifest)
    }

    @Test
    fun thePolicyDoesNotClaimTheAppRequestsNoPermissionAtAll() {
        // AndroidX core merges DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION into the built app, so the
        // flat claim the audit found ("requests no Android permissions") is untrue as written.
        assertFalse(
            "the policy claims no Android permissions again; the built app requests the AndroidX one",
            Regex("requests no Android permissions|asks for no Android permissions").containsMatchIn(policy),
        )
        assertTrue("the policy no longer explains the AndroidX permission", "AndroidX" in policy)
    }

    @Test
    fun thePolicyCarriesAPrivacyContactAndTheDeveloperName() {
        assertTrue("no mailto contact in the policy", "mailto:" in policy)
        assertTrue("the publishing entity is not named", "SocialSure Private Limited" in policy)
    }

    @Test
    fun thePolicyStillSaysAndroidBackupMayIncludeProgress() {
        assertTrue("the backup disclosure went missing", "backup" in policy.lowercase())
    }
}
