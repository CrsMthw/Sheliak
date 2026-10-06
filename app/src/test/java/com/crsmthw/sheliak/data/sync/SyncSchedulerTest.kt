package com.crsmthw.sheliak.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SyncSchedulerTest {

    @Test
    fun `each source has its own periodic and one-time work names`() {
        val names = listOf("plex:a", "plex:b").flatMap {
            listOf(SyncScheduler.periodicWorkName(it), SyncScheduler.oneTimeWorkName(it))
        }
        assertEquals(names.size, names.toSet().size)
        assertNotEquals(SyncScheduler.periodicWorkName("plex:a"), SyncScheduler.oneTimeWorkName("plex:a"))
    }

    @Test
    fun `the periodic sync runs every six hours`() {
        assertEquals(6, SyncScheduler.PERIODIC_HOURS)
    }
}
