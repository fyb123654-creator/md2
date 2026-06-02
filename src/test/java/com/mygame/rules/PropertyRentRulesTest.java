package com.mygame.rules;

import com.mygame.model.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PropertyRentRulesTest {

    @Test
    void getRent_returnsCorrectValuesForBrownSet() {
        assertEquals(1, PropertyRentRules.getRent(Color.BROWN, 1));
        assertEquals(2, PropertyRentRules.getRent(Color.BROWN, 2));
    }

    @Test
    void getRent_returnsCorrectValuesForDarkBlueSet() {
        assertEquals(3, PropertyRentRules.getRent(Color.DARK_BLUE, 1));
        assertEquals(8, PropertyRentRules.getRent(Color.DARK_BLUE, 2));
    }

    @Test
    void getRent_returnsCorrectValuesForRailroadSet() {
        assertEquals(1, PropertyRentRules.getRent(Color.RAILROAD, 1));
        assertEquals(2, PropertyRentRules.getRent(Color.RAILROAD, 2));
        assertEquals(3, PropertyRentRules.getRent(Color.RAILROAD, 3));
        assertEquals(4, PropertyRentRules.getRent(Color.RAILROAD, 4));
    }
}
