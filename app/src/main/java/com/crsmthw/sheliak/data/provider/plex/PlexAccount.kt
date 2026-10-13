package com.crsmthw.sheliak.data.provider.plex

/**
 * Which stored secrets a Plex change deletes — the account-token retention rule (Cris, M1 fix round 1: "add the
 * server again (PIN skipped)"). Pure: unit-tested in PlexAccountTest over the real key names.
 *
 * - Removing a server deletes ONLY that server's namespace (`plex:<machineId>:`), even when it was the last Plex
 *   server: the plex.tv account token is KEPT, so adding a server again skips the sign-in.
 * - "Forget Plex account" deletes ONLY the account's namespace ([PlexKeys.ACCOUNT_PREFIX]): the servers already
 *   added keep their own access tokens and keep playing; the next server added asks for a sign-in.
 * - Neither ever touches the install's client identifier ([PlexKeys.CLIENT_ID]).
 */
internal object PlexCredentialRules {

    /**
     * The prefixes `CredentialStore.removeAll` is given when server [machineId] is removed. Empty for an id whose
     * namespace would ALIAS the account's or the client identifier's (a blank id, or `account` / `client`): a
     * machine identifier is never one of those, and a removal must never sign the account out by accident.
     */
    fun onServerRemoved(machineId: String): List<String> {
        if (machineId.isBlank()) return emptyList()
        val prefix = PlexKeys.serverPrefix(machineId)
        val aliases = listOf(PlexKeys.ACCOUNT_TOKEN, PlexKeys.ACCOUNT_SERVERS, PlexKeys.CLIENT_ID)
        return if (aliases.any { it.startsWith(prefix) }) emptyList() else listOf(prefix)
    }

    /** The prefixes "Forget Plex account" deletes: the account's namespace only. */
    fun onForgetAccount(): List<String> = listOf(PlexKeys.ACCOUNT_PREFIX)

    /** The keys of [stored] that removing the [prefixes] deletes (what `removeAll` does, as a pure rule). */
    fun removed(stored: Set<String>, prefixes: List<String>): Set<String> =
        stored.filterTo(LinkedHashSet()) { key -> prefixes.any { key.startsWith(it) } }
}
