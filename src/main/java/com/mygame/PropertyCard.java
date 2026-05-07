package com.mygame;

import java.util.Set;

public interface PropertyCard extends Card {
    Set<Color> getPlayableColors();

    @Override
    default boolean isPropertyCard() {
        return true;
    }
}
  
