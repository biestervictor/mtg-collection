## 1. Backend — Multi-Set-Controller-Refactor

- [x] 1.1 In `CollectionController.compareCollection()`: neuen `@RequestParam(name="sets", required=false) String setsParam` einlesen; bestehenden `@RequestParam(name="set", required=false) String set` als Fallback behalten
- [x] 1.2 Helper-Methode `private List<String> normalizeSets(String setsParam, String singleSet)` ergänzen: split nach `,`, trim, lowercase, distinct, empty-filter; `sets` gewinnt vor `set`
- [x] 1.3 `setResults`-Map (`LinkedHashMap<String, CompareDiff>`) aufbauen: pro Set in `normalizedSets` einmal `getCardsBySet` + `computeDiff` aufrufen
- [x] 1.4 Token/Promo-Loops anpassen: pro Hauptset eigene Sub-Maps (`tokenResults`, `promoResults`) aufbauen; im Template per Set-Code zugreifbar
- [x] 1.5 Backwards-Compat-Test: `compareCollection_singleSetParam_stillWorks` schreiben (URL mit `?set=tdm`)
- [x] 1.6 Multi-Set-Test: `compareCollection_multipleSetsParam_loadsAllSets` schreiben
- [x] 1.7 Sets-vor-set-Test: `compareCollection_bothParams_setsWins` schreiben
- [x] 1.8 Leer-Test: `compareCollection_emptySetsParam_noResults` schreiben

## 2. Backend — TradeWizardService

- [x] 2.1 Neue Java-Records erstellen: `TradeCard(String cardId, String name, String setCode, Double price, boolean foil, String owner)`, `TradePair(TradeCard fromA, TradeCard fromB)`, `TradeBundle(List<TradeCard> aSide, List<TradeCard> bSide)`, `SkippedCard(TradeCard card, String reason)`, `TradeMatchResult`, `TradeBundleResult` (Double statt BigDecimal — projektkonsistent mit ScryfallCard.priceRegular/Foil)
- [x] 2.2 Neue Klasse `com.mtg.collection.service.TradeWizardService` mit Spring `@Service` Annotation
- [x] 2.3 `buildPool(String user, String otherUser, List<String> setCodes, Double minValue, List<SkippedCard> skippedOut)`: traversiert UserCollection, filtert tradeable (≥2 own, =0 other), filtert nach `minValue`, gibt `List<TradeCard>` zurück; Foil und Normal als separate Einträge; skipped-out via Side-Effect
- [x] 2.4 `greedyMatch(List<TradeCard> poolA, List<TradeCard> poolB, double tolerancePercent)`: sortiere beide ↓ nach Preis, sekundär ↑ nach Name, iteratives Pair-Matching mit Toleranz, return `TradeMatchResult` (pairs + 2× skipped)
- [x] 2.5 `karmarkarKarpMatch(List<TradeCard> poolA, List<TradeCard> poolB)`: KK-Heuristik mit Max-Heap aus `java.util.PriorityQueue`; return `TradeBundleResult` mit allen Karten beider Seiten sortiert nach Preis ↓
- [x] 2.6 `computeFairnessScore(double sumA, double sumB)`: `1.0 - abs(sumA-sumB) / max(sumA, sumB)`, clamp [0,1]; bei sumA=sumB=0 → 1.0
- [x] 2.7 Pool-Truncation: wenn Pool > 300 → sortiere nach Preis ↓, behalte Top-300, restliche in `skipped` mit Grund "pool_truncated"
- [x] 2.8 Unit-Tests `TradeWizardServiceTest`: 22 Tests (buildPool 6, greedyMatch 5, KK 4, fairnessScore 4, truncation 1, perf 2)
- [x] 2.9 Performance-Test mit synthetischem Pool von 200 Karten je Seite: lokal-Budget Greedy <200ms, KK <500ms (RPi-Budget Greedy <50ms, KK <200ms — gemessen bei Deploy)

## 3. Backend — Wizard-Endpoint

- [x] 3.1 Neuen Controller `com.mtg.collection.controller.TradeWizardController` mit `@RestController` und `@RequestMapping("/api/compare")`
- [x] 3.2 Request-DTO `TradeWizardRequest(String userA, String userB, List<String> sets, String mode, double tolerancePercent, Double minCardValue)` mit Bean-Validation (`@NotBlank`, `@NotEmpty`, `@Pattern(greedy|bundle)`, `@DecimalMin/Max`)
- [x] 3.3 Response-DTO `TradeWizardResponse(String mode, List<TradePair> pairs, TradeBundle bundle, double totalA, double totalB, double diff, double fairnessScore, List<SkippedCard> skippedA, List<SkippedCard> skippedB, List<String> notes)`
- [x] 3.4 Endpoint `@PostMapping("/trade-wizard")` mit `@Valid @RequestBody`, ruft `TradeWizardService` auf, mapped Mode "greedy"/"bundle" auf entsprechende Service-Methode; Domain-Validierung (userA/B ∈ {Victor,Andre}, userA≠userB)
- [x] 3.5 Validation-Handler: `@ExceptionHandler(MethodArgumentNotValidException.class)` → 400 mit `ProblemDetail`; auch `IllegalArgumentException` → 400
- [x] 3.6 Unit-Test `TradeWizardControllerTest`: 10 Tests (Happy Greedy/Bundle/Multi-Set/skipped + 6 Validation-Cases — 400 bei empty sets, invalid mode, invalid user, same userA/B, blank userA, tolerance out of range)

## 4. Frontend — Tom Select Multi-Select

- [ ] 4.1 Tom Select v2 (MIT, ~30 KB gzipped) vendoren: JS + CSS unter `src/main/resources/static/vendor/tom-select/`
- [ ] 4.2 In `compare.html` bestehende Custom-Set-Dropdown-Komponente identifizieren (aus `compare-set-icons-refresh`), durch `<select multiple>` mit Tom-Select-Initialisierung ersetzen
- [ ] 4.3 Tom-Select-Config: `plugins: ['remove_button']`, `render.option`/`render.item` mit Set-Icon und Set-Name (Custom-HTML wie bisher)
- [ ] 4.4 On-Submit-Handler: konvertiere ausgewählte Sets zu Komma-getrennter String, setze als `?sets=...` URL-Param
- [ ] 4.5 Auf Page-Load: Parse `?sets=...` und `?set=...`, initialisiere Tom Select mit den vorausgewählten Werten
- [ ] 4.6 Mindestens-ein-Set-Validation: Submit-Button disabled wenn Auswahl leer

## 5. Frontend — Collapsable Set-Sektionen

- [ ] 5.1 Im Template `compare.html`: bisherigen einzelnen Result-Block in `th:each` über `setResults` einbetten
- [ ] 5.2 Pro Set ein Bootstrap `<div class="accordion-item">` mit Header (Set-Icon, Set-Name, Code, Badges für onlyUser/onlyCompare Counts) und Body (bestehende Diff-Darstellung)
- [ ] 5.3 Accordion-IDs eindeutig pro Set (z.B. `accordion-set-tdm`)
- [ ] 5.4 Default-Zustand: Sektion expanded wenn `setResults.size() == 1`, sonst collapsed
- [ ] 5.5 Token/Promo-Sub-Sektionen pro Hauptset unter der entsprechenden Accordion-Sektion (eingerückt oder als Sub-Accordion)
- [ ] 5.6 CSS: Accordion-Header-Hover-Highlight, sticky-position für Header beim Scroll innerhalb expanded Body

## 6. Frontend — Trade Wizard UI

- [ ] 6.1 Oberhalb der Set-Sektionen einen Wizard-Block einfügen: Button "Trade berechnen", Mode-Toggle (Greedy/Bundle), Toleranz-Slider (1–50%), Mindestwert-Input (€)
- [ ] 6.2 Button disabled wenn `setResults` leer; Tooltip "Mindestens ein Set wählen"
- [ ] 6.3 JS: fetch-POST an `/api/compare/trade-wizard` mit aktueller Konfiguration (Sets aus Tom Select, beide User, Mode, Toleranz, MinValue)
- [ ] 6.4 Loading-Spinner während Request (button-disabled + Bootstrap-Spinner)
- [ ] 6.5 Response-Rendering: zwei Spalten "Du bietest" / "Du bekommst" mit Karten-Liste (Name, Set-Code, Foil-Icon, Preis), Summen unten, Fairness-Badge oben
- [ ] 6.6 Fairness-Badge-Farben: ≥0.95 grün "Fair", 0.85–0.95 gelb "Akzeptabel", <0.85 rot "Unfair" + €-Differenz
- [ ] 6.7 "Übersprungen"-Sektion (collapsable) zeigt skipped-Karten mit Grund (no_price, below_min_value, pool_truncated, no_match_in_tolerance)
- [ ] 6.8 Bei Re-Run (Toggle/Slider geändert + Button-Klick): aktualisiere bestehende Wizard-Sektion ohne Page-Reload

## 7. Tests & Integration

- [ ] 7.1 End-to-End Browser-Test (manuell auf Dev-Stage): Multi-Select 3 Sets → Tags erscheinen, X-Klick entfernt Tag, Submit zeigt 3 collapsable Sektionen
- [ ] 7.2 End-to-End Wizard-Test (manuell): Beide Modi durchspielen, Fairness-Badges prüfen, Toleranz-Slider Live-Update
- [ ] 7.3 Test mit Token/Promo-Toggles + Multi-Set: Sub-Sektionen erscheinen pro Hauptset
- [ ] 7.4 Test alte Bookmark-URL `?set=tdm` öffnet Single-Set wie bisher
- [ ] 7.5 Performance-Test im Browser: Wizard-Klick mit 3 Sets sollte <500ms Antwort liefern (Network + Render)

## 8. Build & Deploy

- [ ] 8.1 Lokal `mvn test` — alle bisherigen 375 Tests + neue müssen grün sein
- [ ] 8.2 Commit auf `dev` mit Message gemäß Konvention (`feat: trade wizard with multi-set on compare page`)
- [ ] 8.3 Warten auf CI-Build + ArgoCD-Deploy auf Dev-Stage
- [ ] 8.4 Manueller Test auf `https://mtg-dev.biester.vip/compare`
- [ ] 8.5 Bei Erfolg: `git pull origin dev` (CI-Commits holen), `dev → main` mergen, pushen
- [ ] 8.6 COSTS.md mit Delta-Eintrag + Session-Total aktualisieren
