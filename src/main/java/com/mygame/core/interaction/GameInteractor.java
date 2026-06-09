package com.mygame.core.interaction;

import com.mygame.cards.base.Card;
import com.mygame.model.Color;
import com.mygame.model.PlayerManagement;
import com.mygame.model.PropertyZone;

import java.util.List;

public interface GameInteractor {
    PlayerManagement choiceTargetPlayer(PlayerManagement currentPlayer, List<PlayerManagement> players);

    Card choiceStealablePropertyCard(PlayerManagement targetPlayer);

    boolean confirmJustSayNo(PlayerManagement responder, PlayerManagement opponent, String actionToCancel);

    List<Card> showSelectableAssets(PlayerManagement targetPlayer, int requiredAmount);

    boolean confirmUseDoubleTheRent(PlayerManagement player, Color selectedColor, int baseRentAmount);

    PropertyZone choicePropertyZone(PlayerManagement player);

    PropertyZone choiceBuildingPropertyZone(PlayerManagement player, Card buildingCard);

    Card choiceProperty(PlayerManagement targetPlayer);
}
