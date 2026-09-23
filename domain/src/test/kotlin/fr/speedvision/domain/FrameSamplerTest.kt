package fr.speedvision.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrameSamplerTest {
    @Test fun irregularTimestampsRetainSourceTime() {
        val sampler = FrameSampler()
        val times = listOf(0L, 33_000L, 70_000L, 101_000L, 140_000L, 220_000L)
        assertEquals(listOf(0L, 101_000L, 220_000L), times.filter(sampler::accept))
    }

    @Test fun duplicateNegativeAndBackwardTimestampsAreRejected() {
        val sampler = FrameSampler()
        assertFalse(sampler.accept(-1))
        assertTrue(sampler.accept(200_000))
        assertFalse(sampler.accept(200_000))
        assertFalse(sampler.accept(0))
        assertTrue(sampler.accept(300_000))
    }

    @Test fun newSessionCanRestartAtZero() {
        assertTrue(FrameSampler().accept(1_000_000))
        assertTrue(FrameSampler().accept(0))
    }

    @Test fun invalidIntervalIsRejected() {
        assertFailsWith<IllegalArgumentException> { FrameSampler(0) }
    }
}
