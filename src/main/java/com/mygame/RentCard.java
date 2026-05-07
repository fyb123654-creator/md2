package com.mygame;

import java.util.Set;

public interface RentCard extends ActionCard {
    Set<Color> getValidColors();

    @Override
    default CardType getCardType() {
        return CardType.RENT_BICOLOR;
    }
}

