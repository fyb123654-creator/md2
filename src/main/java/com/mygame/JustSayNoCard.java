package com.mygame;

public final class JustSayNoCard implements ActionCard {
    private final String id;
    private final String name;
    private final int value;

    public JustSayNoCard(String id, String name, int value) {
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
        // Just Say No 卡牌的执行逻辑由 GameManager.tryCancelWithJustSayNo 处理
        // 这里不需要额外操作，直接返回 true
        return true;
    }
}
