package fr.speedvision.domain

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleTrackerTest {
    private fun car(
        x: Float,
        cls: Int = 2,
    ) = Detection(PixelBox(x, 10f, x + 40, 40f), .9f, cls)

    private fun result(vararg cars: Detection) = DetectionResult(cars.toList(), emptyList(), 0.0, 0.0, 0.0, 0, 0)

    private fun VehicleTracker.step(
        t: Long,
        vararg cars: Detection,
    ) = update(t, 640, 480, result(*cars))

    @Test fun confirmsOnlyConsecutiveObservations() {
        val tracker = VehicleTracker()
        val first = tracker.step(0, car(0f)).tracks.single()
        assertEquals(TrackStatus.TENTATIVE, first.status)
        assertTrue(tracker.step(100_000).tracks.isEmpty())
        val replacement = tracker.step(200_000, car(0f)).tracks.single()
        assertNotEquals(first.id, replacement.id)
        tracker.step(300_000, car(2f))
        assertEquals(
            TrackStatus.CONFIRMED,
            tracker
                .step(400_000, car(4f))
                .tracks
                .single()
                .status,
        )
    }

    @Test fun crossingAndReorderedDetectionsKeepMotionIdentities() {
        val tracker = VehicleTracker()
        var rightId = 0L
        var leftId = 0L
        for (i in 0..12) {
            val right = car(i * 6f)
            val left = car(78f - i * 6f)
            val frame = if (i % 2 == 0) tracker.step(i * 100_000L, right, left) else tracker.step(i * 100_000L, left, right)
            if (i == 0) {
                rightId = frame.tracks[0].id
                leftId = frame.tracks[1].id
            }
            assertEquals(if (i % 2 == 0) 0 else 1, frame.tracks.single { it.id == rightId }.detectionIndex)
            assertEquals(if (i % 2 == 0) 1 else 0, frame.tracks.single { it.id == leftId }.detectionIndex)
        }
    }

    @Test fun occlusionPredictsButDoesNotInventObservationAndExpires() {
        val tracker = VehicleTracker(minHits = 1)
        val id =
            tracker
                .step(0, car(0f))
                .tracks
                .single()
                .id
        tracker.step(100_000, car(5f))
        val lost = tracker.step(200_000).tracks.single()
        assertEquals(TrackStatus.LOST, lost.status)
        assertNull(lost.detectionIndex)
        assertEquals(100_000L, lost.lastObservedUs)
        assertTrue(lost.box.left > 5f)
        assertEquals(
            id,
            tracker
                .step(300_000, car(15f))
                .tracks
                .single()
                .id,
        )
        assertTrue(tracker.step(800_001).tracks.isEmpty())
        assertNotEquals(
            id,
            tracker
                .step(900_000, car(15f))
                .tracks
                .single()
                .id,
        )
    }

    @Test fun classGateDoesNotStealAnotherVehicle() {
        val tracker = VehicleTracker(minHits = 1)
        val id =
            tracker
                .step(0, car(0f))
                .tracks
                .single()
                .id
        val tracks = tracker.step(100_000, car(0f, 7)).tracks
        assertEquals(TrackStatus.LOST, tracks.single { it.id == id }.status)
        assertNotEquals(id, tracks.single { it.detectionIndex == 0 }.id)
    }

    @Test fun invalidTimeGeometryAndExplicitResetNeverReuseIdentity() {
        val tracker = VehicleTracker(minHits = 1)
        val ids = mutableSetOf<Long>()
        ids.add(
            tracker
                .step(100_000, car(0f))
                .tracks
                .single()
                .id,
        )
        ids.add(
            tracker
                .step(100_000, car(0f))
                .tracks
                .single()
                .id,
        )
        ids.add(
            tracker
                .step(50_000, car(0f))
                .tracks
                .single()
                .id,
        )
        ids.add(
            tracker
                .update(150_000, 480, 640, result(car(0f)))
                .tracks
                .single()
                .id,
        )
        tracker.reset()
        ids.add(
            tracker
                .step(0, car(0f))
                .tracks
                .single()
                .id,
        )
        assertEquals(5, ids.size)
    }

    @Test fun platesFollowCurrentParentAndNeverPersistAcrossLoss() {
        val tracker = VehicleTracker(minHits = 1)
        val cars = result(car(0f), car(100f))
        val frame = cars.copy(plates = listOf(PlateDetection(car(105f), 1)))
        val tracked = tracker.update(0, 640, 480, frame)
        assertEquals(tracked.tracks[1].id, tracked.plates.single().trackId)
        assertTrue(tracker.step(100_000).plates.isEmpty())
        assertFailsWith<IllegalArgumentException> {
            tracker.update(
                200_000,
                640,
                480,
                cars.copy(plates = listOf(PlateDetection(car(0f), 2))),
            )
        }
    }

    @Test fun assignmentIsGlobalRatherThanGreedy() {
        val assigned = minimumAssignment(arrayOf(doubleArrayOf(.1, .2, 1.0, 1.0), doubleArrayOf(.11, .9, 1.0, 1.0)))
        assertContentEquals(intArrayOf(1, 0), assigned)
        assertContentEquals(intArrayOf(1, 2), minimumAssignment(arrayOf(doubleArrayOf(1e6, 1.0, 1.0), doubleArrayOf(1e6, 1.0, 1.0))))
    }

    @Test fun irregularTimestampsUseElapsedSourceTime() {
        val tracker = VehicleTracker(minHits = 1)
        val id =
            tracker
                .step(0, car(0f))
                .tracks
                .single()
                .id
        tracker.step(100_000, car(5f))
        tracker.step(250_000, car(12.5f))
        val next = tracker.step(550_000, car(27.5f)).tracks.single()
        assertEquals(id, next.id)
        assertEquals(27.5f, next.box.left, 2f)
    }

    @Test fun boundedStateEvictsLostTracksBeforeDroppingNewVehicles() {
        val tracker = VehicleTracker(minHits = 1, maxTracks = 2)
        val old =
            tracker
                .step(0, car(0f), car(100f))
                .tracks
                .map { it.id }
                .toSet()
        val next = tracker.step(100_000, car(200f), car(300f)).tracks
        assertEquals(2, next.size)
        assertTrue(next.all { it.id !in old && it.detectionIndex != null })
    }

    @Test fun tentativePlateIsNotAcceptedAndTrackStatesAreIndependent() {
        val tracker = VehicleTracker()
        val detections = result(car(0f), car(100f)).copy(plates = listOf(PlateDetection(car(0f), 0)))
        assertTrue(tracker.update(0, 640, 480, detections).plates.isEmpty())
        tracker.step(100_000, car(5f), car(100f))
        tracker.step(200_000, car(10f), car(100f))
        val tracks = tracker.step(300_000).tracks
        assertTrue(tracks[0].box.left > 10f)
        assertEquals(100f, tracks[1].box.left, .01f)
    }

    @Test fun hungarianMatchesBruteForceOnSmallRectangularProblems() {
        val random = kotlin.random.Random(42)
        repeat(100) {
            val costs = Array(3) { DoubleArray(5) { random.nextDouble() } }
            var best = Double.POSITIVE_INFINITY
            for (a in 0..4) {
                for (b in 0..4) {
                    for (c in 0..4) {
                        if (a != b && a != c && b != c) best = minOf(best, costs[0][a] + costs[1][b] + costs[2][c])
                    }
                }
            }
            val assignment = minimumAssignment(costs)
            assertEquals(best, assignment.indices.sumOf { costs[it][assignment[it]] }, 1e-10)
        }
    }
}
