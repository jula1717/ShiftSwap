package shiftswap

sealed class ShiftSwapError : Exception() {
    data class NotFound(val id: Int) : ShiftSwapError()
    data class InvalidState(val current: ShiftSwapStatus, val attempted: String) : ShiftSwapError()
    data class Underlying(val detail: String) : ShiftSwapError()
}

sealed class ShiftSwapFacadeError : Exception() {
    data class Failed(val detail: String) : ShiftSwapFacadeError()
}
