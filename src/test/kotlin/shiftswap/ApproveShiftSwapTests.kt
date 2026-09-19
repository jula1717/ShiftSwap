package shiftswap

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ApproveShiftSwapTests {

    @Test
    fun approvingARequestedSwap_marksItApprovedWithPayAdjustment() = runTest {
        val repository = InMemoryShiftSwapRepository()
        val notifier = RecordingShiftSwapNotifier()
        val logger = RecordingShiftSwapEventLogger()
        val service = ShiftSwapService(repository, notifier, logger)

        val requested = service.request(
            requesterId = 1,
            filedBy = 1,
            segments = listOf(
                ShiftSegment("Saturday morning", hours = 4.0, hourlyRateInDollars = 30.0),
                ShiftSegment("Saturday afternoon", hours = 3.0, hourlyRateInDollars = 15.0)
            )
        )

        val approved = service.approve(requested.id, approverId = 42)

        assertEquals(ShiftSwapStatus.APPROVED, approved.status)
        assertEquals(42, approved.approverId)
        assertEquals(16_500, approved.payAdjustmentCents)
        assertEquals(2, notifier.events.size)
    }

    @Test
    fun approvalSucceeds_evenWhenNotificationChannelFails() = runTest {
        val repository = InMemoryShiftSwapRepository()
        val notifier = ThrowingShiftSwapNotifier()
        val logger = RecordingShiftSwapEventLogger()
        val service = ShiftSwapService(repository, notifier, logger)

        val requested = service.request(
            requesterId = 7,
            filedBy = 7,
            segments = listOf(ShiftSegment("Cover shift", hours = 8.0, hourlyRateInDollars = 20.0))
        )

        val approved = service.approve(requested.id, approverId = 99)

        assertEquals(ShiftSwapStatus.APPROVED, approved.status)

        val persisted = repository.find(requested.id)
        assertEquals(ShiftSwapStatus.APPROVED, persisted?.status)

        assertFalse(logger.messages.isEmpty(), "a failed notification must be logged, not silently discarded")
    }

    @Test
    fun payAdjustment_accountsForManySmallSegmentsWithoutDrift() = runTest {
        val repository = InMemoryShiftSwapRepository()
        val notifier = RecordingShiftSwapNotifier()
        val logger = RecordingShiftSwapEventLogger()
        val service = ShiftSwapService(repository, notifier, logger)

        val tenDimeSegments = List(10) { ShiftSegment("Minute of overtime", hours = 0.1, hourlyRateInDollars = 1.0) }
        val requested = service.request(requesterId = 2, filedBy = 2, segments = tenDimeSegments)

        val approved = service.approve(requested.id, approverId = 5)

        assertEquals(
            100,
            approved.payAdjustmentCents,
            "ten 0.1h segments at \$1/h must adjust pay by exactly \$1.00, not \$0.99"
        )
    }
}
