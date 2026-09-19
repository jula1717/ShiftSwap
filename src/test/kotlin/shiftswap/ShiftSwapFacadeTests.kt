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
    fun approvingMissingRequest_returnsNotFoundFailure() = runTest {
        val facade = makeFacade(InMemoryShiftSwapRepository())

        val result = facade.approve(999, approverId = 1)

        val error = result.exceptionOrNull()
        if (error !is ShiftSwapFacadeError.NotFound || error.id != 999) {
            fail("expected NotFound(999) failure for a missing request, got $error")
        }
    }

    @Test
    fun approvingAnAlreadyApprovedRequest_returnsInvalidStateFailure() = runTest {
        val repository = InMemoryShiftSwapRepository()
        val facade = makeFacade(repository)

        val requested = facade.request(
            RequestShiftSwapPayload(
                requesterId = 1,
                filedBy = 1,
                segments = emptyList()
            )
        )
        facade.approve(requestId = requested.id, approverId = 1)

        val result = facade.approve(requestId = requested.id, approverId = 1)

        val error = result.exceptionOrNull()
        if (error !is ShiftSwapFacadeError.InvalidState) {
            fail("expected InvalidState failure for a re-approval attempt, got $error")
        }
    }

    @Test
    fun approvingWhenRepositoryFails_returnsUnderlyingFailure() = runTest {
        val repository = FailingShiftSwapRepository(InMemoryShiftSwapRepository())
        val facade = makeFacade(repository)

        val requested = facade.request(
            RequestShiftSwapPayload(
                requesterId = 1,
                filedBy = 1,
                segments = emptyList()
            )
        )

        val result = facade.approve(requestId = requested.id, approverId = 1)

        val error = result.exceptionOrNull()
        if (error !is ShiftSwapFacadeError.Underlying) {
            fail("expected Underlying failure for a repository error, got $error")
        }
    }

    @Test
    fun requestingOnBehalfOfSomeoneElse_keepsRequesterAndFilerDistinct() = runTest {
        val repository = InMemoryShiftSwapRepository()
        val facade = makeFacade(repository)

        val requested = facade.request(
            RequestShiftSwapPayload(
                requesterId = 1,
                filedBy = 2,
                segments = emptyList()
            )
        )

        assertEquals(
            1,
            requested.requesterId,
            "the employee who owns the shift must stay the requester"
        )
        assertEquals(
            2,
            requested.filedBy,
            "the person who filed the request in the app must stay filedBy"
        )
    }
}
