package shiftswap

class ThrowingShiftSwapNotifier : ShiftSwapNotifier {
    class NotificationFailure : Exception()

    override suspend fun notify(event: ShiftSwapEvent) {
        throw NotificationFailure()
    }
}

class RecordingShiftSwapNotifier : ShiftSwapNotifier {
    private val _events = mutableListOf<ShiftSwapEvent>()
    val events: List<ShiftSwapEvent> get() = _events

    override suspend fun notify(event: ShiftSwapEvent) {
        _events.add(event)
    }
}

class RecordingShiftSwapEventLogger : ShiftSwapEventLogger {
    private val _messages = mutableListOf<String>()
    val messages: List<String> get() = _messages

    override suspend fun log(message: String) {
        _messages.add(message)
    }
}

fun makeFacade(
    repository: ShiftSwapRepository,
    notifier: ShiftSwapNotifier = RecordingShiftSwapNotifier(),
    eventLogger: ShiftSwapEventLogger = RecordingShiftSwapEventLogger()
): ShiftSwapFacade = ShiftSwapFacade(repository, notifier, eventLogger)
