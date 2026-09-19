package shiftswap.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess
import shiftswap.InMemoryShiftSwapRepository
import shiftswap.ShiftSwapRequest
import shiftswap.ShiftSwapStatus

fun main() = runBlocking {
    val iterations = 2_000
    val repository = InMemoryShiftSwapRepository()

    val ids = coroutineScope {
        (0 until iterations).map { i ->
            async(Dispatchers.Default) {
                val request = ShiftSwapRequest(
                    id = 0,
                    status = ShiftSwapStatus.REQUESTED,
                    requesterId = i,
                    filedBy = i,
                    segments = emptyList()
                )
                repository.insert(request).id
            }
        }.map { it.await() }
    }

    val uniqueIds = ids.toSet()
    println("Inserted $iterations requests concurrently.")
    println("Unique ids assigned: ${uniqueIds.size} (expected $iterations)")

    if (uniqueIds.size != iterations) {
        println("FAIL: the repository is not safe for concurrent use (lost/duplicated ids).")
        exitProcess(1)
    }

    println("PASS: the repository handled concurrent inserts correctly.")
}
