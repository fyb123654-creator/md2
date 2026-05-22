package com.mygame.cards.property;

import com.mygame.cards.base.Card;
import com.mygame.model.Color;

import java.util.Set;

public interface PropertyCard extends Card {

    Set<Color> getPlayableColors();

    // Added for wildcard properties
    Color getCurrentActiveColor();

    @Override
    default boolean isPropertyCard() {
        return true;
    }
}
