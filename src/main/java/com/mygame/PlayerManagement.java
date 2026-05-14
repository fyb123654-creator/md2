package com.mygame;

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

    // 兼容 GameController 中的调用命名
    public String getName() {
        return getPlayerName();
    }

    public int getHandCardCount() {
        return handCards.size();
    }

    public List<Card> getHandCardsView() {
        return Collections.unmodifiableList(handCards);
    }

    // 兼容 GameController 中的调用命名
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

        for (Map.Entry<Color, PropertyZone> entry : propertyZones.entrySet()) {
            PropertyZone zone = entry.getValue();
            if (zone.removeCard(card)) {
                // 清理空分区，避免 UI 显示“(空)”残留
                if (zone.getPropertiesView().isEmpty() && zone.getHouse() == null && zone.getHotel() == null) {
                    propertyZones.remove(entry.getKey());
                }
                return true;
            }
        }
        return false;
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

        PropertyZone zone = propertyZones.computeIfAbsent(color, c -> new PropertyZone(c));
        zone.properties.add(propertyCard);
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
            if (zone.hotel != null) {
                throw new IllegalStateException("hotel already exists in set: " + color);
            }
            zone.hotel = buildingCard;
            zone.hasHotel = true;
            return;
        }

        if (lowerName.contains("house")) {
            if (zone.house != null) {
                throw new IllegalStateException("house already exists in set: " + color);
            }
            if (!isSetComplete(color)) {
                throw new IllegalStateException("cannot add house to incomplete set: " + color);
            }
        zone.house = buildingCard;
        zone.hasHouse = true;
        return;
        }
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
            throw new IllegalArgumentException("No rent rule found for color: " + color);
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
        return getCompleteSetCount() >= REQUIRED_COMPLETE_SETS_TO_WIN;
    }

    public boolean needsToDiscard() {
        return getHandCardCount() > MAX_HAND_SIZE;
    }

    public int getDiscardCountNeeded() {
        return Math.max(0, getHandCardCount() - MAX_HAND_SIZE);
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

