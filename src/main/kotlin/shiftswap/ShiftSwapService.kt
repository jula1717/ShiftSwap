package shiftswap

import kotlinx.coroutines.CancellationException
import kotlin.math.roundToLong

class ShiftSwapService(
    private val repository: ShiftSwapRepository,
    private val notifier: ShiftSwapNotifier,
    private val eventLogger: ShiftSwapEventLogger
) {

    suspend fun request(requesterId: Int, filedBy: Int, segments: List<ShiftSegment>): ShiftSwapRequest {
        val draft = ShiftSwapRequest(
            id = 0,
            status = ShiftSwapStatus.REQUESTED,
            requesterId = requesterId,
            filedBy = filedBy,
            segments = segments
        )
        return repositoryCall { repository.insert(draft) }
    }

    suspend fun approve(requestId: Int, approverId: Int): ShiftSwapRequest {
        val existing =
            requirePendingRequest(requestId = requestId, decision = ShiftSwapDecision.APPROVE)

        val updated = existing.copy(
            status = ShiftSwapStatus.APPROVED,
            approverId = approverId,
            payAdjustmentCents = computePayAdjustmentCents(existing.segments)
        )
        val saved = repositoryCall { repository.update(updated) }

        notifySafely(ShiftSwapEvent.SwapApproved(saved.id, saved.requesterId))
        notifySafely(ShiftSwapEvent.SwapApprovedForPayroll(saved.id, approverId))

        return saved
    }

    private suspend fun requirePendingRequest(
        requestId: Int,
        decision: ShiftSwapDecision
    ): ShiftSwapRequest {
        val existing = repositoryCall {
            repository.find(requestId)
        } ?: throw ShiftSwapError.NotFound(requestId)
        if (existing.status != ShiftSwapStatus.REQUESTED) {
            throw ShiftSwapError.InvalidState(existing.status, decision)
        }
        return existing
    }

    suspend fun deny(requestId: Int, deniedBy: Int): ShiftSwapRequest {
        val existing = requirePendingRequest(requestId = requestId, decision = ShiftSwapDecision.DENY)

        val updated = existing.copy(
            status = ShiftSwapStatus.DENIED,
            deniedBy = deniedBy,
        )
        val saved = repositoryCall { repository.update(updated) }

        notifySafely(ShiftSwapEvent.SwapDenied(saved.id, saved.requesterId))

        return saved
    }

    private suspend fun <T> repositoryCall(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ShiftSwapError.Underlying(e.toString())
        }
    }

    private suspend fun notifySafely(event: ShiftSwapEvent) {
        try {
            notifier.notify(event)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            eventLogger.log("Failed to notify for event $event: ${e.message}")
        }
    }

    private fun computePayAdjustmentCents(segments: List<ShiftSegment>): Int {
        val totalCents = segments.sumOf { (it.hours * it.hourlyRateInDollars * 100.0).roundToLong() }
        return Math.toIntExact(totalCents)
    }
}
