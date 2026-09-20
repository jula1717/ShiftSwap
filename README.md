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

## Problem 2 - `ShiftSwapFacade.approve()` zwraca ten sam ogólny przypadek błędu dla każdej możliwej przyczyny niepowodzenia

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

## Problem 3 - Naprawienie problemu zgłoszonego przez support

### Natura problemu i jego zdiagnozowanie

Skoro problem dotyczył pomieszania danych właściciela requestu (pracownika,
którego dotyczy zmiana) i osoby, która złożyła wniosek, w pierwszej
kolejności sprawdziłam, gdzie te dwie wartości są w ogóle ustawiane - i w
`ShiftSwapFacade.request()` zauważyłam zamienioną kolejność:

```kotlin
private fun resolveSwapOwnership(payload: RequestShiftSwapPayload): Ownership =
   Ownership(requesterId = payload.filedBy, filedBy = payload.requesterId)
```

Zauważenie tego ułatwiły dodatkowo rzucające się w oczy named parameters -
`requesterId = payload.filedBy` z przypisanymi odwrotnymi wartościami.

Żeby zrozumieć, czemu problem występował tylko "czasem", zwróciłam uwagę, że we wcześmniejszych
testach `requesterId` i `filedBy` to ta sama wartość, więc błąd nie zostanie w takim przypadku
zauważony.

### Rozwiązanie

Usunęłam metodę `resolveSwapOwnership()` wraz z klasą `Ownership` w całości,
zamiast tylko poprawić kolejność argumentów w środku - po takiej korekcie
`Ownership` stałoby się zwykłym przepisaniem tych samych dwóch pól bez żadnej
dodatkowej logiki, więc sama jej obecność byłaby zbędna. `ShiftSwapFacade.request()`
przekazuje teraz `payload.requesterId` i `payload.filedBy` bezpośrednio do
`ShiftSwapService.request()`.

Dzięki dodaniu testu `requestingOnBehalfOfSomeoneElse_keepsRequesterAndFilerDistinct`,
który celowo ustawia `requesterId != filedBy` kod jest zabezpieczony przed ponownym pomieszaniem
tych dwóch wartości w przyszłości.

## Problem 4 - `InMemoryShiftSwapRepository` nie jest bezpieczne przy współbieżności

### Natura problemu

`nextId: Int` i `storage: HashMap` były modyfikowane bez synchronizacji z wielu korutyn równolegle (
`Dispatchers.Default`, czyli z wielu wątków). `nextId += 1` to tak naprawdę 3 osobne operacje (
odczyt, inkrementacja i zapis), przez co dwie korutyny mogły odczytac ten sam `id` zanim ktoras z
nich zapisała wartość, a `HashMap` nie jest bezpieczny przy współbieżnych zapisach (np. utrata
wpisu, nadpisanie czy problemy ze zmianą rozmiaru struktury). `ConcurrencyStressCheck` (2000
równoległych wstawień) wykrywał to jako niezgodną liczbę unikalnych `id`.

### Rozwiązanie

`AtomicInteger.getAndIncrement()` łączy odczyt i inkrementację wartości w jedną atomową
(niepodzielną) operację, więc dwie korutyny nigdy nie dostaną tego samego `id` i nie zapiszą wpisu
pod tym samym indeksem, a `ConcurrentHashMap` gwarantuje bezpieczeństwo pojedynczych zapisów
pod różnymi kluczami.