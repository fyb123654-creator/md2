package com.mygame;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PropertyRentRulesTest {

    @Test
    void testGetRent() {
        assertEquals(1, PropertyRentRules.getRent(Color.BROWN, 1));
        assertEquals(2, PropertyRentRules.getRent(Color.BROWN, 2));
        assertEquals(3, PropertyRentRules.getRent(Color.DARK_BLUE, 1));
        assertEquals(8, PropertyRentRules.getRent(Color.DARK_BLUE, 2));
        assertEquals(1, PropertyRentRules.getRent(Color.RAILROAD, 1));
        assertEquals(4, PropertyRentRules.getRent(Color.RAILROAD, 4));
        assertEquals(1, PropertyRentRules.getRent(Color.UTILITY, 1));
        assertEquals(2, PropertyRentRules.getRent(Color.UTILITY, 2));
    }
}