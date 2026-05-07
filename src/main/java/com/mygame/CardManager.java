package com.mygame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class CardManager {
    // 修改1：总游戏牌数修正为 106 张
    private static final int TOTAL_CARDS = 106;

    private final List<Card> drawPile;
    private final List<Card> discardPile;

    public CardManager(List<Card> initialDrawPile) {
        if (initialDrawPile == null) {
            throw new IllegalArgumentException("initialDrawPile cannot be null");
        }
        this.drawPile = new ArrayList<>(initialDrawPile);
        this.discardPile = new ArrayList<>();
    }

    public static CardManager createDefaultCardManager() {
        Card[] cards = new Card[TOTAL_CARDS];
        int[] nextId = {1};

        addSingleColorProperties(cards, nextId);
        addActionCards(cards, nextId);
        addWildProperties(cards, nextId);
        addMoneyCards(cards, nextId);

        if (nextId[0] != TOTAL_CARDS + 1) {
            throw new IllegalStateException("Expected to create 106 cards, but created " + (nextId[0] - 1));
        }

        List<Card> deck = new ArrayList<>(List.of(cards));
        Collections.shuffle(deck);
        return new CardManager(deck);
    }

    public int getDrawPileSize() {
        return drawPile.size();
    }

    public int getDiscardPileSize() {
        return discardPile.size();
    }

    public List<Card> getDrawPileView() {
        return Collections.unmodifiableList(drawPile);
    }

    public List<Card> getDiscardPileView() {
        return Collections.unmodifiableList(discardPile);
    }

    public Card drawCard() {
        if (drawPile.isEmpty()) {
            return null;
        }
        return drawPile.remove(drawPile.size() - 1);
    }

    public List<Card> drawCards(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }
        List<Card> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Card card = drawCard();
            if (card == null) {
                break;
            }
            result.add(card);
        }
        return result;
    }

    public void playCard(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("card cannot be null");
        }
        discardPile.add(card);
    }

    public void playCards(List<Card> cards) {
        if (cards == null) {
            throw new IllegalArgumentException("cards cannot be null");
        }
        for (Card card : cards) {
            playCard(card);
        }
    }

    public void addToTopOfDrawPile(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("card cannot be null");
        }
        drawPile.add(card);
    }

    private static void addSingleColorProperties(Card[] cards, int[] nextId) {
        addStandardProperty(cards, nextId, "Mediterranean Avenue", 1, Color.BROWN, Map.of(1, 1, 2, 2));
        addStandardProperty(cards, nextId, "Baltic Avenue", 1, Color.BROWN, Map.of(1, 1, 2, 2));
        addStandardProperty(cards, nextId, "Oriental Avenue", 1, Color.LIGHT_BLUE, Map.of(1, 1, 2, 2, 3, 3));
        addStandardProperty(cards, nextId, "Vermont Avenue", 1, Color.LIGHT_BLUE, Map.of(1, 1, 2, 2, 3, 3));
        addStandardProperty(cards, nextId, "Connecticut Avenue", 1, Color.LIGHT_BLUE, Map.of(1, 1, 2, 2, 3, 3));
        addStandardProperty(cards, nextId, "St. Charles Place", 2, Color.PINK, Map.of(1, 1, 2, 2, 3, 4));
        addStandardProperty(cards, nextId, "States Avenue", 2, Color.PINK, Map.of(1, 1, 2, 2, 3, 4));
        addStandardProperty(cards, nextId, "Virginia Avenue", 2, Color.PINK, Map.of(1, 1, 2, 2, 3, 4));
        addStandardProperty(cards, nextId, "St. James Place", 2, Color.ORANGE, Map.of(1, 1, 2, 3, 3, 5));
        addStandardProperty(cards, nextId, "Tennessee Avenue", 2, Color.ORANGE, Map.of(1, 1, 2, 3, 3, 5));
        addStandardProperty(cards, nextId, "New York Avenue", 2, Color.ORANGE, Map.of(1, 1, 2, 3, 3, 5));
        addStandardProperty(cards, nextId, "Kentucky Avenue", 3, Color.RED, Map.of(1, 2, 2, 3, 3, 6));
        addStandardProperty(cards, nextId, "Indiana Avenue", 3, Color.RED, Map.of(1, 2, 2, 3, 3, 6));
        addStandardProperty(cards, nextId, "Illinois Avenue", 3, Color.RED, Map.of(1, 2, 2, 3, 3, 6));
        addStandardProperty(cards, nextId, "Atlantic Avenue", 3, Color.YELLOW, Map.of(1, 2, 2, 4, 3, 6));
        addStandardProperty(cards, nextId, "Ventnor Avenue", 3, Color.YELLOW, Map.of(1, 2, 2, 4, 3, 6));
        addStandardProperty(cards, nextId, "Marvin Gardens", 3, Color.YELLOW, Map.of(1, 2, 2, 4, 3, 6));
        addStandardProperty(cards, nextId, "Pacific Avenue", 4, Color.GREEN, Map.of(1, 2, 2, 4, 3, 7));
        addStandardProperty(cards, nextId, "North Carolina Avenue", 4, Color.GREEN, Map.of(1, 2, 2, 4, 3, 7));
        addStandardProperty(cards, nextId, "Pennsylvania Avenue", 4, Color.GREEN, Map.of(1, 2, 2, 4, 3, 7));
        addStandardProperty(cards, nextId, "Park Place", 4, Color.DARK_BLUE, Map.of(1, 3, 2, 8));
        addStandardProperty(cards, nextId, "Boardwalk", 4, Color.DARK_BLUE, Map.of(1, 3, 2, 8));
        addStandardProperty(cards, nextId, "Reading Railroad", 2, Color.RAILROAD, Map.of(1, 1, 2, 2, 3, 3, 4, 4));
        addStandardProperty(cards, nextId, "Pennsylvania Railroad", 2, Color.RAILROAD, Map.of(1, 1, 2, 2, 3, 3, 4, 4));
        addStandardProperty(cards, nextId, "B. & O. Railroad", 2, Color.RAILROAD, Map.of(1, 1, 2, 2, 3, 3, 4, 4));
        addStandardProperty(cards, nextId, "Short Line", 2, Color.RAILROAD, Map.of(1, 1, 2, 2, 3, 3, 4, 4));
        addStandardProperty(cards, nextId, "Electric Company", 2, Color.UTILITY, Map.of(1, 1, 2, 2));
        addStandardProperty(cards, nextId, "Water Works", 2, Color.UTILITY, Map.of(1, 1, 2, 2));
    }

    private static void addActionCards(Card[] cards, int[] nextId) {
        addSimpleActionCards(cards, nextId, "Deal Breaker", 5, 2);
        addSimpleActionCards(cards, nextId, "Just Say No!", 4, 3);
        addSimpleActionCards(cards, nextId, "Sly Deal", 3, 3);
        addSimpleActionCards(cards, nextId, "Forced Deal", 3, 3);
        addSimpleActionCards(cards, nextId, "Debt Collector", 3, 3);
        addSimpleActionCards(cards, nextId, "It's My Birthday", 2, 3);
        addSimpleActionCards(cards, nextId, "Pass Go", 1, 10);
        addBuildingCards(cards, nextId, "House", 3, 3, 3);
        addBuildingCards(cards, nextId, "Hotel", 4, 2, 4);
        addSimpleActionCards(cards, nextId, "Double The Rent", 1, 2);
        addBiColorRent(cards, nextId, "Rent - Brown / Light Blue", 1, 2, Color.BROWN, Color.LIGHT_BLUE);
        addBiColorRent(cards, nextId, "Rent - Pink / Orange", 1, 2, Color.PINK, Color.ORANGE);
        addBiColorRent(cards, nextId, "Rent - Red / Yellow", 1, 2, Color.RED, Color.YELLOW);
        addBiColorRent(cards, nextId, "Rent - Green / Dark Blue", 1, 2, Color.GREEN, Color.DARK_BLUE);
        addBiColorRent(cards, nextId, "Rent - Railroad / Utility", 1, 2, Color.RAILROAD, Color.UTILITY);
        for (int i = 0; i < 3; i++) {
            put(cards, nextId, new MultiColorWildRentCard(nextCardId(nextId), "Rent - Wild Rent", 3));
        }
    }

    private static void addWildProperties(Card[] cards, int[] nextId) {
        addBiWild(cards, nextId, "Brown / Light Blue Property Wild Card", 1, 1, Color.BROWN, Color.LIGHT_BLUE);
        addBiWild(cards, nextId, "Pink / Orange Property Wild Card", 2, 2, Color.PINK, Color.ORANGE);
        addBiWild(cards, nextId, "Red / Yellow Property Wild Card", 3, 2, Color.RED, Color.YELLOW);
        addBiWild(cards, nextId, "Green / Dark Blue Property Wild Card", 4, 1, Color.GREEN, Color.DARK_BLUE);
        addBiWild(cards, nextId, "Railroad / Utility Property Wild Card", 2, 1, Color.RAILROAD, Color.UTILITY);
        
        // 修改2：更正并移除了重复/错误的百搭牌
        addBiWild(cards, nextId, "Railroad / Green Property Wild Card", 4, 1, Color.RAILROAD, Color.GREEN);
        addBiWild(cards, nextId, "Railroad / Light Blue Property Wild Card", 4, 1, Color.RAILROAD, Color.LIGHT_BLUE);
        
        for (int i = 0; i < 2; i++) {
            put(cards, nextId, new MultiColorWildPropertyCard(nextCardId(nextId), "Any Property Wild Card", 0, Map.of()));
        }
    }

    private static void addMoneyCards(Card[] cards, int[] nextId) {
        addMoney(cards, nextId, 1, 6);
        addMoney(cards, nextId, 2, 5);
        addMoney(cards, nextId, 3, 3);
        addMoney(cards, nextId, 4, 3);
        addMoney(cards, nextId, 5, 2);
        addMoney(cards, nextId, 10, 1);
    }

    private static void addStandardProperty(Card[] cards, int[] nextId, String name, int value, Color color,
            Map<Integer, Integer> rents) {
        put(cards, nextId, new StandardPropertyCard(nextCardId(nextId), name, value, color, rents));
    }

    private static void addSimpleActionCards(Card[] cards, int[] nextId, String name, int value, int count) {
        for (int i = 0; i < count; i++) {
            put(cards, nextId, new SimpleActionCard(nextCardId(nextId), name, value));
        }
    }

    private static void addBuildingCards(Card[] cards, int[] nextId, String name, int value, int count, int addedRent) {
        for (int i = 0; i < count; i++) {
            put(cards, nextId, new BuildingCard(nextCardId(nextId), name, value, addedRent));
        }
    }

    private static void addBiColorRent(Card[] cards, int[] nextId, String name, int value, int count, Color c1, Color c2) {
        for (int i = 0; i < count; i++) {
            put(cards, nextId, new BiColorRentCard(nextCardId(nextId), name, value, EnumSet.of(c1, c2)));
        }
    }

    private static void addBiWild(Card[] cards, int[] nextId, String name, int value, int count, Color c1, Color c2) {
        for (int i = 0; i < count; i++) {
            put(cards, nextId, new BiColorWildPropertyCard(nextCardId(nextId), name, value, c1, c2, c1, Map.of()));
        }
    }

    private static void addMoney(Card[] cards, int[] nextId, int value, int count) {
        for (int i = 0; i < count; i++) {
            put(cards, nextId, new MoneyCard(nextCardId(nextId), value + "M", value));
        }
    }

    private static void put(Card[] cards, int[] nextId, Card card) {
        int index = Integer.parseInt(card.getId()) - 1;
        if (index < 0 || index >= TOTAL_CARDS) {
            throw new IllegalStateException("Card index out of range: " + index + ", cardId=" + card.getId());
        }
        cards[index] = card;
    }

    private static String nextCardId(int[] nextId) {
        return String.valueOf(nextId[0]++);
    }
}