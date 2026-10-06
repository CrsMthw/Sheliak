package com.crsmthw.sheliak.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionAccessTest {

    private val own = "com.crsmthw.sheliak"

    private fun decide(
        packageName: String,
        trusted: Boolean = false,
        verified: Boolean = true,
        notification: Boolean = false,
    ) = SessionAccessPolicy.decide(
        packageName            = packageName,
        ownPackage             = own,
        trusted                = trusted,
        packageNameVerified    = verified,
        notificationController = notification,
    )

    @Test
    fun `Sheliak's own controller gets its own access, the notification controller the default one`() {
        assertEquals(SessionAccess.OWN_APP, decide(own, trusted = true))
        assertEquals(SessionAccess.OWN_APP, decide(own, trusted = false))
        assertEquals(SessionAccess.ACCEPT, decide(own, trusted = true, notification = true))
    }

    @Test
    fun `a trusted controller is accepted whatever its package`() {
        assertEquals(SessionAccess.ACCEPT, decide("com.example.notificationlistener", trusted = true))
        assertEquals(SessionAccess.ACCEPT, decide("com.example.unverified", trusted = true, verified = false))
    }

    @Test
    fun `every allowlisted package is accepted when verified`() {
        val expected = setOf(
            "com.google.android.projection.gearhead",
            "com.android.car.media",
            "com.android.car.carlauncher",
            "com.google.android.googlequicksearchbox",
            "com.android.bluetooth",
            "com.android.systemui",
            "com.google.android.wearable.app",
        )
        assertEquals(expected, SessionAccessPolicy.ALLOWED_PACKAGES)
        expected.forEach { assertEquals(SessionAccess.ACCEPT, decide(it), it) }
    }

    @Test
    fun `an unverified package name is only a claim`() {
        assertEquals(SessionAccess.REJECT, decide("com.google.android.projection.gearhead", verified = false))
        assertEquals(SessionAccess.REJECT, decide(own, verified = false))
    }

    @Test
    fun `any other app is rejected`() {
        assertEquals(SessionAccess.REJECT, decide("com.example.reader"))
        assertEquals(SessionAccess.REJECT, decide("com.android.systemui.evil"))
        assertEquals(SessionAccess.REJECT, decide(""))
    }

    @Test
    fun `the allowlist can be extended`() {
        val access = SessionAccessPolicy.decide(
            packageName            = "com.example.watch",
            ownPackage             = own,
            trusted                = false,
            packageNameVerified    = true,
            notificationController = false,
            allowed                = SessionAccessPolicy.ALLOWED_PACKAGES + "com.example.watch",
        )
        assertEquals(SessionAccess.ACCEPT, access)
        assertTrue("com.example.watch" !in SessionAccessPolicy.ALLOWED_PACKAGES)
    }
}
