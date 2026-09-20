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

## Problem 5 - Dodanie operacji `ShiftSwapService.deny(requestId, deniedBy)`

### Natura problemu

Metoda `deny` wymagała implementacji.

### Rozwiązanie

Przy implementacji można było skorzystać z metod zastosowanych przy rozwiązywaniu pozostałych
problemów:

1. Powiadamianie przez `notifySafely()` z istniejącym zdarzeniem `ShiftSwapEvent.SwapDenied`. Błąd w
   notify nie powinien wpływać na wynik operacji.
2. Skorzystanie z metody `requirePendingRequest()` - walidacja, czy request istnieje i jest w stanie
   `REQUESTED`.
3. Skorzystanie z metody `toFacadeResult()` - mapowanie błędów domenowych na błędy fasady.

Poza tym rozwiązanie obejmuje:

1. Dodanie pola `deniedBy: Int? = null` do `ShiftSwapRequest` i `ShiftSwapDto`. Dołączony do zadania
   test sprawdza jedynie `status` po odrzuceniu, nie kto odrzucił, ale `approvedBy` to nazwa, która
   twierdzi, że ktoś zatwierdził prośbę - zapisanie tam osoby odrzucającej sprawiłoby, że pole
   kłamałoby wprost przy `status = DENIED` (podobny problem pomieszania znaczenia pól miał już
   miejsce w problemie 3 i stąd głównie decyzja na takie rozwiązanie). Dzięki wartości domyślnej
   `null` ta zmiana nie wymagała poprawek w istniejących użyciach. Można było również wprowadzić
   jedno neutralne `decidedBy: Int?`, odczytywane zawsze razem ze `status`, ale wtedy sama nazwa
   pola nie mówiła o rodzaju decyzji bez patrzenia na `status`, stąd wybrałam osobne, jednoznaczne
   pole.
2. Dodanie testu `requestingAndDenyingThroughTheFacade_returnsDeniedRequest`, aby `deny` miała
   pokrycie również na poziomie fasady, a nie tylko serwisu.

## Problem 6* - naprawienie logiki odpowiedzialnej za fail testu `payAdjustment_accountsForManySmallSegmentsWithoutDrift`

### Natura problemu

Metoda sumowała `hours * hourlyRateInDollars` w `Double`, a na
końcu obcinała (`.toInt()`) zamiast zaokrąglić. W związku z tym błąd przy sumowaniu wielu małych
wartości (np. dziesięciu `0.1`), połączony z obcinaniem w dół, dawał `99` centów zamiast `100`.

### Rozwiązanie

Każdy segment jest zaokrąglany do pełnych centów osobno (`roundToLong()`), zanim błędy zdążą się
skumulować w sumie, a wynik sumowany jako `Long` z `Math.toIntExact()` pilnuje przepełnienia `Int`.
Test `approvingWithAnOverflowingPayAdjustment_returnsFailedFailure` sprawdza, że przepełnienie `Int`
nadal kończy się wyjątkiem i że jest on mapowany na `ShiftSwapFacadeError.Failed` tym samym
mechanizmem co w problemie 2 (`toFacadeResult()`).