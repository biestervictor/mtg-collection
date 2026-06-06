## 1. Backend: Refresh Endpoint

- [x] 1.1 Neuen Endpoint `POST /api/sets/refresh` im CollectionController erstellen (ruft `scryfallService.getAllSets(true)` auf, redirect auf `/show`)
- [x] 1.2 Unit-Test fuer den neuen Endpoint schreiben

## 2. Show-Seite: Refresh Button

- [x] 2.1 "Refresh Sets"-Button in show.html einfuegen (neben den bestehenden "Clear Cache"-Buttons)
- [x] 2.2 Button als POST-Form implementieren (mit Action `/api/sets/refresh`)

## 3. Compare-Seite: Custom Set-Dropdown

- [x] 3.1 CSS fuer das custom Set-Dropdown von show.html nach compare.html uebernehmen (`.set-search-wrapper`, `.set-input-row`, `.set-icon-current`, `.set-options-dropdown`, `.set-option-item`, `.set-opt-icon`)
- [x] 3.2 HTML-Struktur des Set-Dropdowns von show.html nach compare.html uebernehmen (ersetze das Select2-`<select>` durch den custom wrapper mit `set-option-item` divs)
- [x] 3.3 JavaScript fuer Set-Search (setData Array, filterSetOptions, selectSetOption, applySetSelection) von show.html nach compare.html uebernehmen
- [x] 3.4 Select2-CSS und JS-Includes entfernen (falls keine andere Nutzung auf der Seite), andernfalls nur die Set-Select2-Initialisierung entfernen
- [x] 3.5 Vorauswahl des Sets (`th:value="${selectedSet}"`) korrekt im custom Dropdown restaurieren

## 4. Verifikation

- [x] 4.1 Lokaler Test: Compare-Seite zeigt Set-Dropdown mit Icons und Suchfunktion
- [x] 4.2 Lokaler Test: Show-Seite hat funktionierenden "Refresh Sets"-Button
- [x] 4.3 `mvn test` muss bestehen
