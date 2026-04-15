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

            DeckCard dc = new DeckCard(
                    card.getName(),
                    card.getSetCode(),
                    card.getCollectorNumber(),
                    card.getQuantity(),
                    card.isFoil()
            );

            switch (pf.board) {
                case MAINBOARD  -> deck.getMainboard().add(dc);
                case SIDEBOARD  -> deck.getSideboard().add(dc);
                case EXTRABOARD -> deck.getExtraboard().add(dc);
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

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Looks up ScryfallCard for every DeckCard (batch by set), sets thumbnailUrl/imageUrl/price,
     * then aggregates each board by card name: sum quantities, keep most expensive thumbnail.
     */
    private void enrichAndAggregate(Collection<UserDeck> decks) {
        // Collect all set codes across all decks
        Set<String> setCodes = decks.stream()
                .flatMap(d -> allCards(d).stream())
                .map(DeckCard::getSetCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Batch load: one query per set code
        Map<String, ScryfallCard> sfMap = new HashMap<>();
        for (String sc : setCodes) {
            for (ScryfallCard sf : scryfallCardRepository.findBySetCode(sc)) {
                sfMap.put(sf.getSetCode() + "_" + sf.getCollectorNumber(), sf);
            }
        }

        for (UserDeck deck : decks) {
            deck.setMainboard(enrichAndAggregate(deck.getMainboard(), sfMap));
            deck.setSideboard(enrichAndAggregate(deck.getSideboard(), sfMap));
            deck.setExtraboard(enrichAndAggregate(deck.getExtraboard(), sfMap));
        }
    }

    /**
     * For a single board:
     * 1. Enrich each DeckCard with Scryfall thumbnail/price.
     * 2. Aggregate by card name: sum quantities, keep the most expensive printing's images.
     */
    private List<DeckCard> enrichAndAggregate(List<DeckCard> board,
                                               Map<String, ScryfallCard> sfMap) {
        // Enrich
        for (DeckCard dc : board) {
            ScryfallCard sf = sfMap.get(dc.getSetCode() + "_" + dc.getCollectorNumber());
            if (sf != null) {
                dc.setThumbnailUrl(sf.getThumbnailFront());
                dc.setImageUrl(sf.getImageFront());
                double p = dc.isFoil()
                        ? (sf.getPriceFoil() != null ? sf.getPriceFoil() : 0.0)
                        : (sf.getPriceRegular() != null ? sf.getPriceRegular() : 0.0);
                dc.setPrice(p);
            }
        }

        // Aggregate by card name: keep most expensive thumbnail, sum quantities
        Map<String, DeckCard> byName = new LinkedHashMap<>();
        for (DeckCard dc : board) {
            DeckCard existing = byName.get(dc.getName());
            if (existing == null) {
                byName.put(dc.getName(), dc);
            } else {
                // merge quantities
                existing.setQuantity(existing.getQuantity() + dc.getQuantity());
                // keep more expensive thumbnail
                if (dc.getPrice() > existing.getPrice()) {
                    existing.setThumbnailUrl(dc.getThumbnailUrl());
                    existing.setImageUrl(dc.getImageUrl());
                    existing.setSetCode(dc.getSetCode());
                    existing.setPrice(dc.getPrice());
                }
            }
        }

        // Sort by name
        return byName.values().stream()
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
