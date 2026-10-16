package com.crsmthw.sheliak.data.provider.plex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The account-token retention rule (M1 fix round 1, D2 — Cris: "add the server again (PIN skipped)"): removing a
 * Plex server, even the last one, deletes only that server's secrets; "Sign out from Plex" deletes only the
 * account's; neither touches the install's client identifier. Run over the real key names.
 */
class PlexAccountTest {

    private val machineA = "0123abcd0123abcd0123abcd0123abcd0123abcd"
    private val machineB = "fedcba98fedcba98fedcba98fedcba98fedcba98"

    /** What a phone with two Plex servers, an account and an M1-era server list holds. */
    private val stored = setOf(
        PlexKeys.CLIENT_ID,
        PlexKeys.ACCOUNT_TOKEN,
        PlexKeys.ACCOUNT_SERVERS,
        PlexKeys.serverToken(machineA),
        PlexKeys.serverToken(machineB),
    )

    @Test
    fun `removing a server deletes only its own keys`() {
        val removed = PlexCredentialRules.removed(stored, PlexCredentialRules.onServerRemoved(machineA))
        assertEquals(setOf(PlexKeys.serverToken(machineA)), removed)
    }

    @Test
    fun `removing the LAST server keeps the account token, so adding it again skips the sign-in`() {
        val onlyOne = setOf(PlexKeys.CLIENT_ID, PlexKeys.ACCOUNT_TOKEN, PlexKeys.serverToken(machineA))
        val left = onlyOne - PlexCredentialRules.removed(onlyOne, PlexCredentialRules.onServerRemoved(machineA))
        assertEquals(setOf(PlexKeys.CLIENT_ID, PlexKeys.ACCOUNT_TOKEN), left)
    }

    @Test
    fun `forgetting the account deletes the account's keys only`() {
        val removed = PlexCredentialRules.removed(stored, PlexCredentialRules.onForgetAccount())
        assertEquals(setOf(PlexKeys.ACCOUNT_TOKEN, PlexKeys.ACCOUNT_SERVERS), removed)
    }

    @Test
    fun `the client identifier survives everything`() {
        val everything = PlexCredentialRules.onForgetAccount() +
            PlexCredentialRules.onServerRemoved(machineA) +
            PlexCredentialRules.onServerRemoved(machineB)
        assertTrue(PlexKeys.CLIENT_ID !in PlexCredentialRules.removed(stored, everything))
    }

    @Test
    fun `a server id that would alias the account or the client namespace deletes nothing`() {
        listOf("account", "client", "", " ").forEach { id ->
            assertEquals(emptyList(), PlexCredentialRules.onServerRemoved(id), "'$id'")
        }
    }

    @Test
    fun `every account key lives under the account prefix`() {
        assertTrue(PlexKeys.ACCOUNT_TOKEN.startsWith(PlexKeys.ACCOUNT_PREFIX))
        assertTrue(PlexKeys.ACCOUNT_SERVERS.startsWith(PlexKeys.ACCOUNT_PREFIX))
        assertTrue(!PlexKeys.CLIENT_ID.startsWith(PlexKeys.ACCOUNT_PREFIX))
        assertTrue(!PlexKeys.serverToken(machineA).startsWith(PlexKeys.ACCOUNT_PREFIX))
    }
}
