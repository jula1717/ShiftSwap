package shiftswap

interface ShiftSwapRepository {
    suspend fun insert(request: ShiftSwapRequest): ShiftSwapRequest
    suspend fun find(id: Int): ShiftSwapRequest?
    suspend fun update(request: ShiftSwapRequest): ShiftSwapRequest
}

interface ShiftSwapNotifier {
    suspend fun notify(event: ShiftSwapEvent)
}

sealed interface ShiftSwapEvent {
    data class SwapApproved(val requestId: Int, val requesterId: Int) : ShiftSwapEvent
    data class SwapApprovedForPayroll(val requestId: Int, val approverId: Int) : ShiftSwapEvent
    data class SwapDenied(val requestId: Int, val requesterId: Int) : ShiftSwapEvent
}

interface ShiftSwapEventLogger {
    suspend fun log(message: String)
}

class ConsoleShiftSwapEventLogger : ShiftSwapEventLogger {
    override suspend fun log(message: String) {
        println("[ShiftSwap] $message")
    }
}
