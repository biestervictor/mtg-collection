package com.mtg.collection.service;

import com.mtg.collection.model.UserDeck;
import com.mtg.collection.model.UserDeck.DeckCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.UserDeckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

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

    private final UserDeckRepository userDeckRepository;

    public UserDeckService(UserDeckRepository userDeckRepository) {
        this.userDeckRepository = userDeckRepository;
    }

    /**
     * Parses folder names from imported cards, builds UserDeck objects and
     * replaces all existing decks for the given user.
     *
     * @param user  the owner
     * @param cards imported cards that may carry a folderName (transient field)
     */
    public void buildAndSaveDecks(String user, List<UserCard> cards) {
        // key: deckName, value: in-progress UserDeck
        Map<String, UserDeck> deckMap = new LinkedHashMap<>();

        for (UserCard card : cards) {
            String folder = card.getFolderName();
            if (folder == null || folder.isBlank()) continue;

            ParsedFolder pf = parseFolder(folder);
            if (pf == null) continue; // not a deck folder

            UserDeck deck = deckMap.computeIfAbsent(pf.deckName, name -> {
                UserDeck d = new UserDeck();
                d.setId(user + "_" + name);
                d.setUser(user);
                d.setName(name);
                d.setCommander(pf.commander);
                d.setUpdatedAt(LocalDate.now());
                return d;
            });

            // if any folder for this deck name is commander, mark the deck
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

        userDeckRepository.deleteByUser(user);
        userDeckRepository.saveAll(deckMap.values());
        log.info("Saved {} deck(s) for user '{}'", deckMap.size(), user);
    }

    public List<UserDeck> getDecksForUser(String user) {
        return userDeckRepository.findByUserOrderByCommanderDescNameAsc(user);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private enum Board { MAINBOARD, SIDEBOARD, EXTRABOARD }

    private record ParsedFolder(String deckName, Board board, boolean commander) {}

    /**
     * Returns null if the folder name does not match any deck prefix.
     */
    private ParsedFolder parseFolder(String folder) {
        String f = folder.trim();

        // MB_CM_<name>  – must come before MB_ check
        if (f.startsWith("MB_CM_")) {
            String name = f.substring("MB_CM_".length()).trim();
            if (name.isEmpty()) return null;
            return new ParsedFolder(name, Board.MAINBOARD, true);
        }
        if (f.startsWith("MB_")) {
            String name = f.substring("MB_".length()).trim();
            if (name.isEmpty()) return null;
            return new ParsedFolder(name, Board.MAINBOARD, false);
        }
        if (f.startsWith("SB_")) {
            String name = f.substring("SB_".length()).trim();
            if (name.isEmpty()) return null;
            return new ParsedFolder(name, Board.SIDEBOARD, false);
        }
        if (f.startsWith("EB_")) {
            String name = f.substring("EB_".length()).trim();
            if (name.isEmpty()) return null;
            return new ParsedFolder(name, Board.EXTRABOARD, false);
        }

        return null; // e.g. "Sets" or any other non-deck folder
    }
}
