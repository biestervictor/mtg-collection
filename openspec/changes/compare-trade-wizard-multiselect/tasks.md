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

- [x] 4.1 Tom Select v2.3.1 via CDN (jsdelivr) einbinden — abweichend vom ursprünglichen Plan (Vendoring), konsistent mit bestehender Bootstrap-Icons-CDN-Strategie; ggf. später vendoren
- [x] 4.2 In `compare.html` bestehende Custom-Set-Dropdown-Komponente entfernt (Custom CSS `.set-search-wrapper`/`.set-option-item` gelöscht), durch `<select id="setMultiSelect" name="sets" multiple>` mit Tom-Select-Initialisierung ersetzt
- [x] 4.3 Tom-Select-Config: `plugins: ['remove_button']`, `render.option`/`render.item` mit Set-Icon (aus `data-icon` über `iconMap` lookup vor Init) und Set-Name; dark-theme via CSS-Overrides (.ts-wrapper, .ts-dropdown, .ts-set-icon)
- [x] 4.4 GET-Form serialisiert Multi-Select automatisch als `?sets=tdm&sets=dmu` Repeating-Params; Backend liest mit `@RequestParam(name="sets") List<String>` (Block 1 fertig)
- [x] 4.5 Auf Page-Load: Thymeleaf rendert `th:selected` auf Options via `#lists.contains(selectedSets, s.setCode)` — Tom Select übernimmt automatisch; `?set=` Single-Param-Fallback im Controller (Block 1.2)
- [x] 4.6 `submitCompareForm()` prüft `setSelect.getValue().length > 0` zusätzlich zu beiden Usern bevor submit

## 5. Frontend — Collapsable Set-Sektionen

- [x] 5.1 Im Template `compare.html`: bisheriger Flat-Result-Block ersetzt durch `th:each="entry : ${setResults}"` über LinkedHashMap (~250 Lines duplizierter Card-Grid/Text-Table in Fragment ausgelagert)
- [x] 5.2 Pro Set ein Bootstrap `<div class="accordion-item">` mit Header (Set-Icon, Set-Code, Set-Name, Badges für onlyUser/onlyCompare Counts) und Body (Diff via Fragment-Call `~{fragments/compare-diff :: diff-column}`)
- [x] 5.3 Accordion-IDs eindeutig pro Set: `id="h-{setCode}"` und `id="body-{setCode}"`
- [x] 5.4 Default-Zustand: Sektion expanded wenn `#maps.size(setResults) == 1`, sonst collapsed (umgesetzt via `expanded` Local Variable + `th:classappend`)
- [x] 5.5 Token-/Promo-Sub-Sektionen als `.subset-card` innerhalb des Accordion-Bodys mit eigenen Fragment-Calls (showPrice=false für Token); rendern wenn `tokenResults.get(setCode) != null`
- [x] 5.6 CSS: Accordion-Header mit Accent-Color-Highlight beim Expanded-State (`:not(.collapsed)`), Hover-Linien, dark-theme conform; sticky entfällt (Bootstrap-Accordion-Standard)

## 6. Frontend — Trade Wizard UI

- [x] 6.1 Oberhalb der Set-Sektionen `.wizard-card` mit Mode-Toggle (Greedy/Bundle Radio-Buttons), Toleranz-Slider (1–50%, Live-Label), Mindestwert-Input (€, default 0.50), Berechnen-Button
- [x] 6.2 Wizard-Card erscheint nur wenn `setResults != null and not empty` (via `th:if`); innerhalb des Containers reicht das aus — kein separates Tooltip nötig (Sektion ist dann gar nicht sichtbar)
- [x] 6.3 JS `runWizard()`: fetch-POST an `/api/compare/trade-wizard` mit `{userA, userB, sets, mode, tolerancePercent, minCardValue}`
- [x] 6.4 Loading: Button disabled + `<span class="spinner-border spinner-border-sm">` während Request; reset in `finally`
- [x] 6.5 `renderWizardResult()`: 3-Col-Grid (Side A | Arrow | Side B), `<ul>` mit Set-Code · Card-Name · Foil-Mark · Preis, `.sum` Footer, Fairness-Badge oben rechts mit Score % + Δ€
- [x] 6.6 Fairness-Badge-Farben: ≥0.95 `.fair` grün, 0.80–0.95 `.acceptable` gelb, <0.80 `.unfair` rot (kleinere Schwelle als ursprünglicher Plan — projektkonsistent mit Service-Implementation)
- [x] 6.7 `<details>`-basierte "Übersprungen"-Sektion (`.wizard-skipped`) mit Card-Name + Grund pro Pool (skippedA, skippedB)
- [x] 6.8 Bei Re-Run wird `#wizardResult` und `#wizardError` zuerst geleert, dann neu gerendert; kein Page-Reload nötig

## 7. Tests & Integration

- [ ] 7.1 End-to-End Browser-Test (manuell auf Dev-Stage): Multi-Select 3 Sets → Tags erscheinen, X-Klick entfernt Tag, Submit zeigt 3 collapsable Sektionen
- [ ] 7.2 End-to-End Wizard-Test (manuell): Beide Modi durchspielen, Fairness-Badges prüfen, Toleranz-Slider Live-Update
- [ ] 7.3 Test mit Token/Promo-Toggles + Multi-Set: Sub-Sektionen erscheinen pro Hauptset
- [ ] 7.4 Test alte Bookmark-URL `?set=tdm` öffnet Single-Set wie bisher
- [ ] 7.5 Performance-Test im Browser: Wizard-Klick mit 3 Sets sollte <500ms Antwort liefern (Network + Render)

## 8. Build & Deploy

- [x] 8.1 Lokal `mvn test` — alle 412 Tests grün (Backend war 375 vorher, +37 neue)
- [ ] 8.2 Commit auf `dev` mit Message gemäß Konvention (`feat: trade wizard frontend + multi-set accordion`)
- [ ] 8.3 Warten auf CI-Build + ArgoCD-Deploy auf Dev-Stage
- [ ] 8.4 Manueller Test auf `https://mtg-dev.biester.vip/compare`
- [ ] 8.5 Bei Erfolg: `git pull origin dev` (CI-Commits holen), `dev → main` mergen, pushen
- [ ] 8.6 COSTS.md mit Delta-Eintrag + Session-Total aktualisieren
