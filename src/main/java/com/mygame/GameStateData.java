package com.mygame;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏状态数据类 - 用于网络传输
 * 包含所有需要同步的游戏信息
 */
public class GameStateData implements Serializable {
    private static final long serialVersionUID = 1L;

    private int currentPlayerIndex;
    private int playedCardsThisTurn;
    private int maxPlayCountPerTurn;
    private boolean gameStarted;
    private boolean gameOver;
    private String winner;
    private List<PlayerData> players;
    private int drawPileCount;
    private int discardPileCount;

    public GameStateData() {
        this.players = new ArrayList<>();
    }

    // 从GameManager创建状态数据
    public static GameStateData fromGameManager(GameManager gameManager) {
        GameStateData data = new GameStateData();
        
        data.currentPlayerIndex = gameManager.getCurrentPlayerIndex();
        data.playedCardsThisTurn = gameManager.getPlayedCardsThisTurn();
        data.maxPlayCountPerTurn = 3; // MAX_PLAY_COUNT_PER_TURN
        data.gameStarted = true;
        data.gameOver = gameManager.isGameOver();
        data.winner = gameManager.hasWinner() ? gameManager.getWinner().getName() : null;
        
        // 序列化所有玩家数据
        for (PlayerManagement player : gameManager.getPlayersView()) {
            data.players.add(PlayerData.fromPlayerManagement(player));
        }
        
        // 牌堆信息
        if (gameManager.getCardManager() != null) {
            data.drawPileCount = gameManager.getCardManager().getDrawPileSize();
            data.discardPileCount = gameManager.getCardManager().getDiscardPileSize();
        }
        
        return data;
    }

    // Getters and Setters
    public int getCurrentPlayerIndex() { return currentPlayerIndex; }
    public void setCurrentPlayerIndex(int currentPlayerIndex) { this.currentPlayerIndex = currentPlayerIndex; }
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
    public void setDiscardPileCount(int discardPileCount) { this.discardPileCount = discardPileCount; }

    /**
     * 玩家数据类
     */
    public static class PlayerData implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String playerId;
        private String playerName;
        private List<CardData> handCards;
        private List<CardData> bankCards;
        private Map<Color, PropertyZoneData> propertyZones;
        private int handCardCount;

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
            
            // 手牌（只传输数量，不传输具体内容，保护隐私）
            for (Card card : player.getHandCardsView()) {
                data.handCards.add(CardData.fromCard(card));
            }
            
            // 银行
            for (Card card : player.getBankCardsView()) {
                data.bankCards.add(CardData.fromCard(card));
            }
            
            // 物业区域
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
    }

    /**
     * 卡牌数据类
     */
    public static class CardData implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String cardId;
        private String name;
        private int value;
        private String cardType;
        private String color;

        public CardData() {}

        public static CardData fromCard(Card card) {
            CardData data = new CardData();
            data.cardId = card.getId();
            data.name = card.getName();
            data.value = card.getValue();
            data.cardType = card.getCardType().name();
            if (card instanceof PropertyCard) {
                PropertyCard pc = (PropertyCard) card;
                if (!pc.getPlayableColors().isEmpty()) {
                    data.color = pc.getPlayableColors().iterator().next().name();
                }
            }
            return data;
        }
        
        public Card toCard() {
            CardType type = CardType.valueOf(cardType);

            // 创建匿名 Card 实现
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

                @Override
                public boolean isActionCard() {
                    return type == CardType.ACTION || type == CardType.BUILDING;
                }

                @Override
                public boolean isPropertyCard() {
                    return type == CardType.PROPERTY_STANDARD
                            || type == CardType.PROPERTY_WILD_BICOLOR
                            || type == CardType.PROPERTY_WILD_MULTICOLOR;
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
    }

    /**
     * 物业区域数据类
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