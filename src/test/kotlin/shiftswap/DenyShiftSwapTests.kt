package shiftswap

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class DenyShiftSwapTests {

    @Test
    fun denyingARequestedSwap_marksItDenied() = runTest {
        val repository = InMemoryShiftSwapRepository()
        val notifier = RecordingShiftSwapNotifier()
        val logger = RecordingShiftSwapEventLogger()
        val service = ShiftSwapService(repository, notifier, logger)

        val requested = service.request(
            requesterId = 3,
            filedBy = 3,
            segments = listOf(ShiftSegment("Late shift", hours = 5.0, hourlyRateInDollars = 18.0))
        )

        val denied = service.deny(requested.id, deniedBy = 9)

        assertEquals(ShiftSwapStatus.DENIED, denied.status)

        val persisted = repository.find(requested.id)
        assertEquals(ShiftSwapStatus.DENIED, persisted?.status)
    }

    @Test
    fun denyingAnAlreadyApprovedSwap_isRejectedAsInvalidState() = runTest {
        val repository = InMemoryShiftSwapRepository()
        val notifier = RecordingShiftSwapNotifier()
        val logger = RecordingShiftSwapEventLogger()
        val service = ShiftSwapService(repository, notifier, logger)

        val requested = service.request(requesterId = 4, filedBy = 4, segments = emptyList())
        service.approve(requested.id, approverId = 1)

        try {
            service.deny(requested.id, deniedBy = 1)
            fail<Unit>("expected an InvalidState error")
        } catch (error: ShiftSwapError.InvalidState) {

        }
    }
}
