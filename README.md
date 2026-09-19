# Opis problemów i rozwiązań

## Problem 1 - Zatwierdzanie prośby czasem wygląda na nieudane

### Natura problemu

Problem polegał na tym, że metoda `ShiftSwapService.approve()` wołała notifier bezpośrednio bez
zabezpieczeń. Nawet jeśli request został zatwierdzony w repozytorium, to w przypadku wyjątku w
`notifier.notify(ShiftSwapEvent)`, wyjątek był propagowany w górę jako niezłapany, co powodowało, że
test `approvalSucceeds_evenWhenNotificationChannelFails` nie przechodził.

### Rozwiązanie

Dzięki wywołaniu metody `notifySafely()` w `ShiftSwapService` (polegającej na opakowaniu w
try...catch) błędy są logowane, ale nie propagowane dalej. Wyjątek nie wpływa na wynik zatwierdzania
requestu, ale jednocześnie dostarcza powiadomienie jako log, że coś poszło nie tak w kanale
powiadomień. W ten sposób test `approvalSucceeds_evenWhenNotificationChannelFails`
przechodzi i nie wymagało to zmiany jego asercji. Jednocześnie CancellationException jest
rethrowowany, aby nie blokować anulowania korutyny (structure concurrency).

## Problem 2 - `ShiftSwapFacade.approve()` zwraca ten sam ogólny przypadek błędu dla każdej możliwej przyczyny
niepowodzenia

### Natura problemu

Każdy rodzaj wyjątku w `ShiftSwapService.approve()` był propagowany w górę do fasady i tam trafiał
do jednej kategorii `Failed`, przez co niezależnie od przyczyny niepowodzenia, facade zwracał ten
sam ogólny błąd.

### Rozwiązanie

Dzięki dodaniu brakujących klas wyjątków w `ShiftSwapFacadeError` jest możliwe mapowanie 1:1 na
błędy domenowe:

1. `ShiftSwapError.NotFound` --> `ShiftSwapFacadeError.NotFound` - request nie istnieje w
   repozytorium
2. `ShiftSwapError.InvalidState` --> `ShiftSwapFacadeError.InvalidState` - request nie jest w stanie
   `REQUESTED`
3. `ShiftSwapError.Underlying` --> `ShiftSwapFacadeError.Underlying` - błąd infrastruktury (np.
   wyjątek w repozytorium), tworzony przez nową metodę `repositoryCall()`, która opakowuje każde
   wywołanie portu `ShiftSwapRepository` (`insert`/`find`/`update`) i tłumaczy na ten typ dowolny
   nieoczekiwany wyjątek

Pozostawiony `ShiftSwapFacadeError.Failed` jako ogólny przypadek, może być użyty w przypadku
nieprzewidzianych błędów.
Mapowanie zostało wydzielone do metody `toFacadeResult()` - reużycie kodu w dalszym rozwiązaniu dla
problemu 5 z `deny`(ponieważ obie operacje wymagają tej samej logiki mapowania błędów).

Wszystkie 4 klasy wyjątków mają swoje pokrycie w dodanych testach, a także w zmodyfikowanym teście (
`approvingMissingRequest_currentlyReturnsGenericFailure` -->
`approvingMissingRequest_returnsNotFoundFailure`)

Dodatkowo dzięki dodaniu dla pola `attempted` enum class `ShiftSwapDecision`, zapewnione jest
bezpieczeństwo typów. Dodałam także obsługę CancellationException (jak dla problemu 1).

