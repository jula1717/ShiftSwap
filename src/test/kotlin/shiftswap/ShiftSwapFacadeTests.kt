package shiftswap

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class ShiftSwapFacadeTests {

    @Test
    fun requestingAndApprovingThroughTheFacade_returnsApprovedRequest() = runTest {
        val repository = InMemoryShiftSwapRepository()
        val facade = makeFacade(repository)

        val requested = facade.request(
            RequestShiftSwapPayload(
                requesterId = 10,
                filedBy = 10,
                segments = listOf(ShiftSegment("Holiday cover", hours = 6.0, hourlyRateInDollars = 50.0))
            )
        )

        val result = facade.approve(requested.id, approverId = 20)

        assertTrue(result.isSuccess)
        val approved = result.getOrNull()!!
        assertEquals(ShiftSwapStatus.APPROVED, approved.status)
        assertEquals(30_000, approved.payAdjustmentCents)
    }

    @Test
    fun approvingMissingRequest_currentlyReturnsGenericFailure() = runTest {
        val facade = makeFacade(InMemoryShiftSwapRepository())

        val result = facade.approve(999, approverId = 1)

        val error = result.exceptionOrNull()
        if (error !is ShiftSwapFacadeError.Failed) {
            fail<Unit>("expected today's generic Failed(...) for a missing request")
        }
    }
}
