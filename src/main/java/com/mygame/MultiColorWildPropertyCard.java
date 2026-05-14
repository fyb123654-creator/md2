package com.mygame;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class MultiColorWildPropertyCard implements PropertyCard {
    private final String id;
    private final String name;
    private final int value;
    private final EnumSet<Color> playableColors;
    private final Color currentActiveColor;
    private final Map<Integer, Integer> rentValues;

    public MultiColorWildPropertyCard(String id, String name, int value, Map<Integer, Integer> rentValues) {
        this.id = id;
        this.name = name;
        this.value = value;
        this.playableColors = EnumSet.allOf(Color.class);
        this.playableColors.remove(Color.WILD);
        this.currentActiveColor = Color.WILD;
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
        return CardType.PROPERTY_WILD_MULTICOLOR;
    }

    @Override
    public Set<Color> getPlayableColors() {
        return Collections.unmodifiableSet(playableColors);
    }

    public Color getCurrentActiveColor() {
        return currentActiveColor;
    }

    public Map<Integer, Integer> getRentValues() {
        return rentValues;
    }
}
