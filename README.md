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