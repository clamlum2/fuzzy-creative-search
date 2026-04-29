package com.clamlum.fuzzycreativesearch.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ItemSearchEngine {

    private static final int MAX_RESULTS = 6;

    private ItemSearchEngine() {}

    private record ItemScore(Item item, int score) {}

    public static List<Item> search(String query, List<Item> allItems) {
        String q = query.toLowerCase().trim();

        return allItems.stream()
                .map(item -> {
                    String name = item.getName(new ItemStack(item)).getString().toLowerCase();

                    int score = FuzzyMatcher.fuzzyScore(q, name);
                    if (score < 0) return null;

                    int freq = ItemSelectionHistory.getCount(item);
                    int prefixBonus = name.startsWith(q) ? 50 : 0;

                    score -= (freq * 10 + prefixBonus);

                    return new ItemScore(item, score);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(ItemScore::score))
                .limit(MAX_RESULTS)
                .map(ItemScore::item)
                .toList();
    }
}