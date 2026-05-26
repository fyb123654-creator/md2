package com.mygame.cards.property;

import com.mygame.cards.base.CardType;
import com.mygame.model.Color;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class MultiColorWildPropertyCard
        implements PropertyCard {

    private final String id;

    private final String name;

    private final int value;

    private final Set<Color> playableColors;

    private Color currentActiveColor;

    private final Map<Integer, Integer> rentValues;

    public MultiColorWildPropertyCard(
            String id,
            String name,
            int value,
            Map<Integer, Integer> rentValues
    ) {

        this.id = id;

        this.name = name;

        this.value = value;

        this.playableColors =
                Set.of(Color.values());

        this.currentActiveColor =
                Color.RED;

        this.rentValues =
                Map.copyOf(rentValues);
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

        return Collections.unmodifiableSet(
                playableColors
        );
    }

    @Override
    public Color getCurrentActiveColor() {

        return currentActiveColor;
    }

    public void setCurrentActiveColor(
            Color color
    ) {

        if (!playableColors.contains(color)) {

            throw new IllegalArgumentException(
                    "Invalid color"
            );
        }

        this.currentActiveColor = color;
    }

    public Map<Integer, Integer> getRentValues() {

        return rentValues;
    }
}
