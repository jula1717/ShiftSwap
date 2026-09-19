package shiftswap

import kotlinx.coroutines.CancellationException

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

    suspend fun approve(requestId: Int, approverId: Int) = toFacadeResult {
        service.approve(requestId, approverId)
    }

    suspend fun find(requestId: Int): ShiftSwapDto? = repository.find(requestId)?.let(::ShiftSwapDto)

    private fun resolveSwapOwnership(payload: RequestShiftSwapPayload): Ownership =
        Ownership(requesterId = payload.filedBy, filedBy = payload.requesterId)

    private data class Ownership(val requesterId: Int, val filedBy: Int)

    private suspend fun toFacadeResult(
        block: suspend () -> ShiftSwapRequest
    ): Result<ShiftSwapDto> =
        try {
            Result.success(ShiftSwapDto(request = block()))
        } catch (error: ShiftSwapError.NotFound) {
            Result.failure(ShiftSwapFacadeError.NotFound(id = error.id))
        } catch (error: ShiftSwapError.InvalidState) {
            Result.failure(
                ShiftSwapFacadeError.InvalidState(
                    current = error.current,
                    attempted = error.attempted
                )
            )
        } catch (error: ShiftSwapError.Underlying) {
            Result.failure(ShiftSwapFacadeError.Underlying(detail = error.detail))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(ShiftSwapFacadeError.Failed(detail = e.toString()))
        }
}
