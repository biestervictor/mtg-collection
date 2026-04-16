package com.mtg.collection.service;

import com.mtg.collection.dto.CardWithUserData;
import com.mtg.collection.dto.ImportResult;
import com.mtg.collection.model.ImportHistory;
import com.mtg.collection.model.ImportHistory.ImportedCardInfo;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.ImportHistoryRepository;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.UserCardRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CollectionService {

    private static final Logger log = LoggerFactory.getLogger(CollectionService.class);

    private final UserCardRepository userCardRepository;
    private final ScryfallCardRepository scryfallCardRepository;
    private final ScryfallService scryfallService;
    private final ImportHistoryRepository importHistoryRepository;
    private final UserDeckService userDeckService;

    public CollectionService(UserCardRepository userCardRepository,
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

    public List<UserCard> getUserCardsBySet(String user, String setCode) {
        return userCardRepository.findByUserAndSetCode(user, setCode);
    }

    public List<UserCard> getAllUserCards(String user) {
        return userCardRepository.findByUser(user);
    }

    public void saveUserCards(String user, List<UserCard> cards) {
        userCardRepository.deleteByUser(user);
        cards.forEach(card -> card.setUser(user));
        userCardRepository.saveAll(cards);
    }

    public void deleteUserData(String user) {
        userCardRepository.deleteByUser(user);
        userDeckService.deleteDecksForUser(user);
        importHistoryRepository.deleteByUser(user);
        log.info("Deleted all data for user '{}'", user);
    }

    public ImportResult importCards(String user, MultipartFile file, String format) {
        ImportResult result = new ImportResult();
        List<String> errors = new ArrayList<>();
        List<CardWithUserData> newCards = new ArrayList<>();

        try {
            List<UserCard> importedCards = parseCsvFile(file, format);
            int cardsCount = importedCards.stream().mapToInt(UserCard::getQuantity).sum();
            result.setCardsCount(cardsCount);

            List<UserCard> currentCollection = getAllUserCards(user);
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

            List<UserCard> newImportedCards = importedCards.stream()
                    .filter(card -> !currentCardMap.containsKey(
                            card.getSetCode() + "_" + card.getCollectorNumber() + "_" + card.isFoil()))
                    .collect(Collectors.toList());

            saveUserCards(user, importedCards);

            // Extract and persist user's physical decks from folder names
            userDeckService.buildAndSaveDecks(user, importedCards);

            Set<String> importedSetCodes = importedCards.stream()
                    .map(UserCard::getSetCode)
                    .collect(Collectors.toSet());

            for (String setCode : importedSetCodes) {
                scryfallService.getCardsBySet(setCode, null);
            }

            List<ScryfallCard> scryfallCards = new ArrayList<>();
            for (String setCode : importedSetCodes) {
                scryfallCards.addAll(scryfallCardRepository.findBySetCode(setCode));
            }

            newImportedCards.forEach(newCard -> {
                ScryfallCard sfCard = scryfallCards.stream()
                        .filter(c -> c.getSetCode().equals(newCard.getSetCode()) &&
                                    c.getCollectorNumber().equals(newCard.getCollectorNumber()))
                        .findFirst()
                        .orElse(null);

                if (sfCard != null) {
                    CardWithUserData cardWithData = new CardWithUserData(
                            sfCard,
                            newCard.isFoil() ? 0 : newCard.getQuantity(),
                            newCard.isFoil() ? newCard.getQuantity() : 0
                    );
                    newCards.add(cardWithData);
                }
            });

            result.setNewCardsCount(newCards.size());
            result.setNewCards(newCards);
            result.setErrors(errors);

            ImportHistory history = new ImportHistory();
            history.setUser(user);
            history.setFormat(format);
            history.setTotalCardsCount(cardsCount);
            history.setUniqueCardsCount(importedCards.size());
            history.setAddedCards(addedCards);
            history.setRemovedCards(removedCards);
            importHistoryRepository.save(history);

        } catch (Exception e) {
            log.error("Import failed", e);
            errors.add("Import failed: " + e.getMessage());
            result.setErrors(errors);
        }

        return result;
    }

    private List<UserCard> parseCsvFile(MultipartFile file, String format) throws Exception {
        List<UserCard> cards = new ArrayList<>();

        BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));

        CSVFormat csvFormat = CSVFormat.DEFAULT;
        if ("dragonshield_web".equals(format)) {
            // DragonShield web exports may start with an Excel "sep=," artifact line
            // before the real header.  Peek at the first line and skip it when present.
            bufferedReader.mark(512);
            String firstLine = bufferedReader.readLine();
            if (firstLine == null || !firstLine.trim().startsWith("sep=")) {
                bufferedReader.reset(); // not a sep-directive — put it back
            }
            // The next line is now the real CSV header; use auto-header detection.
            csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .build();
        }

        try (CSVParser csvParser = new CSVParser(bufferedReader, csvFormat)) {
            for (CSVRecord record : csvParser) {
                UserCard card = mapRecordToCard(record, format);
                if (card != null) {
                    cards.add(card);
                }
            }
        }

        return cards;
    }

    private UserCard mapRecordToCard(CSVRecord record, String format) {
        try {
            UserCard card = new UserCard();
            
            if ("dragonshield_web".equals(format)) {
                card.setName(record.get("Card Name"));
                card.setQuantity(Integer.parseInt(record.get("Quantity")));
                card.setSetCode(fixSet(record.get("Set Code").toLowerCase()));
                card.setCollectorNumber(fixCollectorNumber(record.get("Card Number")));
                card.setFoil("Foil".equals(record.get("Printing")));
                card.setFolderName(safeGet(record, "Folder Name"));
            } else if ("dragonshield_app".equals(format)) {
                card.setName(record.get("Name"));
                card.setQuantity(Integer.parseInt(record.get("Quantity")));
                card.setSetCode(fixSet(record.get(" Expansion Code").toLowerCase()));
                card.setCollectorNumber(record.get(" CardNumber"));
                card.setFoil("true".equals(record.get(" Foil")));
                // DragonShield app may use " Folder Name" (with leading space) or "Folder Name"
                String folder = safeGet(record, " Folder Name");
                if (folder == null) folder = safeGet(record, "Folder Name");
                card.setFolderName(folder);
            }

            if (card.getName() == null || card.getName().isEmpty()) {
                return null;
            }

            return card;
        } catch (Exception e) {
            log.warn("Failed to parse card record: {}", e.getMessage());
            return null;
        }
    }

    private String fixSet(String setCode) {
        if ("mom".equals(setCode)) return "mat";
        if ("mom".equals(setCode)) return "mat";
        return setCode;
    }

    private String fixCollectorNumber(String collectorNumber) {
        return collectorNumber.replaceAll("[^0-9]", "");
    }

    /** Returns null instead of throwing if the column doesn't exist in the record. */
    private String safeGet(CSVRecord record, String columnName) {
        try {
            return record.get(columnName);
        } catch (Exception e) {
            return null;
        }
    }

    public List<CardWithUserData> getCardsWithUserData(String user, String setCode, List<String> filters) {
        List<ScryfallCard> sfCards = scryfallService.getCardsBySet(setCode, filters);
        List<UserCard> userCards = getUserCardsBySet(user, setCode);

        Map<String, UserCard> userCardMap = new HashMap<>();
        for (UserCard uc : userCards) {
            String key = uc.getCollectorNumber() + "_" + uc.isFoil();
            UserCard existing = userCardMap.get(key);
            if (existing != null) {
                int qty = uc.getQuantity() < 0 ? 0 : uc.getQuantity();
                existing.setQuantity(existing.getQuantity() + qty);
            } else {
                userCardMap.put(key, uc);
            }
        }

        List<CardWithUserData> result = new ArrayList<>();
        for (ScryfallCard sfCard : sfCards) {
            UserCard regularUserCard = userCardMap.get(sfCard.getCollectorNumber() + "_false");
            UserCard foilUserCard = userCardMap.get(sfCard.getCollectorNumber() + "_true");

            int quantity = regularUserCard != null ? Math.max(0, regularUserCard.getQuantity()) : 0;
            int foilQuantity = foilUserCard != null ? Math.max(0, foilUserCard.getQuantity()) : 0;

            result.add(new CardWithUserData(sfCard, quantity, foilQuantity));
        }

        return result;
    }
}
