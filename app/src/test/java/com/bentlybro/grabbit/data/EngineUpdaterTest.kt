package com.bentlybro.grabbit.data

import com.bentlybro.grabbit.engine.EngineUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineUpdaterTest {
    private val day = EngineUpdater.MAX_AGE_MS
    private val now = 10 * day

    @Test fun dueOnFirstRunAndWhenStale() {
        assertTrue(EngineUpdater.isDue(now, 0, EngineChannel.NIGHTLY, null))
        assertTrue(EngineUpdater.isDue(now, now - day, EngineChannel.NIGHTLY, EngineChannel.NIGHTLY))
        assertFalse(EngineUpdater.isDue(now, now - day / 2, EngineChannel.NIGHTLY, EngineChannel.NIGHTLY))
    }

    @Test fun dueWhenChannelChangesOrClockGoesBack() {
        assertTrue(EngineUpdater.isDue(now, now - 1000, EngineChannel.STABLE, EngineChannel.NIGHTLY))
        assertTrue(EngineUpdater.isDue(now, now + 1000, EngineChannel.NIGHTLY, EngineChannel.NIGHTLY))
    }

    @Test fun nightlyIsTheDefault() {
        assertEquals(EngineChannel.NIGHTLY, EngineChannel.DEFAULT)
        assertEquals(EngineChannel.STABLE, EngineChannel.fromName("STABLE"))
        assertEquals(EngineChannel.NIGHTLY, EngineChannel.fromName("bogus"))
    }
}
