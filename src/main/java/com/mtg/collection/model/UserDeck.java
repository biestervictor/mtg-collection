package com.mtg.collection.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.ArrayList;
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
    }
}
