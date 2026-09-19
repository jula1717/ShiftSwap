package shiftswap

sealed class ShiftSwapError : Exception() {
    data class NotFound(val id: Int) : ShiftSwapError()
    data class InvalidState(val current: ShiftSwapStatus, val attempted: ShiftSwapDecision) : ShiftSwapError()
    data class Underlying(val detail: String) : ShiftSwapError()
}

sealed class ShiftSwapFacadeError : Exception() {
    data class NotFound(val id: Int) : ShiftSwapFacadeError()
    data class InvalidState(val current: ShiftSwapStatus, val attempted: ShiftSwapDecision) : ShiftSwapFacadeError()
    data class Underlying(val detail: String) : ShiftSwapFacadeError()
    data class Failed(val detail: String) : ShiftSwapFacadeError()
}
