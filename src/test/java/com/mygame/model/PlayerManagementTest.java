package com.mygame.model;

import com.mygame.cards.money.MoneyCard;
import com.mygame.cards.property.BuildingCard;
import com.mygame.cards.property.StandardPropertyCard;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerManagementTest {

    @Test
    void calculateAssetTotalValue_sumsBankAndPropertiesCorrectly() {
        PlayerManagement player = new PlayerManagement("p1", "Alice");
        player.depositToBank(new MoneyCard("m1", "1M", 1));
        player.depositToBank(new MoneyCard("m2", "5M", 5));

        assertEquals(6, player.calculateAssetTotalValue());
    }

    @Test
    void calculateAssetTotalValue_includesPropertiesInTotal() {
        PlayerManagement player = new PlayerManagement("p1", "Alice");
        player.addProperty(Color.BROWN, new StandardPropertyCard("b1", "Baltic", 1, Color.BROWN, Map.of(1, 1, 2, 2)));
        player.addProperty(Color.BROWN, new StandardPropertyCard("b2", "Mediterranean", 1, Color.BROWN, Map.of(1, 1, 2, 2)));

        assertEquals(2, player.calculateAssetTotalValue());
    }

    @Test
    void transferAllAssetsTo_movesAllToCollector() {
        PlayerManagement payer = new PlayerManagement("p1", "Alice");
        PlayerManagement collector = new PlayerManagement("p2", "Bob");
        payer.depositToBank(new MoneyCard("m1", "3M", 3));
        payer.addProperty(Color.RED, new StandardPropertyCard("r1", "Red A", 2, Color.RED, Map.of(1, 2, 2, 3)));

        payer.transferAllAssetsTo(collector);

        assertEquals(0, payer.calculateAssetTotalValue());
        assertEquals(0, payer.getBankCardsView().size());
        assertEquals(0, payer.getPropertyCount(Color.RED));
        assertEquals(5, collector.calculateAssetTotalValue());
    }

    @Test
    void isSetComplete_returnsTrueWhenEnoughProperties() {
        PlayerManagement player = new PlayerManagement("p1", "Alice");
        player.addProperty(Color.BROWN, new StandardPropertyCard("b1", "Baltic", 1, Color.BROWN, Map.of(1, 1, 2, 2)));
        assertFalse(player.isSetComplete(Color.BROWN));

        player.addProperty(Color.BROWN, new StandardPropertyCard("b2", "Mediterranean", 1, Color.BROWN, Map.of(1, 1, 2, 2)));
        assertTrue(player.isSetComplete(Color.BROWN));
    }

    @Test
    void getRent_includesBuildingAddedValues() {
        PlayerManagement player = new PlayerManagement("p1", "Alice");
        player.addProperty(Color.BROWN, new StandardPropertyCard("b1", "Baltic", 1, Color.BROWN, Map.of(1, 1, 2, 2)));
        player.addProperty(Color.BROWN, new StandardPropertyCard("b2", "Mediterranean", 1, Color.BROWN, Map.of(1, 1, 2, 2)));

        int baseRent = player.getRent(Color.BROWN);

        BuildingCard house = new BuildingCard("h1", "House", 3, 3);
        player.addBuilding(Color.BROWN, house);

        assertEquals(baseRent + 3, player.getRent(Color.BROWN));
    }
}
