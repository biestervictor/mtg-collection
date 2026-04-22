package com.mtg.collection.service;

import com.mtg.collection.dto.CardWithUserData;
import com.mtg.collection.dto.ImportResult;
import com.mtg.collection.dto.ImportResult.DuplicateInfo;
import com.mtg.collection.model.ImportHistory;
import com.mtg.collection.model.ImportHistory.ImportedCardInfo;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.ImportHistoryRepository;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.UserCardRepository;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InventoryImportService {

    private static final Logger log = LoggerFactory.getLogger(InventoryImportService.class);

    private final UserCardRepository userCardRepository;
    private final ScryfallCardRepository scryfallCardRepository;
    private final ScryfallService scryfallService;
    private final ImportHistoryRepository importHistoryRepository;
    private final UserDeckService userDeckService;

    public InventoryImportService(UserCardRepository userCardRepository,
                                 ScryfallCardRepository scryfallCardRepository,
                                 ScryfallService scryfallService,
                                 ImportHistoryRepository importHistoryRepository,
                                 UserDeckService userDeckService) {
        this.userCardRepository = userCardRepository;
        this.scryfallCardRepository = scryfallCardRepository;
        this.scryfallService = scryfallService;
        this.importHistoryRepository = importHistoryRepository;
        this.userDeckService = userDeckService;
    }

    // ── Inner class returned by parseInventoryFile ────────────────────────────
    static class ParseResult {
        final List<UserCard>     cards;
        final List<DuplicateInfo> duplicates;

        ParseResult(List<UserCard> cards, List<DuplicateInfo> duplicates) {
            this.cards      = cards;
            this.duplicates = duplicates;
        }
    }

    public ImportResult importInventory(String user, MultipartFile file) {
        ImportResult result = new ImportResult();
        List<String> errors = new ArrayList<>();
        List<CardWithUserData> newCards = new ArrayList<>();
        Set<String> importedSetCodes = new HashSet<>();

        try {
            ParseResult parseResult = parseInventoryFile(file);
            List<UserCard> importedCards = parseResult.cards;
            result.setDuplicatesRemoved(parseResult.duplicates);

            int cardsCount = importedCards.stream().mapToInt(UserCard::getQuantity).sum();
            result.setCardsCount(cardsCount);

            log.info("Parsed {} cards from inventory file ({} exact duplicates removed)",
                    importedCards.size(), parseResult.duplicates.size());

            long foilCount = importedCards.stream().filter(UserCard::isFoil).count();
            long normalCount = importedCards.stream().filter(c -> !c.isFoil()).count();
            log.info("Foil cards: {}, Normal cards: {}", foilCount, normalCount);

            List<UserCard> currentCollection = userCardRepository.findByUser(user);
            Map<String, UserCard> currentCardMap = currentCollection.stream()
                    .collect(Collectors.toMap(
                            c -> c.getSetCode() + "_" + c.getCollectorNumber() + "_" + c.isFoil(),
                            c -> c,
                            (a, b) -> a
                    ));

            Map<String, UserCard> importedCardMap = importedCards.stream()
                    .collect(Collectors.toMap(
                            c -> c.getSetCode() + "_" + c.getCollectorNumber() + "_" + c.isFoil(),
                            c -> c,
                            (a, b) -> a
                    ));

            Set<String> currentKeys = new HashSet<>(currentCardMap.keySet());
            Set<String> importedKeys = new HashSet<>(importedCardMap.keySet());

            Set<String> removedKeys = new HashSet<>(currentKeys);
            removedKeys.removeAll(importedKeys);

            Set<String> addedKeys = new HashSet<>(importedKeys);
            addedKeys.removeAll(currentKeys);

            List<ImportedCardInfo> removedCards = removedKeys.stream()
                    .map(key -> {
                        UserCard uc = currentCardMap.get(key);
                        return new ImportedCardInfo(uc.getName(), uc.getSetCode(),
                                uc.getCollectorNumber(), uc.getQuantity(), uc.isFoil());
                    })
                    .collect(Collectors.toList());

            List<ImportedCardInfo> addedCards = addedKeys.stream()
                    .map(key -> {
                        UserCard uc = importedCardMap.get(key);
                        return new ImportedCardInfo(uc.getName(), uc.getSetCode(),
                                uc.getCollectorNumber(), uc.getQuantity(), uc.isFoil());
                    })
                    .collect(Collectors.toList());

            result.setRemovedCardsCount(removedCards.size());
            result.setAddedCardsCount(addedCards.size());
            result.setRemovedCards(removedCards);
            result.setAddedCards(addedCards);

            saveUserCards(user, importedCards);

            // Extract and persist user's physical decks from folder names
            userDeckService.buildAndSaveDecks(user, importedCards);

            importedCards.forEach(card -> importedSetCodes.add(card.getSetCode()));

            // Fetch Scryfall data and detect unknown set codes
            List<String> unknownSetCodes = new ArrayList<>();
            List<ScryfallCard> sfCards = new ArrayList<>();
            for (String setCode : importedSetCodes) {
                scryfallService.getCardsBySet(setCode, null);
                List<ScryfallCard> fetched = scryfallCardRepository.findBySetCode(setCode);
                if (fetched.isEmpty()) {
                    unknownSetCodes.add(setCode);
                    log.warn("Unknown set code (no Scryfall data): {}", setCode);
                }
                sfCards.addAll(fetched);
            }
            result.setUnknownSetCodes(unknownSetCodes);

            Map<String, ScryfallCard> cardMap = sfCards.stream()
                    .collect(Collectors.toMap(
                            c -> c.getSetCode() + "_" + c.getCollectorNumber(),
                            c -> c,
                            (a, b) -> a
                    ));

            for (UserCard uc : importedCards) {
                String key = uc.getSetCode() + "_" + uc.getCollectorNumber() + "_" + uc.isFoil();
                if (!addedKeys.contains(key)) continue; // only truly new cards
                ScryfallCard sfCard = cardMap.get(uc.getSetCode() + "_" + uc.getCollectorNumber());
                if (sfCard != null) {
                    CardWithUserData cwu = new CardWithUserData(
                            sfCard,
                            uc.isFoil() ? 0 : uc.getQuantity(),
                            uc.isFoil() ? uc.getQuantity() : 0
                    );
                    newCards.add(cwu);
                } else {
                    log.debug("No Scryfall card found for {}_{}", uc.getSetCode(), uc.getCollectorNumber());
                }
            }

            result.setNewCardsCount(importedCards.size()); // = unique imported cards (shown as "Unique Cards" stat)
            result.setNewCards(newCards);
            result.setErrors(errors);

            ImportHistory history = new ImportHistory();
            history.setUser(user);
            history.setFormat("inventory");
            history.setTotalCardsCount(cardsCount);
            history.setUniqueCardsCount(importedCards.size());
            history.setAddedCards(addedCards);
            history.setRemovedCards(removedCards);

            // Persist duplicate-row warnings
            List<ImportHistory.DuplicateRowInfo> histDuplicates = parseResult.duplicates.stream()
                    .map(d -> new ImportHistory.DuplicateRowInfo(
                            d.getFolder(), d.getCardName(), d.getSetCode(),
                            d.getCollectorNumber(), d.isFoil(), d.getOccurrences()))
                    .collect(Collectors.toList());
            history.setDuplicatesRemoved(histDuplicates);
            history.setUnknownSetCodes(new ArrayList<>(unknownSetCodes));

            importHistoryRepository.save(history);

        } catch (Exception e) {
            log.error("Inventory import failed", e);
            errors.add("Import failed: " + e.getMessage());
            result.setErrors(errors);
        }

        return result;
    }

    ParseResult parseInventoryFile(MultipartFile file) throws IOException, CsvValidationException {
        Map<String, UserCard>     cardMap         = new LinkedHashMap<>();
        // Raw-line duplicate tracking
        Set<String>               seenRawLines    = new LinkedHashSet<>();
        Map<String, Integer>      duplicateCounts = new LinkedHashMap<>();
        Map<String, UserCard>     firstCards      = new LinkedHashMap<>();

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String[] line;
            int lineNum = 0;
            boolean skipHeader = true;

            while ((line = reader.readNext()) != null) {
                lineNum++;

                if (line.length == 0) continue;

                if (line[0] != null && line[0].startsWith("sep=")) {
                    skipHeader = true;
                    continue;
                }

                if (skipHeader) {
                    skipHeader = false;
                    continue;
                }

                // ── Exact-duplicate detection ────────────────────────────────
                String rawKey = buildRawKey(line);
                if (seenRawLines.contains(rawKey)) {
                    duplicateCounts.merge(rawKey, 1, Integer::sum);
                    log.debug("Exact duplicate line {} skipped (rawKey={})", lineNum, rawKey);
                    continue;
                }
                seenRawLines.add(rawKey);

                // ── Normal parse + folder-aware dedup ────────────────────────
                UserCard card = parseInventoryLine(line, lineNum);
                if (card != null) {
                    firstCards.put(rawKey, card);
                    String key = (card.getFolderName() != null ? card.getFolderName() : "")
                            + "_" + card.getSetCode()
                            + "_" + card.getCollectorNumber()
                            + "_" + card.isFoil();
                    if (cardMap.containsKey(key)) {
                        cardMap.get(key).setQuantity(cardMap.get(key).getQuantity() + card.getQuantity());
                    } else {
                        cardMap.put(key, card);
                    }
                }
            }
        }

        // Build DuplicateInfo list
        List<DuplicateInfo> duplicates = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : duplicateCounts.entrySet()) {
            UserCard fc = firstCards.get(entry.getKey());
            if (fc != null) {
                duplicates.add(new DuplicateInfo(
                        fc.getFolderName() != null ? fc.getFolderName() : "",
                        fc.getName(),
                        fc.getSetCode(),
                        fc.getCollectorNumber(),
                        fc.isFoil(),
                        entry.getValue()
                ));
            }
        }

        if (!duplicates.isEmpty()) {
            log.info("{} distinct exact-duplicate row(s) removed from import CSV", duplicates.size());
        }

        return new ParseResult(new ArrayList<>(cardMap.values()), duplicates);
    }

    /** Normalises a raw CSV fields array into a stable string key for exact-duplicate detection. */
    private static String buildRawKey(String[] fields) {
        return Arrays.stream(fields)
                .map(s -> s == null ? "" : s.trim())
                .collect(Collectors.joining("\t"));
    }

    private UserCard parseInventoryLine(String[] fields, int lineNum) {
        try {
            if (fields.length < 9) {
                log.warn("Line {} has too few fields: {}", lineNum, fields.length);
                return null;
            }

            String cardName = fields[3].trim();
            String setCode = fields[4].trim().toLowerCase();
            setCode = mapSetCode(setCode);
            String cardNumber = fields[6].trim();
            
            int quantity = 1;
            try {
                String qtyStr = fields[1].trim();
                if (!qtyStr.isEmpty()) {
                    quantity = Integer.parseInt(qtyStr);
                }
            } catch (NumberFormatException e) {
                quantity = 1;
            }
            
            String printing = fields[8].trim();
            // DragonShield exports many foil variants: "Foil", "Surge Foil", "Galaxy Foil", etc.
            boolean isFoil = printing.toLowerCase().contains("foil");

            if (cardName.isEmpty() || setCode.isEmpty()) {
                log.warn("Line {} has empty card name or set code", lineNum);
                return null;
            }

            UserCard card = new UserCard();
            card.setName(cardName);
            card.setSetCode(setCode);
            card.setCollectorNumber(cardNumber.replaceAll("[^0-9a-zA-Z]", ""));
            card.setQuantity(quantity);
            card.setFoil(isFoil);
            card.setFolderName(fields[0].trim()); // Folder Name column

            return card;
        } catch (Exception e) {
            log.warn("Failed to parse line {}: {}", lineNum, e.getMessage());
            return null;
        }
    }

    private void saveUserCards(String user, List<UserCard> cards) {
        userCardRepository.deleteByUser(user);
        cards.forEach(card -> card.setUser(user));
        userCardRepository.saveAll(cards);
    }

    private String mapSetCode(String setCode) {
        Map<String, String> setMappings = Map.of(
            "fwb", "3ed"
        );
        return setMappings.getOrDefault(setCode, setCode);
    }
}
