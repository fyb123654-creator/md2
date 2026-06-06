package com.mygame.model;

import com.mygame.cards.base.Card;
import com.mygame.cards.property.BuildingCard;
import com.mygame.cards.property.PropertyCard;
import com.mygame.core.deck.CardManager;
import com.mygame.rules.PropertyRentRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerManagement {
    public static final int MAX_HAND_SIZE = 7;
    public static final int REQUIRED_COMPLETE_SETS_TO_WIN = 3;

    private final String playerId;
    private final String playerName;
    private int avatarId;
    private boolean eliminated;

    private final List<Card> handCards;
    private final List<Card> bankCards;
    private final Map<Color, PropertyZone> propertyZones;

    private static final Map<Color, Integer> REQUIRED_SET_SIZE = createRequiredSetSize();

    public PlayerManagement(String playerId, String playerName) {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId cannot be null or blank");
        }
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("playerName cannot be null or blank");
        }

        this.playerId = playerId;
        this.playerName = playerName;
        this.avatarId = 0;
        this.eliminated = false;
        this.handCards = new ArrayList<>();
        this.bankCards = new ArrayList<>();
        this.propertyZones = new EnumMap<>(Color.class);
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getAvatarId() {
        return avatarId;
    }

    public void setAvatarId(int avatarId) {
        this.avatarId = Math.max(0, avatarId);
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    // Compatibility alias for UI code
    public String getName() {
        return getPlayerName();
    }

    public int getHandCardCount() {
        return handCards.size();
    }

    public List<Card> getHandCardsView() {
        return Collections.unmodifiableList(handCards);
    }

    // Compatibility alias for UI code
    public List<Card> getHandCards() {
        return getHandCardsView();
    }

    public List<Card> getBankCardsView() {
        return Collections.unmodifiableList(bankCards);
    }

    public boolean removeFromBank(Card card) {
        validateCard(card);
        return bankCards.remove(card);
    }

    public int getBankTotalValue() {
        int total = 0;
        for (Card card : bankCards) {
            total += card.getValue();
        }
        return total;
    }

    public Map<Color, PropertyZone> getPropertyZonesView() {
        return Collections.unmodifiableMap(propertyZones);
    }

    public boolean removeFromPropertyZones(Card card) {
        validateCard(card);
        boolean removed = false;
        Color emptyZoneKeyToRemove = null;

        for (Map.Entry<Color, PropertyZone> entry : propertyZones.entrySet()) {
            PropertyZone zone = entry.getValue();
            if (!zone.removeCard(card)) {
                continue;
            }
            removed = true;
            // Clean empty zones to avoid UI artifacts
            if (zone.getPropertiesView().isEmpty() && zone.getHouse() == null && zone.getHotel() == null) {
                emptyZoneKeyToRemove = entry.getKey();
            } else if (!isSetComplete(entry.getKey())) {
                // If set becomes incomplete, buildings must be moved to hand
                if (zone.getHotel() != null) {
                    handCards.add(zone.getHotel());
                    zone.removeCard(zone.getHotel());
                }
                if (zone.getHouse() != null) {
                    handCards.add(zone.getHouse());
                    zone.removeCard(zone.getHouse());
                }
            }
            break;
        }

        if (emptyZoneKeyToRemove != null) {
            propertyZones.remove(emptyZoneKeyToRemove);
        }
        return removed;
    }

    public void addToHand(Card card) {
        validateCard(card);
        handCards.add(card);
    }

    public boolean removeFromHand(Card card) {
        validateCard(card);
        return handCards.remove(card);
    }

    public boolean canDiscardToHandLimit() {
        return getHandCardCount() <= MAX_HAND_SIZE;
    }

    public void playCard(Card card, CardManager cardManager) {
        validateCard(card);
        if (cardManager == null) {
            throw new IllegalArgumentException("cardManager cannot be null");
        }
        removeFromHand(card);
        cardManager.playCard(card);
    }

    public void depositToBank(Card card) {
        validateCard(card);
        if (!card.isBankable()) {
            throw new IllegalArgumentException("card is not bankable: " + card.getName());
        }
        bankCards.add(card);
    }

    public void addProperty(Color color, PropertyCard propertyCard) {
        if (color == null) {
            throw new IllegalArgumentException("color cannot be null");
        }
        if (propertyCard == null) {
            throw new IllegalArgumentException("propertyCard cannot be null");
        }
        if (!propertyCard.getPlayableColors().contains(color)) {
            throw new IllegalArgumentException("property card cannot be used as " + color);
        }

        setActiveColorIfSupported(propertyCard, color);
        PropertyZone zone = propertyZones.computeIfAbsent(color, c -> new PropertyZone(c));
        zone.properties.add(propertyCard);
    }

    public boolean movePropertyCardToColor(PropertyCard propertyCard, Color targetColor) {
        if (propertyCard == null) {
            throw new IllegalArgumentException("propertyCard cannot be null");
        }
        if (targetColor == null) {
            throw new IllegalArgumentException("targetColor cannot be null");
        }
        if (!propertyCard.getPlayableColors().contains(targetColor)) {
            throw new IllegalArgumentException("property card cannot be used as " + targetColor);
        }

        Color currentColor = null;
        for (Map.Entry<Color, PropertyZone> entry : propertyZones.entrySet()) {
            if (entry.getValue().getPropertiesView().contains(propertyCard)) {
                currentColor = entry.getKey();
                break;
            }
        }
        if (currentColor == null) {
            return false;
        }

        setActiveColorIfSupported(propertyCard, targetColor);
        if (currentColor == targetColor) {
            return true;
        }

        if (!removeFromPropertyZones(propertyCard)) {
            return false;
        }
        addProperty(targetColor, propertyCard);
        return true;
    }

    private static void setActiveColorIfSupported(PropertyCard propertyCard, Color targetColor) {
        if (propertyCard instanceof com.mygame.cards.property.BiColorWildPropertyCard wildCard) {
            wildCard.setCurrentActiveColor(targetColor);
        } else if (propertyCard instanceof com.mygame.cards.property.MultiColorWildPropertyCard wildCard) {
            wildCard.setCurrentActiveColor(targetColor);
        }
    }

    public void addBuilding(Color color, BuildingCard buildingCard) {
        if (color == null) {
            throw new IllegalArgumentException("color cannot be null");
        }
        if (buildingCard == null) {
            throw new IllegalArgumentException("buildingCard cannot be null");
        }

        PropertyZone zone = propertyZones.computeIfAbsent(color, c -> new PropertyZone(c));
        if (!isSetComplete(color)) {
            throw new IllegalStateException("cannot add building to incomplete set: " + color);
        }

        String lowerName = buildingCard.getName() == null ? "" : buildingCard.getName().toLowerCase();
        if (lowerName.contains("hotel")) {
            if (color == Color.RAILROAD || color == Color.UTILITY) {
                throw new IllegalStateException("hotel cannot be placed on railroad or utility set: " + color);
            }
            if (zone.house == null) {
                throw new IllegalStateException("hotel can only be placed on a set that already has a house: " + color);
            }
            if (zone.hotel != null) {
                throw new IllegalStateException("hotel already exists in set: " + color);
            }
            zone.hotel = buildingCard;
            zone.hasHotel = true;
            return;
        }

        if (lowerName.contains("house")) {
            if (color == Color.RAILROAD || color == Color.UTILITY) {
                throw new IllegalStateException("house cannot be placed on railroad or utility set: " + color);
            }
            if (zone.house != null) {
                throw new IllegalStateException("house already exists in set: " + color);
            }
            zone.house = buildingCard;
            zone.hasHouse = true;
            return;
        }

        throw new IllegalArgumentException("unknown building card: " + buildingCard.getName());
    }
    public int getRent(Color color) {
        if (color == null) {
            throw new IllegalArgumentException("color cannot be null");
        }

        PropertyZone zone = propertyZones.get(color);
        if (zone == null || zone.properties.isEmpty()) {
            return 0;
        }

        int propertyCount = zone.properties.size();
        PropertyRentRules.RentRule rule = PropertyRentRules.RULES.get(color);
        if (rule == null) {
            // Color has no rent rules defined (e.g. BLACK, WILD).
            // Return 0 rent so UI rendering doesn't crash.
            return 0;
        }

        int cappedCount = Math.min(propertyCount, rule.getMaxSetSize());
        int baseRent = PropertyRentRules.getRent(color, cappedCount);

        if (zone.house != null) {
            baseRent += zone.house.getAddedRentValue();
        }
        if (zone.hotel != null) {
            baseRent += zone.hotel.getAddedRentValue();
        }
        return baseRent;
    }

    public boolean isSetComplete(Color color) {
        if (color == null) {
            throw new IllegalArgumentException("color cannot be null");
        }

        return getPropertyCount(color) >= getRequiredSetSize(color);
    }

    public int getPropertyCount(Color color) {
        if (color == null) {
            throw new IllegalArgumentException("color cannot be null");
        }
        PropertyZone zone = propertyZones.get(color);
        return zone == null ? 0 : zone.properties.size();
    }

    public int getRequiredSetSize(Color color) {
        if (color == null) {
            throw new IllegalArgumentException("color cannot be null");
        }
        return REQUIRED_SET_SIZE.getOrDefault(color, Integer.MAX_VALUE);
    }

    public int getCompleteSetCount() {
        int count = 0;
        for (Color color : propertyZones.keySet()) {
            if (isSetComplete(color)) {
                count++;
            }
        }
        return count;
    }

    public boolean hasWon() {
        return !eliminated && getCompleteSetCount() >= REQUIRED_COMPLETE_SETS_TO_WIN;
    }

    public List<Card> removeAllCards() {
        List<Card> all = new ArrayList<>();

        all.addAll(handCards);
        handCards.clear();

        all.addAll(bankCards);
        bankCards.clear();

        for (PropertyZone zone : propertyZones.values()) {
            all.addAll(zone.properties);
            zone.properties.clear();
            if (zone.house != null) {
                all.add(zone.house);
                zone.house = null;
                zone.hasHouse = false;
            }
            if (zone.hotel != null) {
                all.add(zone.hotel);
                zone.hotel = null;
                zone.hasHotel = false;
            }
        }
        propertyZones.clear();

        return all;
    }

    public boolean needsToDiscard() {
        return getHandCardCount() > MAX_HAND_SIZE;
    }

    public int getDiscardCountNeeded() {
        return Math.max(0, getHandCardCount() - MAX_HAND_SIZE);
    }

    /**
     * Returns the total monetary value of all assets (bank + properties + buildings).
     */
    public int calculateAssetTotalValue() {
        int total = 0;
        for (Card card : bankCards) {
            total += card.getValue();
        }
        for (PropertyZone zone : propertyZones.values()) {
            for (PropertyCard pc : zone.getPropertiesView()) {
                total += pc.getValue();
            }
            if (zone.getHouse() != null) {
                total += zone.getHouse().getValue();
            }
            if (zone.getHotel() != null) {
                total += zone.getHotel().getValue();
            }
        }
        return total;
    }

    /**
     * Transfers all assets (bank + properties + buildings) from this player to the collector's hand.
     */
    public void transferAllAssetsTo(PlayerManagement collector) {
        List<Card> bankCopy = new ArrayList<>(bankCards);
        for (Card c : bankCopy) {
            if (removeFromBank(c)) {
                collector.addToHand(c);
            }
        }
        List<Card> props = new ArrayList<>();
        for (PropertyZone zone : propertyZones.values()) {
            props.addAll(zone.getPropertiesView());
            if (zone.getHouse() != null) props.add(zone.getHouse());
            if (zone.getHotel() != null) props.add(zone.getHotel());
        }
        for (Card c : props) {
            if (removeFromPropertyZones(c)) {
                collector.addToHand(c);
            }
        }
    }

    /**
     * Finds a Just Say No card in hand, or null if none.
     */
    public Card findJustSayNoCard() {
        for (Card c : handCards) {
            if (c instanceof com.mygame.cards.action.JustSayNoCard) {
                return c;
            }
        }
        return null;
    }

    /**
     * Finds the color of a property card owned by this player.
     */
    public Color findColorOfProperty(Card card) {
        for (var entry : propertyZones.entrySet()) {
            if (entry.getValue().getPropertiesView().contains(card)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static void validateCard(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("card cannot be null");
        }
    }

    private static Map<Color, Integer> createRequiredSetSize() {
        Map<Color, Integer> map = new HashMap<>();
        map.put(Color.BROWN, 2);
        map.put(Color.DARK_BLUE, 2);

        map.put(Color.LIGHT_BLUE, 3);
        map.put(Color.PINK, 3);
        map.put(Color.ORANGE, 3);
        map.put(Color.RED, 3);
        map.put(Color.YELLOW, 3);
        map.put(Color.GREEN, 3);

        map.put(Color.BLACK, 4);
        map.put(Color.RAILROAD, 4);

        map.put(Color.UTILITY, 2);

        return Collections.unmodifiableMap(map);
    }

}
