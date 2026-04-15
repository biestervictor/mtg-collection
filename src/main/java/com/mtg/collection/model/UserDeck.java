package com.mtg.collection.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Represents a physical deck owned by a user.
 * Detected during DragonShield import via Folder Name prefix convention:
 *   MB_DeckName       → mainboard, non-commander
 *   SB_DeckName       → sideboard, non-commander
 *   EB_DeckName       → extra board, non-commander
 *   MB_CM_DeckName    → mainboard, commander deck
 */
@Document(collection = "user-decks")
public class UserDeck {

    @Id
    private String id; // user + "_" + name

    private String user;
    private String name;
    private boolean commander;

    private List<DeckCard> mainboard  = new ArrayList<>();
    private List<DeckCard> sideboard  = new ArrayList<>();
    private List<DeckCard> extraboard = new ArrayList<>();

    private LocalDate updatedAt;

    public UserDeck() {}

    // ── Computed helpers ──────────────────────────────────────────────────────

    public int getMainboardCount() {
        return mainboard == null ? 0 : mainboard.stream().mapToInt(DeckCard::getQuantity).sum();
    }

    public int getSideboardCount() {
        return sideboard == null ? 0 : sideboard.stream().mapToInt(DeckCard::getQuantity).sum();
    }

    public int getExtraboardCount() {
        return extraboard == null ? 0 : extraboard.stream().mapToInt(DeckCard::getQuantity).sum();
    }

    /** Sum of price × quantity across all boards. */
    public double getTotalValue() {
        return allBoards().stream().mapToDouble(c -> c.getPrice() * c.getQuantity()).sum();
    }

    /** Thumbnail URL of the most expensive card across all boards, or null. */
    public String getCoverArtUrl() {
        return allBoards().stream()
                .filter(c -> c.getThumbnailUrl() != null && !c.getThumbnailUrl().isEmpty())
                .max(Comparator.comparingDouble(DeckCard::getPrice))
                .map(DeckCard::getThumbnailUrl)
                .orElse(null);
    }

    /**
     * Best available cover image: stored thumbnail, or Scryfall API fallback from first mainboard card.
     * Returns null only when there are no mainboard cards at all.
     */
    public String getCoverImageUrl() {
        String thumb = getCoverArtUrl();
        if (thumb != null) return thumb;
        if (mainboard != null && !mainboard.isEmpty()) {
            DeckCard first = mainboard.get(0);
            if (first.getSetCode() != null && first.getCollectorNumber() != null) {
                return "https://api.scryfall.com/cards/" + first.getSetCode().toLowerCase()
                        + "/" + first.getCollectorNumber() + "?format=image&version=small";
            }
        }
        return null;
    }

    private List<DeckCard> allBoards() {
        List<DeckCard> all = new ArrayList<>();
        if (mainboard  != null) all.addAll(mainboard);
        if (sideboard  != null) all.addAll(sideboard);
        if (extraboard != null) all.addAll(extraboard);
        return all;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getId()                       { return id; }
    public void   setId(String id)              { this.id = id; }

    public String getUser()                     { return user; }
    public void   setUser(String user)          { this.user = user; }

    public String getName()                     { return name; }
    public void   setName(String name)          { this.name = name; }

    public boolean isCommander()                { return commander; }
    public void    setCommander(boolean commander) { this.commander = commander; }

    public List<DeckCard> getMainboard()        { return mainboard; }
    public void setMainboard(List<DeckCard> mainboard) { this.mainboard = mainboard; }

    public List<DeckCard> getSideboard()        { return sideboard; }
    public void setSideboard(List<DeckCard> sideboard) { this.sideboard = sideboard; }

    public List<DeckCard> getExtraboard()       { return extraboard; }
    public void setExtraboard(List<DeckCard> extraboard) { this.extraboard = extraboard; }

    public LocalDate getUpdatedAt()             { return updatedAt; }
    public void setUpdatedAt(LocalDate updatedAt) { this.updatedAt = updatedAt; }

    // ── Inner class ───────────────────────────────────────────────────────────

    public static class DeckCard {
        private String  name;
        private String  setCode;
        private String  collectorNumber;
        private int     quantity;
        private boolean foil;
        private String  thumbnailUrl;
        private String  imageUrl;
        private double  price;

        public DeckCard() {}

        public DeckCard(String name, String setCode, String collectorNumber, int quantity, boolean foil) {
            this.name            = name;
            this.setCode         = setCode;
            this.collectorNumber = collectorNumber;
            this.quantity        = quantity;
            this.foil            = foil;
        }

        public String  getName()                     { return name; }
        public void    setName(String name)          { this.name = name; }

        public String  getSetCode()                  { return setCode; }
        public void    setSetCode(String setCode)    { this.setCode = setCode; }

        public String  getCollectorNumber()          { return collectorNumber; }
        public void    setCollectorNumber(String cn) { this.collectorNumber = cn; }

        public int     getQuantity()                 { return quantity; }
        public void    setQuantity(int quantity)     { this.quantity = quantity; }

        public boolean isFoil()                      { return foil; }
        public void    setFoil(boolean foil)         { this.foil = foil; }

        public String  getThumbnailUrl()             { return thumbnailUrl; }
        public void    setThumbnailUrl(String u)     { this.thumbnailUrl = u; }

        /** Stored thumbnail, or Scryfall API small-image URL as fallback. */
        public String  getEffectiveThumbnailUrl() {
            if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) return thumbnailUrl;
            if (setCode != null && collectorNumber != null)
                return "https://api.scryfall.com/cards/" + setCode.toLowerCase()
                        + "/" + collectorNumber + "?format=image&version=small";
            return null;
        }

        public String  getImageUrl()                 { return imageUrl; }
        public void    setImageUrl(String u)         { this.imageUrl = u; }

        /** Stored image, or Scryfall API normal-image URL as fallback. */
        public String  getEffectiveImageUrl() {
            if (imageUrl != null && !imageUrl.isEmpty()) return imageUrl;
            if (setCode != null && collectorNumber != null)
                return "https://api.scryfall.com/cards/" + setCode.toLowerCase()
                        + "/" + collectorNumber + "?format=image&version=normal";
            return null;
        }

        public double  getPrice()                    { return price; }
        public void    setPrice(double price)        { this.price = price; }
    }
}
