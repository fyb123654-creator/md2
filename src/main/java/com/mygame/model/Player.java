package com.mygame.model;

import com.mygame.cards.base.Card;

import java.io.Serializable;
import java.util.List;

public interface Player extends Serializable {
    String getId();

    String getName();

    List<Card> getBank();

    List<PropertySet> getPropertySets();
}

