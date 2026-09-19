package shiftswap

class ShiftSwapFacade(
    private val repository: ShiftSwapRepository,
    notifier: ShiftSwapNotifier,
    eventLogger: ShiftSwapEventLogger
) {
    private val service = ShiftSwapService(repository, notifier, eventLogger)

    suspend fun request(payload: RequestShiftSwapPayload): ShiftSwapDto {
        val ownership = resolveSwapOwnership(payload)
        val request = service.request(ownership.requesterId, ownership.filedBy, payload.segments)
        return ShiftSwapDto(request)
    }

    suspend fun approve(requestId: Int, approverId: Int): Result<ShiftSwapDto> {
        return try {
            Result.success(ShiftSwapDto(service.approve(requestId, approverId)))
        } catch (error: Exception) {
            Result.failure(ShiftSwapFacadeError.Failed(error.toString()))
        }
    }

    suspend fun find(requestId: Int): ShiftSwapDto? = repository.find(requestId)?.let(::ShiftSwapDto)

    private fun resolveSwapOwnership(payload: RequestShiftSwapPayload): Ownership =
        Ownership(requesterId = payload.filedBy, filedBy = payload.requesterId)

    private data class Ownership(val requesterId: Int, val filedBy: Int)
}
