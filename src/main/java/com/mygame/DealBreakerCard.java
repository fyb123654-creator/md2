package com.mygame;

public final class DealBreakerCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;

    public DealBreakerCard(String id, String name, int value) {
        this.id = id;
        this.name = name;
        this.value = value;
    }

    @Override
    public String getId() {
        return id;
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
        return CardType.ACTION;
    }

    @Override
    public boolean execute(GameManager gameManager) {
        if (gameManager == null) {
            throw new IllegalArgumentException("gameManager cannot be null");
        }

        PlayerManagement currentPlayer = gameManager.getCurrentPlayer();
        PlayerManagement targetPlayer = gameManager.chooseTargetPlayerExcludingCurrent();
        if (targetPlayer == null) {
            return false;
        }

        // Deal Breaker: 夺取目标玩家的一套完整房产（包括建筑）
        for (Color color : targetPlayer.getPropertyZonesView().keySet()) {
            if (targetPlayer.isSetComplete(color)) {
                // 尝试取消
                if (gameManager.tryCancelWithJustSayNo(targetPlayer, currentPlayer, "Deal Breaker")) {
                    return false;
                }

                // 转移完整的房产区域给当前玩家
                PropertyZone zone = targetPlayer.getPropertyZonesView().get(color);
                for (PropertyCard property : zone.getPropertiesView()) {
                    targetPlayer.removeFromPropertyZones(property);
                    currentPlayer.addProperty(color, property);
                }

                if (zone.getHouse() != null) {
                    BuildingCard house = zone.getHouse();
                    targetPlayer.removeFromPropertyZones(house);
                    currentPlayer.addBuilding(color, house);
                }

                if (zone.getHotel() != null) {
                    BuildingCard hotel = zone.getHotel();
                    targetPlayer.removeFromPropertyZones(hotel);
                    currentPlayer.addBuilding(color, hotel);
                }

                return true;
            }
        }

        throw new IllegalStateException("目标玩家没有完整的房产套组可夺取");
    }
}
