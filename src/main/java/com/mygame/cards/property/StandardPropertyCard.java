package com.mygame.cards.property;

import com.mygame.cards.base.CardType;
import com.mygame.model.Color;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class StandardPropertyCard implements PropertyCard {
    private final String id;
    private final String name;
    private final int value;
    private final Set<Color> playableColors;
    private final Color currentActiveColor;
    private final Map<Integer, Integer> rentValues;

    public StandardPropertyCard(String id, String name, int value, Color color, Map<Integer, Integer> rentValues) {
        this.id = id;
        this.name = name;
        this.value = value;
        this.playableColors = EnumSet.of(color);
        this.currentActiveColor = color;
        this.rentValues = Map.copyOf(rentValues);
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
        return CardType.PROPERTY_STANDARD;
    }

    @Override
    public Set<Color> getPlayableColors() {
        return playableColors;
    }

    public Color getCurrentActiveColor() {
        return currentActiveColor;
    }

    public Map<Integer, Integer> getRentValues() {
        return rentValues;
    }
}
