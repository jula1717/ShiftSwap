package shiftswap

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class InMemoryShiftSwapRepository : ShiftSwapRepository {
    private val storage = ConcurrentHashMap<Int, ShiftSwapRequest>()
    private val nextId = AtomicInteger(1)

    override suspend fun insert(request: ShiftSwapRequest): ShiftSwapRequest {
        val assigned = request.copy(id = nextId.getAndIncrement())
        storage[assigned.id] = assigned
        return assigned
    }

    override suspend fun find(id: Int): ShiftSwapRequest? = storage[id]

    override suspend fun update(request: ShiftSwapRequest): ShiftSwapRequest {
        storage[request.id] = request
        return request
    }
}
