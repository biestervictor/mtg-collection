package com.mtg.collection.controller;

import com.mtg.collection.dto.CardWithUserData;
import com.mtg.collection.dto.TreatmentGroupStat;
import com.mtg.collection.dto.WizardCard;
import com.mtg.collection.dto.WizardGroup;
import com.mtg.collection.model.ScryfallCard;
import com.mtg.collection.model.ScryfallSet;
import com.mtg.collection.model.UserCard;
import com.mtg.collection.repository.UserCardRepository;
import com.mtg.collection.service.CardFilterService;
import com.mtg.collection.service.CollectionService;
import com.mtg.collection.service.ScryfallService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class CollectionController {

    private final CollectionService collectionService;
    private final ScryfallService scryfallService;
    private final CardFilterService cardFilterService;
    private final UserCardRepository userCardRepository;

    public CollectionController(CollectionService collectionService,
                                ScryfallService scryfallService,
                                CardFilterService cardFilterService,
                                UserCardRepository userCardRepository) {
        this.collectionService = collectionService;
        this.scryfallService = scryfallService;
        this.cardFilterService = cardFilterService;
        this.userCardRepository = userCardRepository;
    }

    @GetMapping("/show")
    public String showCollection(Model model,
                                @RequestParam(required = false) String set,
                                @RequestParam(required = false) String user,
                                @RequestParam(required = false, defaultValue = "all") String state,
                                @RequestParam(required = false) String rarity,
                                @RequestParam(required = false) String printing,
                                @RequestParam(required = false) String search,
                                @RequestParam(required = false) String showBasics,
                                @RequestParam(required = false) String frameStyle,
                                @RequestParam(required = false) String showTokens,
                                @RequestParam(required = false) String showPromos) {

        List<ScryfallSet> sets = scryfallService.getAllSets(false);
        model.addAttribute("sets", sets);

        model.addAttribute("state", state);
        model.addAttribute("rarity", rarity);
        model.addAttribute("printing", printing);
        model.addAttribute("search", search);
        model.addAttribute("showBasics", showBasics);
        model.addAttribute("frameStyle", frameStyle);
        model.addAttribute("showTokens", showTokens);
        model.addAttribute("showPromos", showPromos);

        if (set != null && !set.isEmpty() && user != null && !user.isEmpty()) {
            List<CardWithUserData> cards = collectionService.getCardsWithUserData(user, set, null);
            List<CardWithUserData> filteredCards = cardFilterService.filterCards(cards, state, printing, rarity, search, showBasics, frameStyle, null);

            // Sort filtered results by treatment group so Normal comes first, then Showcase, etc.
            List<CardWithUserData> sortedCards = sortByTreatmentGroup(filteredCards);

            // Build per-group stats from the UNFILTERED full-set cards so the divider always
            // shows the set-wide total and missing count, regardless of active filters.
            Map<Integer, TreatmentGroupStat> groupDividers = computeGroupDividers(sortedCards, cards);

            // Load token set (e.g. "ttdm" for "tdm") when "Show Tokens" is active
            List<CardWithUserData> tokenCards = new ArrayList<>();
            if ("true".equals(showTokens)) {
                String tokenSetCode = "t" + set;
                List<ScryfallCard> tokenSetCards = scryfallService.getCardsBySet(tokenSetCode, null);
                if (!tokenSetCards.isEmpty()) {
                    tokenCards = collectionService.getCardsWithUserData(user, tokenSetCode, null);
                }
            }

            // Load promo set (e.g. "pmom" for "mom") when "Show Promos" is active
            List<CardWithUserData> promoCards = new ArrayList<>();
            if ("true".equals(showPromos)) {
                String promoSetCode = "p" + set;
                List<ScryfallCard> promoSetCards = scryfallService.getCardsBySet(promoSetCode, null);
                if (!promoSetCards.isEmpty()) {
                    promoCards = collectionService.getCardsWithUserData(user, promoSetCode, null);
                }
            }

            model.addAttribute("selectedSet", set);
            model.addAttribute("selectedUser", user);
            model.addAttribute("cards", cards);
            model.addAttribute("filteredCards", sortedCards);
            model.addAttribute("groupDividers", groupDividers);
            model.addAttribute("filteredCount", sortedCards.size());
            model.addAttribute("totalCount", cards.size());
            model.addAttribute("tokenCards", tokenCards);
            model.addAttribute("promoCards", promoCards);
        } else {
            model.addAttribute("selectedSet", set);
            model.addAttribute("selectedUser", user);
        }

        return "show";
    }

    @GetMapping("/compare")
    public String compareCollection(Model model,
                                   @RequestParam(required = false) String set,
                                   @RequestParam(required = false) String user,
                                   @RequestParam(required = false) String compareUser) {
        
        List<ScryfallSet> sets = scryfallService.getAllSets(false);
        model.addAttribute("sets", sets);

        if (set != null && !set.isEmpty() && user != null && !user.isEmpty() && 
            compareUser != null && !compareUser.isEmpty()) {
            
            List<CardWithUserData> userCards = collectionService.getCardsWithUserData(user, set, null);
            List<CardWithUserData> compareCards = collectionService.getCardsWithUserData(compareUser, set, null);

            // getCardsWithUserData returns ALL set cards (including qty=0).
            // Only cards actually owned (qty > 0) are relevant for the diff.
            List<CardWithUserData> userOwned = userCards.stream()
                    .filter(c -> c.getQuantity() > 0 || c.getFoilQuantity() > 0)
                    .collect(Collectors.toList());
            List<CardWithUserData> compareOwned = compareCards.stream()
                    .filter(c -> c.getQuantity() > 0 || c.getFoilQuantity() > 0)
                    .collect(Collectors.toList());

            List<CardWithUserData> onlyUser = cardFilterService.getOnlyInLeft(userOwned, compareOwned);
            List<CardWithUserData> onlyCompare = cardFilterService.getOnlyInLeft(compareOwned, userOwned);
            
            model.addAttribute("selectedSet", set);
            model.addAttribute("compareUser", compareUser);
            model.addAttribute("onlyUser", onlyUser);
            model.addAttribute("onlyCompare", onlyCompare);
        }

        return "compare";
    }
    
    @PostMapping("/api/cache/clear")
    public String clearCache(@RequestParam(required = false) String setCode) {
        if (setCode != null && !setCode.isEmpty()) {
            scryfallService.clearCache(setCode);
            return "redirect:/show?set=" + setCode;
        } else {
            scryfallService.clearAllCache();
            return "redirect:/show";
        }
    }

    @GetMapping("/api/set/{setCode}/top-cards")
    @ResponseBody
    public Map<String, List<Map<String, Object>>> getTopCardsForSet(
            @PathVariable String setCode,
            @RequestParam(required = false) String user) {

        List<ScryfallCard> allCards = scryfallService.getCardsBySet(setCode, null);

        // Build ownership map (collectorNumber → qty) when a user is supplied
        Map<String, Integer> ownedRegular = new HashMap<>();
        Map<String, Integer> ownedFoil    = new HashMap<>();
        if (user != null && !user.isBlank()) {
            for (UserCard uc : userCardRepository.findByUserAndSetCode(user, setCode)) {
                if (uc.isFoil()) {
                    ownedFoil.merge(uc.getCollectorNumber(), Math.max(0, uc.getQuantity()), Integer::sum);
                } else {
                    ownedRegular.merge(uc.getCollectorNumber(), Math.max(0, uc.getQuantity()), Integer::sum);
                }
            }
        }

        String[] rarities = {"mythic", "rare", "uncommon", "common"};
        Map<String, List<ScryfallCard>> byRarity = new LinkedHashMap<>();

        for (String rarity : rarities) {
            final String r = rarity;
            List<ScryfallCard> group = allCards.stream()
                    .filter(c -> r.equalsIgnoreCase(c.getRarity()))
                    .filter(c -> c.getPriceRegular() != null || c.getPriceFoil() != null)
                    .sorted((a, b) -> Double.compare(maxCardPrice(b), maxCardPrice(a)))
                    .collect(Collectors.toList());
            byRarity.put(rarity, group);
        }

        // If a lower rarity's top 10 are all < 10 cents → give +5 extra slots to the next higher rarity
        int[] limits = {10, 10, 10, 10};
        for (int i = rarities.length - 1; i >= 1; i--) {
            List<ScryfallCard> top10 = byRarity.get(rarities[i]).stream().limit(10).collect(Collectors.toList());
            boolean allCheap = !top10.isEmpty() && top10.stream().allMatch(c -> maxCardPrice(c) < 0.10);
            if (allCheap) limits[i - 1] += 5;
        }

        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (int i = 0; i < rarities.length; i++) {
            final int limit = limits[i];
            String rarity = rarities[i];
            List<Map<String, Object>> cardList = byRarity.get(rarity).stream()
                    .limit(limit)
                    .map(c -> {
                        int qtyReg  = ownedRegular.getOrDefault(c.getCollectorNumber(), 0);
                        int qtyFoil = ownedFoil.getOrDefault(c.getCollectorNumber(), 0);
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name",            c.getName());
                        m.put("thumbnail",        c.getThumbnailFront());
                        m.put("priceRegular",     c.getPriceRegular());
                        m.put("priceFoil",        c.getPriceFoil());
                        m.put("qtyRegular",       qtyReg);
                        m.put("qtyFoil",          qtyFoil);
                        m.put("tradableRegular",  Math.max(0, qtyReg  - 1));
                        m.put("tradableFoil",     Math.max(0, qtyFoil - 1));
                        return m;
                    })
                    .collect(Collectors.toList());
            result.put(rarity, cardList);
        }
        return result;
    }

    private double maxCardPrice(ScryfallCard c) {
        double r = c.getPriceRegular() != null ? c.getPriceRegular() : 0;
        double f = c.getPriceFoil() != null ? c.getPriceFoil() : 0;
        return Math.max(r, f);
    }

    // ── Missing Card Wizard ──────────────────────────────────────────────────

    /**
     * Returns missing cards for {@code user} in {@code set}, grouped by treatment
     * category (Normal / Showcase / …).  For each card the response includes the
     * Scryfall price and a list of other app-users who own ≥ 2 copies (i.e. 1
     * tradable copy).
     */
    @GetMapping("/api/wizard")
    @ResponseBody
    public List<WizardGroup> getMissingWizard(
            @RequestParam String set,
            @RequestParam String user) {

        // 1. Full card list for this user + set (owned & missing)
        List<CardWithUserData> allCards = collectionService.getCardsWithUserData(user, set, null);

        // 2. Keep only cards the user does NOT own at all
        List<CardWithUserData> missing = allCards.stream()
                .filter(c -> c.getQuantity() == 0 && c.getFoilQuantity() == 0)
                .collect(Collectors.toList());

        // 3. Build tradable map: collectorNumber → set of other users with qty > 1
        List<UserCard> allUsersCardsForSet = userCardRepository.findBySetCode(set.toLowerCase());
        Map<String, Set<String>> tradableMap = new LinkedHashMap<>();
        for (UserCard uc : allUsersCardsForSet) {
            if (user.equals(uc.getUser())) continue;
            if (uc.getQuantity() > 1) {
                tradableMap.computeIfAbsent(uc.getCollectorNumber(), k -> new LinkedHashSet<>())
                           .add(uc.getUser());
            }
        }

        // 4. Bucket missing cards into treatment groups (preserving GROUP_ORDER)
        Map<String, List<WizardCard>> byGroup = new LinkedHashMap<>();
        for (String g : GROUP_ORDER) byGroup.put(g, new ArrayList<>());

        for (CardWithUserData c : missing) {
            ScryfallCard sc = c.getCard();
            if (sc == null) continue;
            String group = treatmentGroup(sc);
            List<String> traders = new ArrayList<>(
                    tradableMap.getOrDefault(sc.getCollectorNumber(), Collections.emptySet()));
            byGroup.get(group).add(new WizardCard(
                    sc.getName(), sc.getCollectorNumber(), sc.getRarity(),
                    sc.getThumbnailFront(), sc.getPriceRegular(), sc.getPriceFoil(),
                    sc.getPurchaseLink(), traders));
        }

        // 5. Convert to ordered result, dropping empty groups
        return byGroup.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> {
                    double total = e.getValue().stream()
                            .mapToDouble(wc -> wc.getPriceRegular() != null ? wc.getPriceRegular() : 0.0)
                            .sum();
                    return new WizardGroup(e.getKey(), e.getValue(), total);
                })
                .collect(Collectors.toList());
    }

    // ── Treatment-group helpers ──────────────────────────────────────────────

    private static final List<String> GROUP_ORDER =
            Arrays.asList("Normal", "Showcase", "Extended Art", "Borderless", "Retro Frame", "Full Art");

    static String treatmentGroup(ScryfallCard card) {
        if (card == null) return "Normal";
        String fs = card.getFrameStatus();
        if (fs != null && fs.contains("showcase"))    return "Showcase";
        if (fs != null && fs.contains("extendedart")) return "Extended Art";
        if ("borderless".equalsIgnoreCase(card.getBorderColor())) return "Borderless";
        // Old-bordered / retro-frame cards (e.g. MH2 bonus sheet) have frame="1997" or "1993"
        // and empty frame_effects – they can only be detected via the `frame` year field.
        String frame = card.getFrame();
        if ("1997".equals(frame) || "1993".equals(frame)) return "Retro Frame";
        if (card.isFullArt())                          return "Full Art";
        return "Normal";
    }

    private List<CardWithUserData> sortByTreatmentGroup(List<CardWithUserData> cards) {
        return cards.stream()
                .sorted(Comparator.comparingInt(c -> {
                    int idx = GROUP_ORDER.indexOf(treatmentGroup(c.getCard()));
                    return idx < 0 ? GROUP_ORDER.size() : idx;
                }))
                .collect(Collectors.toList());
    }

    /**
     * Returns a map: index-in-sortedCards → TreatmentGroupStat for every position where
     * a new treatment group starts. Stats (total / missing) are derived from allCards
     * (the unfiltered set) so they reflect set-wide completeness regardless of filters.
     */
    private Map<Integer, TreatmentGroupStat> computeGroupDividers(
            List<CardWithUserData> sortedCards, List<CardWithUserData> allCards) {

        // Aggregate total + missing per group from the full unfiltered set
        Map<String, int[]> groupStats = new LinkedHashMap<>(); // [total, missing]
        for (CardWithUserData c : allCards) {
            if (c.getCard() == null) continue;
            String g = treatmentGroup(c.getCard());
            groupStats.computeIfAbsent(g, k -> new int[2]);
            groupStats.get(g)[0]++;
            if (c.getQuantity() == 0 && c.getFoilQuantity() == 0) groupStats.get(g)[1]++;
        }

        // Walk the sorted (possibly filtered) list and record where each group starts
        Map<Integer, TreatmentGroupStat> dividers = new LinkedHashMap<>();
        String currentGroup = null;
        for (int i = 0; i < sortedCards.size(); i++) {
            String g = treatmentGroup(sortedCards.get(i).getCard());
            if (!g.equals(currentGroup)) {
                currentGroup = g;
                int[] s = groupStats.getOrDefault(g, new int[]{0, 0});
                dividers.put(i, new TreatmentGroupStat(g, s[0], s[1]));
            }
        }
        return dividers;
    }
}
