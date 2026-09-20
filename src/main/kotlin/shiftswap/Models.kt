package shiftswap

enum class ShiftSwapStatus {
    REQUESTED,
    APPROVED,
    DENIED
}

enum class ShiftSwapDecision {
    APPROVE,
    DENY
}

data class ShiftSegment(
    val label: String,
    val hours: Double,
    val hourlyRateInDollars: Double
)

data class ShiftSwapRequest(
    val id: Int,
    val status: ShiftSwapStatus,
    val requesterId: Int,
    val filedBy: Int,
    val approverId: Int? = null,
    val deniedBy: Int? = null,
    val segments: List<ShiftSegment>,
    val payAdjustmentCents: Int? = null
)

data class ShiftSwapDto(
    val id: Int,
    val status: ShiftSwapStatus,
    val requesterId: Int,
    val filedBy: Int,
    val approverId: Int?,
    val deniedBy: Int?,
    val payAdjustmentCents: Int?
) {
    constructor(request: ShiftSwapRequest) : this(
        id = request.id,
        status = request.status,
        requesterId = request.requesterId,
        filedBy = request.filedBy,
        approverId = request.approverId,
        deniedBy = request.deniedBy,
        payAdjustmentCents = request.payAdjustmentCents
    )
}

data class RequestShiftSwapPayload(
    val requesterId: Int,
    val filedBy: Int,
    val segments: List<ShiftSegment>
)
