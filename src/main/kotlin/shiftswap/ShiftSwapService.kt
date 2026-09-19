package shiftswap

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
        return repository.insert(draft)
    }

    suspend fun approve(requestId: Int, approverId: Int): ShiftSwapRequest {
        val existing = repository.find(requestId) ?: throw ShiftSwapError.NotFound(requestId)
        if (existing.status != ShiftSwapStatus.REQUESTED) {
            throw ShiftSwapError.InvalidState(existing.status, "approve")
        }

        val updated = existing.copy(
            status = ShiftSwapStatus.APPROVED,
            approverId = approverId,
            payAdjustmentCents = computePayAdjustmentCents(existing.segments)
        )
        val saved = repository.update(updated)

        notifier.notify(ShiftSwapEvent.SwapApproved(saved.id, saved.requesterId))
        notifier.notify(ShiftSwapEvent.SwapApprovedForPayroll(saved.id, approverId))

        return saved
    }

    suspend fun deny(requestId: Int, deniedBy: Int): ShiftSwapRequest {
        TODO("DenyShiftSwap is not implemented yet - this is your task")
    }

    private fun computePayAdjustmentCents(segments: List<ShiftSegment>): Int {
        val totalDollars = segments.sumOf { it.hours * it.hourlyRateInDollars }
        return (totalDollars * 100).toInt()
    }
}
