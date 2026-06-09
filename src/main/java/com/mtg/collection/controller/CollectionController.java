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
                                   @RequestParam(name = "sets", required = false) String setsParam,
                                   @RequestParam(required = false) String user,
                                   @RequestParam(required = false) String compareUser,
                                   @RequestParam(required = false, defaultValue = "true") boolean onlyTradableUser,
                                   @RequestParam(required = false, defaultValue = "true") boolean onlyTradableCompare,
                                   @RequestParam(required = false, defaultValue = "normal") String viewMode,
                                   @RequestParam(required = false) String showTokens,
                                   @RequestParam(required = false) String showPromos) {

        List<ScryfallSet> sets = scryfallService.getAllSets(false);
        model.addAttribute("sets", sets);
        // Map for fast icon/name lookup in template (Multi-Set-Accordion)
        Map<String, ScryfallSet> setsByCode = sets.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ScryfallSet::getSetCode, s -> s, (a, b) -> a, LinkedHashMap::new));
        model.addAttribute("setsByCode", setsByCode);

        // Normalize set parameters: prefer `sets` (multi), fallback to `set` (single, legacy)
        List<String> normalizedSets = normalizeSets(setsParam, set);

        model.addAttribute("selectedSet", normalizedSets.isEmpty() ? set : normalizedSets.get(0));
        model.addAttribute("selectedSets", normalizedSets);
        model.addAttribute("onlyTradableUser", onlyTradableUser);
        model.addAttribute("onlyTradableCompare", onlyTradableCompare);
        model.addAttribute("viewMode", viewMode);
        model.addAttribute("showTokens", showTokens);
        model.addAttribute("showPromos", showPromos);

        if (!normalizedSets.isEmpty() && user != null && !user.isEmpty() &&
            compareUser != null && !compareUser.isEmpty()) {

            // Build per-set results: each set gets its own CompareDiff
            LinkedHashMap<String, CompareDiff> setResults = new LinkedHashMap<>();
            LinkedHashMap<String, CompareDiff> tokenResults = new LinkedHashMap<>();
            LinkedHashMap<String, CompareDiff> promoResults = new LinkedHashMap<>();

            for (String setCode : normalizedSets) {
                CompareDiff main = computeDiff(user, compareUser, setCode,
                        onlyTradableUser, onlyTradableCompare, /*filterTokens=*/ true);
                setResults.put(setCode, main);

                if ("true".equals(showTokens)) {
                    String tokenSetCode = "t" + setCode;
                    if (!scryfallService.getCardsBySet(tokenSetCode, null).isEmpty()) {
                        CompareDiff tok = computeDiff(user, compareUser, tokenSetCode,
                                onlyTradableUser, onlyTradableCompare, /*filterTokens=*/ false);
                        tokenResults.put(setCode, tok);
                    }
                }

                if ("true".equals(showPromos)) {
                    String promoSetCode = "p" + setCode;
                    if (!scryfallService.getCardsBySet(promoSetCode, null).isEmpty()) {
                        CompareDiff pro = computeDiff(user, compareUser, promoSetCode,
                                onlyTradableUser, onlyTradableCompare, /*filterTokens=*/ false);
                        promoResults.put(setCode, pro);
                    }
                }
            }

            model.addAttribute("compareUser", compareUser);
            model.addAttribute("user", user);
            model.addAttribute("setResults", setResults);
            model.addAttribute("tokenResults", tokenResults);
            model.addAttribute("promoResults", promoResults);

            // Backwards-compatible: if exactly one set, also expose flat attributes
            // (existing template uses these — kept until template is updated in next phase)
            if (normalizedSets.size() == 1) {
                String firstSet = normalizedSets.get(0);
                CompareDiff main = setResults.get(firstSet);
                model.addAttribute("onlyUser", main.onlyUser);
                model.addAttribute("onlyCompare", main.onlyCompare);
                model.addAttribute("userDividers", main.userDividers);
                model.addAttribute("compareDividers", main.compareDividers);

                CompareDiff tok = tokenResults.get(firstSet);
                if (tok != null) {
                    model.addAttribute("tokenOnlyUser",        tok.onlyUser);
                    model.addAttribute("tokenOnlyCompare",     tok.onlyCompare);
                    model.addAttribute("tokenUserDividers",    tok.userDividers);
                    model.addAttribute("tokenCompareDividers", tok.compareDividers);
                }
                CompareDiff pro = promoResults.get(firstSet);
                if (pro != null) {
                    model.addAttribute("promoOnlyUser",        pro.onlyUser);
                    model.addAttribute("promoOnlyCompare",     pro.onlyCompare);
                    model.addAttribute("promoUserDividers",    pro.userDividers);
                    model.addAttribute("promoCompareDividers", pro.compareDividers);
                }
            }
        }

        return "compare";
    }

    /**
     * Normalize set selection: `sets` (comma-separated) wins over `set` (single, legacy).
     * Returns distinct, trimmed, lowercase, non-empty codes preserving order.
     */
    static List<String> normalizeSets(String setsParam, String singleSet) {
        String raw = (setsParam != null && !setsParam.isEmpty()) ? setsParam : singleSet;
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String s : raw.split(",")) {
            String trimmed = s.trim().toLowerCase();
            if (!trimmed.isEmpty()) distinct.add(trimmed);
        }
        return new ArrayList<>(distinct);
    }

    /**
     * Halter für einen Diff-Result (onlyUser, onlyCompare + zugehörige Divider-Maps).
     * Wird sowohl für reguläre Sets als auch für Token/Promo-Sub-Sets verwendet.
     */
    /**
     * Halter für einen Diff-Result (onlyUser, onlyCompare + zugehörige Divider-Maps).
     * Wird sowohl für reguläre Sets als auch für Token/Promo-Sub-Sets verwendet.
     * Public so Thymeleaf can access via property accessors (getOnlyUser(), getOnlyCompare(), ...).
     */
    public static class CompareDiff {
        private final List<CardWithUserData> onlyUser;
        private final List<CardWithUserData> onlyCompare;
        private final Map<Integer, TreatmentGroupStat> userDividers;
        private final Map<Integer, TreatmentGroupStat> compareDividers;

        CompareDiff(List<CardWithUserData> onlyUser, List<CardWithUserData> onlyCompare,
                    Map<Integer, TreatmentGroupStat> userDividers,
                    Map<Integer, TreatmentGroupStat> compareDividers) {
            this.onlyUser = onlyUser;
            this.onlyCompare = onlyCompare;
            this.userDividers = userDividers;
            this.compareDividers = compareDividers;
        }

        public List<CardWithUserData> getOnlyUser()    { return onlyUser; }
        public List<CardWithUserData> getOnlyCompare() { return onlyCompare; }
        public Map<Integer, TreatmentGroupStat> getUserDividers()    { return userDividers; }
        public Map<Integer, TreatmentGroupStat> getCompareDividers() { return compareDividers; }
    }

    /**
     * Vollständige Diff-Pipeline für ein Set: owned filter → getOnlyInLeft → tradable filter →
     * optional token filter → sortByTreatmentGroup → compute dividers.
     *
     * @param filterTokens true für reguläre Sets (Tokens raus), false für Token-/Promo-Sub-Sets
     */
    private CompareDiff computeDiff(String user, String compareUser, String setCode,
                                    boolean onlyTradableUser, boolean onlyTradableCompare,
                                    boolean filterTokens) {
        List<CardWithUserData> userCards    = collectionService.getCardsWithUserData(user, setCode, null);
        List<CardWithUserData> compareCards = collectionService.getCardsWithUserData(compareUser, setCode, null);

        List<CardWithUserData> userOwned = userCards.stream()
                .filter(c -> c.getQuantity() > 0 || c.getFoilQuantity() > 0)
                .collect(Collectors.toList());
        List<CardWithUserData> compareOwned = compareCards.stream()
                .filter(c -> c.getQuantity() > 0 || c.getFoilQuantity() > 0)
                .collect(Collectors.toList());

        List<CardWithUserData> onlyUser    = cardFilterService.getOnlyInLeft(userOwned, compareOwned);
        List<CardWithUserData> onlyCompare = cardFilterService.getOnlyInLeft(compareOwned, userOwned);

        if (onlyTradableUser)    onlyUser    = cardFilterService.filterTradable(onlyUser);
        if (onlyTradableCompare) onlyCompare = cardFilterService.filterTradable(onlyCompare);

        if (filterTokens) {
            onlyUser    = filterOutTokens(onlyUser);
            onlyCompare = filterOutTokens(onlyCompare);
        }

        onlyUser    = sortByTreatmentGroup(onlyUser);
        onlyCompare = sortByTreatmentGroup(onlyCompare);

        return new CompareDiff(
                onlyUser,
                onlyCompare,
                computeCompareDividers(onlyUser),
                computeCompareDividers(onlyCompare)
        );
    }

    /** Filtert Karten heraus, deren typeLine "token" enthaelt (case-insensitive). */
    private List<CardWithUserData> filterOutTokens(List<CardWithUserData> cards) {
        return cards.stream()
                .filter(c -> c.getCard() == null || c.getCard().getTypeLine() == null ||
                            !c.getCard().getTypeLine().toLowerCase().contains("token"))
                .collect(Collectors.toList());
    }

    /**
     * Wie {@link #computeGroupDividers}, aber fuer die Compare-Seite: zeigt nur die
     * Anzahl der Karten pro Gruppe in der diff-Liste, nicht "X von Y fehlen".
     * Die "missing"-Property wird auf 0 gesetzt; das Template prueft das und blendet
     * den missing-Text aus.
     */
    private Map<Integer, TreatmentGroupStat> computeCompareDividers(List<CardWithUserData> sortedCards) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CardWithUserData c : sortedCards) {
            if (c.getCard() == null) continue;
            String g = treatmentGroup(c.getCard());
            counts.merge(g, 1, Integer::sum);
        }

        Map<Integer, TreatmentGroupStat> dividers = new LinkedHashMap<>();
        String currentGroup = null;
        for (int i = 0; i < sortedCards.size(); i++) {
            String g = treatmentGroup(sortedCards.get(i).getCard());
            if (!g.equals(currentGroup)) {
                currentGroup = g;
                dividers.put(i, new TreatmentGroupStat(g, counts.getOrDefault(g, 0), 0));
            }
        }
        return dividers;
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

    @PostMapping("/api/sets/refresh")
    public String refreshSets() {
        scryfallService.getAllSets(true);
        return "redirect:/show";
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
