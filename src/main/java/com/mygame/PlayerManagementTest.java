package com.mygame;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;

class PlayerManagementTest {

    @Test
    void testAddPropertyAndCompleteSet() {
        PlayerManagement p = new PlayerManagement("1", "Alice");
        PropertyCard brown1 = new StandardPropertyCard("b1", "Mediterranean Avenue", 1, Color.BROWN, Map.of(1,1,2,2));
        PropertyCard brown2 = new StandardPropertyCard("b2", "Baltic Avenue", 1, Color.BROWN, Map.of(1,1,2,2));
        p.addProperty(Color.BROWN, brown1);
        assertFalse(p.isSetComplete(Color.BROWN));
        p.addProperty(Color.BROWN, brown2);
        assertTrue(p.isSetComplete(Color.BROWN));
    }

    @Test
    void testGetRentWithHouse() {
        PlayerManagement p = new PlayerManagement("2", "Bob");
        p.addProperty(Color.BROWN, new StandardPropertyCard("b1", "Med", 1, Color.BROWN, Map.of(1,1,2,2)));
        p.addProperty(Color.BROWN, new StandardPropertyCard("b2", "Bal", 1, Color.BROWN, Map.of(1,1,2,2)));
        assertEquals(2, p.getRent(Color.BROWN)); // 2个棕色，基础租金2
        BuildingCard house = new BuildingCard("h1", "House", 3, 3);
        p.addBuilding(Color.BROWN, house);
        assertEquals(5, p.getRent(Color.BROWN)); // 2 + 3
    }

    @Test
    void testCompleteSetCountAndWin() {
        PlayerManagement p = new PlayerManagement("3", "Charlie");
        // 棕色2张
        p.addProperty(Color.BROWN, new StandardPropertyCard("b1", "Med", 1, Color.BROWN, Map.of(1,1,2,2)));
        p.addProperty(Color.BROWN, new StandardPropertyCard("b2", "Bal", 1, Color.BROWN, Map.of(1,1,2,2)));
        // 浅蓝3张
        p.addProperty(Color.LIGHT_BLUE, new StandardPropertyCard("l1", "Oriental", 1, Color.LIGHT_BLUE, Map.of(1,1,2,2,3,3)));
        p.addProperty(Color.LIGHT_BLUE, new StandardPropertyCard("l2", "Vermont", 1, Color.LIGHT_BLUE, Map.of(1,1,2,2,3,3)));
        p.addProperty(Color.LIGHT_BLUE, new StandardPropertyCard("l3", "Connecticut", 1, Color.LIGHT_BLUE, Map.of(1,1,2,2,3,3)));
        // 粉色3张
        p.addProperty(Color.PINK, new StandardPropertyCard("p1", "St. Charles", 2, Color.PINK, Map.of(1,1,2,2,3,4)));
        p.addProperty(Color.PINK, new StandardPropertyCard("p2", "States", 2, Color.PINK, Map.of(1,1,2,2,3,4)));
        p.addProperty(Color.PINK, new StandardPropertyCard("p3", "Virginia", 2, Color.PINK, Map.of(1,1,2,2,3,4)));
        assertEquals(3, p.getCompleteSetCount());
        assertTrue(p.hasWon());
    }

    @Test
    void testNeedsToDiscard() {
        PlayerManagement p = new PlayerManagement("4", "Dave");
        for (int i = 0; i < 8; i++) {
            p.addToHand(new MoneyCard("m"+i, "1M", 1));
        }
        assertTrue(p.needsToDiscard());
        assertEquals(1, p.getDiscardCountNeeded());
    }
}