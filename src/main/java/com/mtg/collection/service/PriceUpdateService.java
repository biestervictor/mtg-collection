package com.mtg.collection.service;

import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.UserCardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PriceUpdateService {

    private static final Logger log = LoggerFactory.getLogger(PriceUpdateService.class);

    private final UserCardRepository userCardRepository;
    private final ScryfallService scryfallService;

    public PriceUpdateService(UserCardRepository userCardRepository, ScryfallService scryfallService) {
        this.userCardRepository = userCardRepository;
        this.scryfallService = scryfallService;
    }

    /** Nightly scheduler – runs the update and discards the stats. */
    @Scheduled(cron = "0 0 2 * * ?")
    public void updatePricesForAllUsers() {
        log.info("Starting nightly price update for all users");
        try {
            runUpdateForAllUsers();
        } catch (Exception e) {
            log.error("Error during nightly price update", e);
        }
    }

    /**
     * Full manual update:
     * <ol>
     *   <li>Refresh Scryfall cache prices for every set the users own cards from
     *       (targeted — much faster than updating all sets).</li>
     *   <li>Propagate the updated cache prices to each user's {@code UserCard} records.</li>
     * </ol>
     *
     * @return map of results with keys {@code totalUpdated} and {@code perUser}
     */
    public Map<String, Object> runFullUpdate() {
        log.info("Running full price update (targeted Scryfall fetch + propagation)");

        Set<String> ownedSetCodes = userCardRepository.findAll().stream()
                .map(c -> c.getSetCode().toLowerCase())
                .collect(Collectors.toSet());
        log.info("Refreshing Scryfall prices for {} owned sets", ownedSetCodes.size());
        scryfallService.updatePricesForSets(ownedSetCodes);

        Map<String, Integer> perUser = runUpdateForAllUsers();
        int total = perUser.values().stream().mapToInt(Integer::intValue).sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUpdated", total);
        result.put("perUser", perUser);
        return result;
    }

    /**
     * Executes the price-propagation from the Scryfall cache to every user's
     * UserCard records and returns how many cards were updated per user.
     */
    public Map<String, Integer> runUpdateForAllUsers() {
        List<String> users = userCardRepository.findAll().stream()
                .map(UserCard::getUser)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        Map<String, Integer> result = new LinkedHashMap<>();
        for (String user : users) {
            result.put(user, updatePricesForUser(user));
        }
        log.info("Completed price update for {} users: {}", users.size(), result);
        return result;
    }

    /**
     * Propagates Scryfall cache prices to a single user's cards.
     *
     * @return number of UserCard records that were actually changed
     */
    public int updatePricesForUser(String user) {
        log.info("Updating prices for user: {}", user);

        List<UserCard> userCards = userCardRepository.findByUser(user);

        List<ScryfallCard> allScryfallCards = scryfallService.getAllCards(false);
        Map<String, ScryfallCard> scryfallMap = allScryfallCards.stream()
                .collect(Collectors.toMap(
                        c -> c.getSetCode() + "_" + c.getCollectorNumber(),
                        c -> c,
                        (a, b) -> a
                ));

        int updated = 0;
        for (UserCard card : userCards) {
            String key = card.getSetCode() + "_" + card.getCollectorNumber();
            ScryfallCard sfCard = scryfallMap.get(key);

            if (sfCard != null) {
                double price = card.isFoil()
                        ? (sfCard.getPriceFoil() != null ? sfCard.getPriceFoil() : 0.0)
                        : (sfCard.getPriceRegular() != null ? sfCard.getPriceRegular() : 0.0);

                if (Math.abs(card.getPrice() - price) > 0.01) {
                    card.setPrice(price);
                    card.setPriceUpdatedAt(LocalDate.now());
                    userCardRepository.save(card);
                    updated++;
                }
            }
        }

        log.info("Updated {} prices for user: {}", updated, user);
        return updated;
    }
}
