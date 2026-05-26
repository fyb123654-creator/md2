package com.mygame.rules;

import com.mygame.model.Color;

import java.util.List;
import java.util.Map;

public final class PropertyRentRules {

    public static final class RentRule {
        private final int maxSetSize;
        private final List<Integer> rentsByCount;

        public RentRule(int maxSetSize, List<Integer> rentsByCount) {
            this.maxSetSize = maxSetSize;
            this.rentsByCount = List.copyOf(rentsByCount);
        }

        public int getMaxSetSize() {
            return maxSetSize;
        }

        // index = property count - 1
        public List<Integer> getRentsByCount() {
            return rentsByCount;
        }

        public int getRentForCount(int count) {
            if (count <= 0 || count > rentsByCount.size()) {
                throw new IllegalArgumentException("Invalid property count: " + count);
            }
            return rentsByCount.get(count - 1);
        }
    }

    private PropertyRentRules() {
    }

    public static final Map<Color, RentRule> RULES = Map.of(
            Color.BROWN, new RentRule(2, List.of(1, 2)),
            Color.LIGHT_BLUE, new RentRule(3, List.of(1, 2, 3)),
            Color.PINK, new RentRule(3, List.of(1, 2, 4)),
            Color.ORANGE, new RentRule(3, List.of(1, 3, 5)),
            Color.RED, new RentRule(3, List.of(2, 3, 6)),
            Color.YELLOW, new RentRule(3, List.of(2, 4, 6)),
            Color.GREEN, new RentRule(3, List.of(2, 4, 7)),
            Color.DARK_BLUE, new RentRule(2, List.of(3, 8)),
            Color.RAILROAD, new RentRule(4, List.of(1, 2, 3, 4)),
            Color.UTILITY, new RentRule(2, List.of(1, 2))
    );

    public static int getRent(Color color, int propertyCount) {
        RentRule rule = RULES.get(color);
        if (rule == null) {
            throw new IllegalArgumentException("No rent rule found for color: " + color);
        }
        return rule.getRentForCount(propertyCount);
    }
}

