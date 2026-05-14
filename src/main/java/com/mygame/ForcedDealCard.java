package com.mygame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ForcedDealCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;

    public ForcedDealCard(String id, String name, int value) {
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

        Interactor interactor = gameManager.getInteractor();
        if (interactor == null) {
            throw new IllegalStateException("interactor is not set");
        }

        PlayerManagement user = gameManager.getCurrentPlayer();
        PlayerManagement target = interactor.choiceTargetPlayer(user, gameManager.getPlayersView());
        if (target == null) {
            return false;
        }

        List<Card> userSelectable = getIncompleteSetPropertyCards(user);
        List<Card> targetSelectable = getIncompleteSetPropertyCards(target);

        // 任一方没有“未凑齐套”的物业牌，则不执行
        if (userSelectable.isEmpty() || targetSelectable.isEmpty()) {
            return false;
        }

        Card userCard = interactor.choicePorperty(user);
        if (userCard == null || !userSelectable.contains(userCard)) {
            return false;
        }

        Card targetCard = interactor.choicePorperty(target);
        if (targetCard == null || !targetSelectable.contains(targetCard)) {
            return false;
        }

        if (!(userCard instanceof PropertyCard userPropertyCard) || !(targetCard instanceof PropertyCard targetPropertyCard)) {
            return false;
        }

        Color userCardColor = findCardColorInPropertyZones(user, userCard);
        Color targetCardColor = findCardColorInPropertyZones(target, targetCard);
        if (userCardColor == null || targetCardColor == null) {
            return false;
        }

        boolean removedUserCard = user.removeFromPropertyZones(userCard);
        boolean removedTargetCard = target.removeFromPropertyZones(targetCard);
        if (!removedUserCard || !removedTargetCard) {
            return false;
        }

        user.addProperty(targetCardColor, targetPropertyCard);
        target.addProperty(userCardColor, userPropertyCard);
        return true;
    }

    private List<Card> getIncompleteSetPropertyCards(PlayerManagement player) {
        List<Card> result = new ArrayList<>();
        for (Map.Entry<Color, PropertyZone> entry : player.getPropertyZonesView().entrySet()) {
            Color color = entry.getKey();
            PropertyZone zone = entry.getValue();
            if (!player.isSetComplete(color)) {
                result.addAll(zone.getPropertiesView());
            }
        }
        return result;
    }

    private Color findCardColorInPropertyZones(PlayerManagement player, Card card) {
        for (Map.Entry<Color, PropertyZone> entry : player.getPropertyZonesView().entrySet()) {
            PropertyZone zone = entry.getValue();
            if (zone.getPropertiesView().contains(card)
                    || zone.getHouse() == card
                    || zone.getHotel() == card) {
                return entry.getKey();
            }
        }
        return null;
    }
}
