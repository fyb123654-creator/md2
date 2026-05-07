package com.mygame;

import java.util.List;

public interface Player {
    String getId();

    String getName();

    List<Card> getBank();

    List<PropertySet> getPropertySets();
}

