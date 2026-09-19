# ShiftSwapKit - zadanie techniczne (Senior Android)

To zadanie ocenia pracę w czystej logice modułu (warstwa serwisowa +
repozytorium/notifier jako interfejsy), nie Jetpack Compose/Android SDK. Masz swobodę w tym, jak zorganizujesz
nowe/przebudowane pliki, byle było rozdzielenie odpowiedzialności.

System obsługuje zatwierdzanie/odrzucanie próśb o zamianę zmiany (shift swap) w
aplikacji do grafików pracowniczych, przez `ShiftSwapFacade` → `ShiftSwapService`
→ `ShiftSwapRepository` (interfejs). Kod działa i ma testy, ale zespół zgłasza
kilka niezależnych, realnych problemów:

1. Czasem zatwierdzenie prośby "wygląda na nieudane", mimo że w repozytorium widać poprawnie zatwierdzoną prośbę.
2. `ShiftSwapFacade.approve()` zwraca **ten sam, jeden ogólny przypadek błędu** dla każdej możliwej przyczyny niepowodzenia -
   także dla sytuacji, które wcale nie są błędem infrastruktury.
3. **Zgłoszenie od supportu, którego nikt jeszcze nie zdiagnozował:**
   ```
   "część nowo złożonych próśb o zamianę zmiany w raportach kadrowych ma pomieszane dane
   właściciela - pracownik proszący o zamianę i osoba, która faktycznie złożyła
   wniosek w aplikacji, są jakby zamienione miejscami, ale nie za każdym razem i
   nie widać tego w żadnym z naszych dotychczasowych testów".
   ```
4. Repozytorium w pamięci nie jest bezpieczne przy współbieżnym dostępie z wielu korutyn - uruchom `./gradlew run` kilka razy,
   żeby zobaczyć to w akcji.

Dołączone testy pokazują część tego wprost:
`src/test/kotlin/shiftswap/ApproveShiftSwapTests.kt` **nie przechodzi w 2 z 3
przypadków**, a
`src/test/kotlin/shiftswap/ShiftSwapFacadeTests.kt` dokumentuje obecne (błędne)
zachowanie facade'a.

### Co należy zrobić

1. **Napraw problem 1** - spraw, żeby `approvalSucceeds_evenWhenNotificationChannelFails`
   przeszedł, bez zmiany treści jego asercji.
2. **Napraw problem 2** - `ShiftSwapFacade` powinien mapować różne rodzaje błędów
   na różne, rozróżnialne przypadki. Zaktualizuj `approvingMissingRequest_currentlyReturnsGenericFailure` tak, żeby
   odzwierciedlał **poprawione** zachowanie.
3. **Znajdź i napraw problem 3** (zgłoszenie supportu opisane wyżej).
4. **Napraw problem 4** - spraw, żeby `ConcurrencyStressCheck` przechodził **konsekwentnie**, nie tylko czasami.
5. **Dodaj własną, nową operację w tym samym stylu** - w
   `src/test/kotlin/shiftswap/DenyShiftSwapTests.kt` dostajesz testy dla
   `ShiftSwapService.deny(requestId, deniedBy)`, którego **jeszcze nie ma**. Na podstawie samych testów zaprojektuj i zaimplementuj brakującą
   logikę, używając podejścia, do którego doszedłeś naprawiając punkty 1-4 - nie
   odtwarzaj oryginalnych problemów w nowym kodzie.

### Na co zwrócić uwagę

- `ApproveShiftSwapTests.approvingARequestedSwap_marksItApprovedWithPayAdjustment`
  i `ShiftSwapFacadeTests.requestingAndApprovingThroughTheFacade_returnsApprovedRequest`
  (happy-path) muszą zostać zielone przed i po Twoich zmianach, bez zmiany treści
  asercji.
- Nie musisz wprowadzać rzeczywistej sieci/kolejki na potrzeby problemu 1 - da się i należy go rozwiązać bez tego.
- Do problemu 3 zalecamy realnie odpalić kod i użyć debugera zamiast zgadywać z samego czytania kodu.

### Co dostarczasz

Repozytorium z przebudowanym kodem, zielonymi testami (`./gradlew test` - w tym
nowy test na logikę liczenia kwoty i nowe testy Deny) oraz `README.md`, w którym
opisujesz naturę wszystkich problemów (włącznie z tym, jak zdiagnozowałeś problem
3 - jakim tropem poszedłeś) i uzasadniasz projekt nowej operacji (Deny).
