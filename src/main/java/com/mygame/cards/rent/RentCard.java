package com.mygame.cards.rent;

import com.mygame.cards.base.ActionCard;
import com.mygame.cards.base.CardType;
import com.mygame.model.Color;

import java.util.Set;

public interface RentCard extends ActionCard {
    Set<Color> getValidColors();

    @Override
    default CardType getCardType() {
        return CardType.RENT_BICOLOR;
    }
    
}

