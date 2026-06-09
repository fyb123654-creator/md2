package com.mygame.network.dto;

import com.mygame.cards.base.Card;
import com.mygame.cards.base.CardType;
import com.mygame.cards.property.BiColorWildPropertyCard;
import com.mygame.cards.property.BuildingCard;
import com.mygame.cards.property.MultiColorWildPropertyCard;
import com.mygame.cards.property.PropertyCard;
import com.mygame.cards.property.StandardPropertyCard;
import com.mygame.cards.rent.BiColorRentCard;
import com.mygame.core.GameManager;
import com.mygame.model.Color;
import com.mygame.model.PlayerManagement;
import com.mygame.model.PropertyZone;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Game state data used for network transfer.
 * Contains all game info that needs to be synchronized.
 */
public class GameStateData implements Serializable {
    private static final long serialVersionUID = 1L;

    private int currentPlayerIndex;
    private int turnClockId;
    private int playedCardsThisTurn;
    private int maxPlayCountPerTurn;
    private boolean gameStarted;
    private boolean gameOver;
    private String winner;
    private List<PlayerData> players;
    private List<String> eventLog;
    private int drawPileCount;
    private int discardPileCount;

    public GameStateData() {
        this.players = new ArrayList<>();
    }

    // Create state data from GameManager
    public static GameStateData fromGameManager(GameManager gameManager) {
        GameStateData data = new GameStateData();
        
        data.currentPlayerIndex = gameManager.getCurrentPlayerIndex();
        data.playedCardsThisTurn = gameManager.getPlayedCardsThisTurn();
        data.maxPlayCountPerTurn = GameManager.MAX_PLAY_COUNT_PER_TURN;
        data.gameStarted = true;
        data.gameOver = gameManager.isGameOver();
        data.winner = gameManager.hasWinner() ? gameManager.getWinner().getName() : null;
        
        // Serialize all players
        for (PlayerManagement player : gameManager.getPlayersView()) {
            data.players.add(PlayerData.fromPlayerManagement(player));
        }
        
        // Deck info
        if (gameManager.getCardManager() != null) {
            data.eventLog = new ArrayList<>(gameManager.getEventLog());
            data.drawPileCount = gameManager.getCardManager().getDrawPileSize();
            data.discardPileCount = gameManager.getCardManager().getDiscardPileSize();
        }
        
        return data;
    }

    // Getters and Setters
    public int getCurrentPlayerIndex() { return currentPlayerIndex; }
    public void setCurrentPlayerIndex(int currentPlayerIndex) { this.currentPlayerIndex = currentPlayerIndex; }
    public int getTurnClockId() { return turnClockId; }
    public void setTurnClockId(int turnClockId) { this.turnClockId = turnClockId; }
    public int getPlayedCardsThisTurn() { return playedCardsThisTurn; }
    public void setPlayedCardsThisTurn(int playedCardsThisTurn) { this.playedCardsThisTurn = playedCardsThisTurn; }
    public int getMaxPlayCountPerTurn() { return maxPlayCountPerTurn; }
    public void setMaxPlayCountPerTurn(int maxPlayCountPerTurn) { this.maxPlayCountPerTurn = maxPlayCountPerTurn; }
    public boolean isGameStarted() { return gameStarted; }
    public void setGameStarted(boolean gameStarted) { this.gameStarted = gameStarted; }
    public boolean isGameOver() { return gameOver; }
    public void setGameOver(boolean gameOver) { this.gameOver = gameOver; }
    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }
    public List<PlayerData> getPlayers() { return players; }
    public void setPlayers(List<PlayerData> players) { this.players = players; }
    public int getDrawPileCount() { return drawPileCount; }
    public void setDrawPileCount(int drawPileCount) { this.drawPileCount = drawPileCount; }
    public int getDiscardPileCount() { return discardPileCount; }
    public List<String> getEventLog() { return eventLog; }
    public void setEventLog(List<String> eventLog) { this.eventLog = eventLog; }
    public void setDiscardPileCount(int discardPileCount) { this.discardPileCount = discardPileCount; }

    /**
     * Player data.
     */
    public static class PlayerData implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String playerId;
        private String playerName;
        private List<CardData> handCards;
        private List<CardData> bankCards;
        private Map<Color, PropertyZoneData> propertyZones;
        private int handCardCount;
        private int avatarId;

        public PlayerData() {
            this.handCards = new ArrayList<>();
            this.bankCards = new ArrayList<>();
            this.propertyZones = new HashMap<>();
        }

        public static PlayerData fromPlayerManagement(PlayerManagement player) {
            PlayerData data = new PlayerData();
            data.playerId = player.getPlayerId();
            data.playerName = player.getName();
            data.handCardCount = player.getHandCardCount();
            data.avatarId = player.getAvatarId();
            
            // Hand cards
            for (Card card : player.getHandCardsView()) {
                data.handCards.add(CardData.fromCard(card));
            }
            
            // Bank
            for (Card card : player.getBankCardsView()) {
                data.bankCards.add(CardData.fromCard(card));
            }
            
            // Property zones
            for (Map.Entry<Color, PropertyZone> entry : player.getPropertyZonesView().entrySet()) {
                data.propertyZones.put(entry.getKey(), PropertyZoneData.fromPropertyZone(entry.getValue()));
            }
            
            return data;
        }

        // Getters and Setters
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public List<CardData> getHandCards() { return handCards; }
        public void setHandCards(List<CardData> handCards) { this.handCards = handCards; }
        public List<CardData> getBankCards() { return bankCards; }
        public void setBankCards(List<CardData> bankCards) { this.bankCards = bankCards; }
        public Map<Color, PropertyZoneData> getPropertyZones() { return propertyZones; }
        public void setPropertyZones(Map<Color, PropertyZoneData> propertyZones) { this.propertyZones = propertyZones; }
        public int getHandCardCount() { return handCardCount; }
        public void setHandCardCount(int handCardCount) { this.handCardCount = handCardCount; }
        public int getAvatarId() { return avatarId; }
        public void setAvatarId(int avatarId) { this.avatarId = avatarId; }
    }

    /**
     * Card data.
     */
    public static class CardData implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String cardId;
        private String name;
        private int value;
        private String cardType;
        private String color;
        private int addedRentValue;

        public CardData() {}

        public static CardData fromCard(Card card) {

            CardData data = new CardData();

            data.cardId = card.getId();

            data.name = card.getName();

            data.value = card.getValue();

            data.cardType = card.getCardType().name();

            if (card instanceof BuildingCard buildingCard) {
                data.addedRentValue = buildingCard.getAddedRentValue();
            }

            if (card instanceof StandardPropertyCard spc) {

                data.color =
                        spc.getCurrentActiveColor()
                                .name();
            }

            else if (card instanceof BiColorWildPropertyCard bwc) {

                data.color =
                        bwc.getCurrentActiveColor()
                                .name();
            }

            else if (card instanceof MultiColorWildPropertyCard mwc) {

                data.color =
                        mwc.getCurrentActiveColor()
                                .name();
            }

            else if (card instanceof BiColorRentCard brc) {

                data.color =
                        brc.getSelectedColor()
                                .name();
            }

            else if (card instanceof PropertyCard pc) {

                if (!pc.getPlayableColors().isEmpty()) {

                    data.color =
                            pc.getPlayableColors()
                                    .iterator()
                                    .next()
                                    .name();
                }
            }

            return data;
        }

        private static transient List<Card> referenceDeck = null;

        public Card toCard() {
            if (CardType.BUILDING.name().equals(cardType) && addedRentValue > 0) {
                return new BuildingCard(cardId, name, value, addedRentValue);
            }
            if (referenceDeck == null) {
                referenceDeck = GameManager.createGameCardManager().getDrawPileView();
            }
            for (Card realCard : referenceDeck) {
                if (realCard.getId().equals(this.cardId)) {
                    if (this.color != null) {
                        Color activeColor = null;
                        try {
                            activeColor = Color.valueOf(this.color);
                        } catch (Exception ignored) {
                        }
                        if (realCard instanceof BiColorWildPropertyCard realBi) {
                            Color[] colors = realBi.getPlayableColors().toArray(new Color[0]);
                            BiColorWildPropertyCard copy = new BiColorWildPropertyCard(
                                    realCard.getId(), realCard.getName(), realCard.getValue(),
                                    colors[0], colors[1], activeColor, realBi.getRentValues());
                            return copy;
                        } else if (realCard instanceof MultiColorWildPropertyCard realMulti) {
                            MultiColorWildPropertyCard copy = new MultiColorWildPropertyCard(
                                    realCard.getId(), realCard.getName(), realCard.getValue(),
                                    realMulti.getRentValues());
                            if (activeColor != null && copy.getPlayableColors().contains(activeColor)) {
                                copy.setCurrentActiveColor(activeColor);
                            }
                            return copy;
                        } else if (realCard instanceof BiColorRentCard brc) {
                            BiColorRentCard copy = new BiColorRentCard(
                                    realCard.getId(), realCard.getName(), realCard.getValue(),
                                    new HashSet<>(brc.getValidColors()));
                            if (activeColor != null && brc.getValidColors().contains(activeColor)) {
                                copy.setSelectedColor(activeColor);
                            }
                            return copy;
                        }
                    }
                    return realCard;
                }
            }

            CardType type = CardType.valueOf(cardType);
            return new Card() {
                @Override
                public String getId() {
                    return cardId;
                }
                @Override
                public String getName() {
                    return name;
                }
                @Override
                public int getValue() {
                    return value;
                }
                @Override
                public CardType getCardType() {
                    return type;
                }
            };
        }

        // Getters and Setters
        public String getCardId() { return cardId; }
        public void setCardId(String cardId) { this.cardId = cardId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
        public String getCardType() { return cardType; }
        public void setCardType(String cardType) { this.cardType = cardType; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public int getAddedRentValue() { return addedRentValue; }
        public void setAddedRentValue(int addedRentValue) { this.addedRentValue = addedRentValue; }
    }

    /**
     * Property zone data.
     */
    public static class PropertyZoneData implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private Color color;
        private List<CardData> properties;
        private CardData house;
        private CardData hotel;

        public PropertyZoneData() {
            this.properties = new ArrayList<>();
        }

        public static PropertyZoneData fromPropertyZone(PropertyZone zone) {
            PropertyZoneData data = new PropertyZoneData();
            data.color = zone.getColor();
            
            for (PropertyCard card : zone.getPropertiesView()) {
                data.properties.add(CardData.fromCard(card));
            }
            
            if (zone.getHouse() != null) {
                data.house = CardData.fromCard(zone.getHouse());
            }
            if (zone.getHotel() != null) {
                data.hotel = CardData.fromCard(zone.getHotel());
            }
            
            return data;
        }

        // Getters and Setters
        public Color getColor() { return color; }
        public void setColor(Color color) { this.color = color; }
        public List<CardData> getProperties() { return properties; }
        public void setProperties(List<CardData> properties) { this.properties = properties; }
        public CardData getHouse() { return house; }
        public void setHouse(CardData house) { this.house = house; }
        public CardData getHotel() { return hotel; }
        public void setHotel(CardData hotel) { this.hotel = hotel; }
    }
}
