package com.crsmthw.sheliak.service

/**
 * Who may connect to the playback session (docs/PLAYER.md, "Session access"). Browse and queue items carry
 * provider art URLs with the server token in the query, so the exported session is not open to every app.
 * Pure; tested in SessionAccessTest. SheliakMediaLibraryCallback.onConnect turns the outcome into commands.
 */
enum class SessionAccess {
    /** Sheliak's own controller (PlayerStateManager): every command plus `MOVE_IN_PLAY_ORDER`. */
    OWN_APP,

    /** A trusted or allowlisted controller (and the media notification controller): every default command. */
    ACCEPT,

    /** Anyone else: the connection is refused. */
    REJECT,
}

object SessionAccessPolicy {

    /**
     * Packages accepted even when Media3 does not call them trusted. To add one: its package name here, one line
     * with what it is, a case in SessionAccessTest, and the table in docs/PLAYER.md. A rejected controller is
     * logged as "Rejected media controller <package>" (tag SheliakSession) — that line names what to add.
     */
    val ALLOWED_PACKAGES: Set<String> = setOf(
        "com.google.android.projection.gearhead",  // Android Auto
        "com.android.car.media",                   // Android Automotive OS media centre
        "com.android.car.carlauncher",             // Android Automotive OS home screen media card
        "com.google.android.googlequicksearchbox", // Google app / Assistant
        "com.android.bluetooth",                   // Bluetooth (AVRCP browsing from a car or headset)
        "com.android.systemui",                    // System UI media controls
        "com.google.android.wearable.app",         // Wear OS companion
    )

    /**
     * The access for a connecting controller.
     *
     * - [trusted] is Media3's `ControllerInfo.isTrusted`: the system, Sheliak itself (same uid), System UI
     *   (STATUS_BAR_SERVICE), MEDIA_CONTENT_CONTROL holders and enabled notification listeners.
     * - A package name counts only when [packageNameVerified] (Media3 matched it to the caller's uid): an
     *   unverified name is whatever the caller claimed.
     * - [notificationController] is Media3's own media notification controller (Sheliak's package): it keeps
     *   the default commands, not Sheliak's custom one.
     */
    fun decide(
        packageName: String,
        ownPackage: String,
        trusted: Boolean,
        packageNameVerified: Boolean,
        notificationController: Boolean,
        allowed: Set<String> = ALLOWED_PACKAGES,
    ): SessionAccess {
        val own = packageNameVerified && packageName == ownPackage
        return when {
            own && !notificationController                -> SessionAccess.OWN_APP
            own || trusted                                -> SessionAccess.ACCEPT
            packageNameVerified && packageName in allowed -> SessionAccess.ACCEPT
            else                                          -> SessionAccess.REJECT
        }
    }
}
