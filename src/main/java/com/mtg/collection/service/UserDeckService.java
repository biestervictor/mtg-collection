package com.mtg.collection.service;

import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.UserDeck;
import com.mtg.collection.model.UserDeck.DeckCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.ScryfallCardRepository;
import com.mtg.collection.repository.UserDeckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses DragonShield Folder Name convention and builds UserDeck documents.
 *
 * Folder Name prefixes:
 *   MB_<name>       → mainboard of a regular deck
 *   SB_<name>       → sideboard of a regular deck
 *   EB_<name>       → extra board of a regular deck
 *   MB_CM_<name>    → mainboard of a commander deck
 *   anything else   → ignored (treated as loose collection)
 */
@Service
public class UserDeckService {

    private static final Logger log = LoggerFactory.getLogger(UserDeckService.class);

    private final UserDeckRepository       userDeckRepository;
    private final ScryfallCardRepository   scryfallCardRepository;

    public UserDeckService(UserDeckRepository userDeckRepository,
                           ScryfallCardRepository scryfallCardRepository) {
        this.userDeckRepository     = userDeckRepository;
        this.scryfallCardRepository = scryfallCardRepository;
    }

    /**
     * Parses folder names from imported cards, builds UserDeck objects and
     * replaces all existing decks for the given user.
     */
    public void buildAndSaveDecks(String user, List<UserCard> cards) {
        Map<String, UserDeck> deckMap = new LinkedHashMap<>();
        // key: deckName -> board -> cardKey -> DeckCard  (for deduplication)
        Map<String, Map<Board, Map<String, DeckCard>>> boardCardMaps = new LinkedHashMap<>();

        for (UserCard card : cards) {
            String folder = card.getFolderName();
            if (folder == null || folder.isBlank()) continue;

            ParsedFolder pf = parseFolder(folder);
            if (pf == null) continue;

            UserDeck deck = deckMap.computeIfAbsent(pf.deckName, name -> {
                UserDeck d = new UserDeck();
                d.setId(user + "_" + name);
                d.setUser(user);
                d.setName(name);
                d.setCommander(pf.commander);
                d.setUpdatedAt(LocalDate.now());
                return d;
            });

            if (pf.commander) deck.setCommander(true);

            // Deduplicate: merge rows with same set+number+foil by summing quantity
            String cardKey = card.getSetCode().toLowerCase() + "_"
                    + card.getCollectorNumber() + "_"
                    + card.isFoil();
            Map<String, DeckCard> cardMap = boardCardMaps
                    .computeIfAbsent(pf.deckName, k -> new EnumMap<>(Board.class))
                    .computeIfAbsent(pf.board, k -> new LinkedHashMap<>());

            if (cardMap.containsKey(cardKey)) {
                DeckCard existing = cardMap.get(cardKey);
                existing.setQuantity(existing.getQuantity() + card.getQuantity());
            } else {
                DeckCard dc = new DeckCard(
                        card.getName(),
                        card.getSetCode(),
                        card.getCollectorNumber(),
                        card.getQuantity(),
                        card.isFoil()
                );
                cardMap.put(cardKey, dc);
                switch (pf.board) {
                    case MAINBOARD  -> deck.getMainboard().add(dc);
                    case SIDEBOARD  -> deck.getSideboard().add(dc);
                    case EXTRABOARD -> deck.getExtraboard().add(dc);
                }
            }
        }

        if (deckMap.isEmpty()) {
            log.info("No deck folders found for user '{}', skipping deck save", user);
            return;
        }

        // Enrich with Scryfall thumbnails & prices, then aggregate by card name
        enrichAndAggregate(deckMap.values());

        userDeckRepository.deleteByUser(user);
        userDeckRepository.saveAll(deckMap.values());
        log.info("Saved {} deck(s) for user '{}'", deckMap.size(), user);
    }

    public List<UserDeck> getDecksForUser(String user) {
        return userDeckRepository.findByUserOrderByCommanderDescNameAsc(user);
    }

    public Optional<UserDeck> getDeckById(String id) {
        return userDeckRepository.findById(id);
    }

    public void deleteDecksForUser(String user) {
        userDeckRepository.deleteByUser(user);
    }

    /**
     * Re-runs Scryfall enrichment on all existing decks for the user (thumbnail, image, price).
     * Useful when decks were saved before enrichment was added, or after a Scryfall data refresh.
     */
    public int reEnrichDecks(String user) {
        List<UserDeck> decks = userDeckRepository.findByUserOrderByCommanderDescNameAsc(user);
        if (decks.isEmpty()) {
            log.info("No decks found for user '{}', nothing to re-enrich", user);
            return 0;
        }
        enrichAndAggregate(decks);
        userDeckRepository.saveAll(decks);
        log.info("Re-enriched {} deck(s) for user '{}'", decks.size(), user);
        return decks.size();
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Looks up ScryfallCard for every DeckCard (batch by set) and sets thumbnailUrl/imageUrl/price.
     * Quantities are preserved exactly as imported — no aggregation by card name.
     */
    private void enrichAndAggregate(Collection<UserDeck> decks) {
        // Collect set codes (normalised to lower-case to match Scryfall storage)
        Set<String> setCodes = decks.stream()
                .flatMap(d -> allCards(d).stream())
                .map(DeckCard::getSetCode)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        // Batch load: one query per set code; key = "setCode_collectorNumber" (lower-case)
        Map<String, ScryfallCard> sfMap = new HashMap<>();
        for (String sc : setCodes) {
            for (ScryfallCard sf : scryfallCardRepository.findBySetCode(sc)) {
                sfMap.put(sf.getSetCode().toLowerCase() + "_" + sf.getCollectorNumber(), sf);
            }
        }

        for (UserDeck deck : decks) {
            deck.setMainboard(enrich(deck.getMainboard(), sfMap));
            deck.setSideboard(enrich(deck.getSideboard(), sfMap));
            deck.setExtraboard(enrich(deck.getExtraboard(), sfMap));
        }
    }

    /**
     * Enriches each DeckCard with Scryfall thumbnail/price.
     * Quantities are kept as-is from the import CSV — no summing across printings.
     * Cards are sorted by name for display.
     */
    private List<DeckCard> enrich(List<DeckCard> board, Map<String, ScryfallCard> sfMap) {
        for (DeckCard dc : board) {
            String key = dc.getSetCode().toLowerCase() + "_" + dc.getCollectorNumber();
            ScryfallCard sf = sfMap.get(key);
            if (sf != null) {
                dc.setThumbnailUrl(sf.getThumbnailFront());
                dc.setImageUrl(sf.getImageFront());
                double p = dc.isFoil()
                        ? (sf.getPriceFoil()    != null ? sf.getPriceFoil()    : 0.0)
                        : (sf.getPriceRegular() != null ? sf.getPriceRegular() : 0.0);
                dc.setPrice(p);
            }
        }
        return board.stream()
                .sorted(Comparator.comparing(DeckCard::getName))
                .collect(Collectors.toList());
    }

    private List<DeckCard> allCards(UserDeck deck) {
        List<DeckCard> all = new ArrayList<>();
        all.addAll(deck.getMainboard());
        all.addAll(deck.getSideboard());
        all.addAll(deck.getExtraboard());
        return all;
    }

    private enum Board { MAINBOARD, SIDEBOARD, EXTRABOARD }

    private record ParsedFolder(String deckName, Board board, boolean commander) {}

    private ParsedFolder parseFolder(String folder) {
        String f = folder.trim();
        if (f.startsWith("MB_CM_")) {
            String name = f.substring("MB_CM_".length()).trim();
            return name.isEmpty() ? null : new ParsedFolder(name, Board.MAINBOARD, true);
        }
        if (f.startsWith("MB_")) {
            String name = f.substring("MB_".length()).trim();
            return name.isEmpty() ? null : new ParsedFolder(name, Board.MAINBOARD, false);
        }
        if (f.startsWith("SB_")) {
            String name = f.substring("SB_".length()).trim();
            return name.isEmpty() ? null : new ParsedFolder(name, Board.SIDEBOARD, false);
        }
        if (f.startsWith("EB_")) {
            String name = f.substring("EB_".length()).trim();
            return name.isEmpty() ? null : new ParsedFolder(name, Board.EXTRABOARD, false);
        }
        return null;
    }
}
