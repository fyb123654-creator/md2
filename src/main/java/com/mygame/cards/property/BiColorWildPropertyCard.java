package com.mygame.cards.property;

import com.mygame.cards.base.CardType;
import com.mygame.model.Color;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class BiColorWildPropertyCard
        implements PropertyCard, java.io.Serializable {

    private final String id;

    private final String name;

    private final int value;

    private final EnumSet<Color> playableColors;

    // Not final: active color can change
    private Color currentActiveColor;

    private final Map<Integer, Integer> rentValues;

    public BiColorWildPropertyCard(
            String id,
            String name,
            int value,
            Color color1,
            Color color2,
            Color activeColor,
            Map<Integer, Integer> rentValues
    ) {

        this.id = id;

        this.name = name;

        this.value = value;

        this.playableColors =
                EnumSet.of(color1, color2);

        this.currentActiveColor =
                activeColor;

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
        return CardType.PROPERTY_WILD_BICOLOR;
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

    // Setter used by network DTO reconstruction
    public void setCurrentActiveColor(Color color) {

        if (!playableColors.contains(color)) {
            throw new IllegalArgumentException(
                    "Invalid active color"
            );
        }

        this.currentActiveColor = color;
    }

    public Map<Integer, Integer> getRentValues() {
        return rentValues;
    }
}
