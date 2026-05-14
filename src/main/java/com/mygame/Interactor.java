package com.mygame;

import java.util.List;

public interface Interactor {
    /**
     * 选择目标玩家（排除当前玩家）
     */
    PlayerManagement choiceTargetPlayer(PlayerManagement currentPlayer, List<PlayerManagement> players);

    /**
     * 选择可偷取的财产卡
     */
    Card choiceStealablePropertyCard(PlayerManagement targetPlayer);

    /**
     * 确认是否使用 Just Say No 取消操作
     */
    boolean confirmJustSayNo(PlayerManagement targetPlayer, PlayerManagement sourcePlayer, String actionName);

    /**
     * 显示可选择的资产并返回玩家选择的卡牌列表
     */
    List<Card> showSelectableAssets(PlayerManagement payer, int amount);

    /**
     * 确认是否使用双倍租金卡
     */
    boolean confirmUseDoubleTheRent(PlayerManagement player, Color selectedColor, int baseRentAmount);

    /**
     * 选择财产区域
     */
    PropertyZone choicePropertyZone(PlayerManagement currentPlayer);
}

