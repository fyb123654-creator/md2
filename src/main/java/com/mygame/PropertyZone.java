package com.mygame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PropertyZone {
    private final Color color;
    final List<PropertyCard> properties;
    BuildingCard house;
    BuildingCard hotel;
    boolean hasHouse;
    boolean hasHotel;

    PropertyZone(Color color) {
        this.color = color;
        this.properties = new ArrayList<>();
        this.hasHouse = false;
        this.hasHotel = false;
    }

    public Color getColor() {
        return color;
    }

    public List<PropertyCard> getPropertiesView() {
        return Collections.unmodifiableList(properties);
    }

    public BuildingCard getHouse() {
        return house;
    }

    public BuildingCard getHotel() {
        return hotel;
    }

    public boolean isHasHouse() {
        return hasHouse;
    }

    public boolean isHasHotel() {
        return hasHotel;
    }

    public boolean removeCard(Card card) {
        if (card == null) {
            throw new IllegalArgumentException("card cannot be null");
        }

        if (card instanceof PropertyCard propertyCard) {
            return properties.remove(propertyCard);
        }

        if (card instanceof BuildingCard buildingCard) {
            if (house == buildingCard) {
                house = null;
                hasHouse = false;
                return true;
            }
            if (hotel == buildingCard) {
                hotel = null;
                hasHotel = false;
                return true;
            }
        }

        return false;
    }
}

