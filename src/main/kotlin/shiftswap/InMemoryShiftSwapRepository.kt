package shiftswap

class InMemoryShiftSwapRepository : ShiftSwapRepository {
    private val storage = HashMap<Int, ShiftSwapRequest>()
    private var nextId = 1

    override suspend fun insert(request: ShiftSwapRequest): ShiftSwapRequest {
        val assigned = request.copy(id = nextId)
        nextId += 1
        storage[assigned.id] = assigned
        return assigned
    }

    override suspend fun find(id: Int): ShiftSwapRequest? = storage[id]

    override suspend fun update(request: ShiftSwapRequest): ShiftSwapRequest {
        storage[request.id] = request
        return request
    }
}
