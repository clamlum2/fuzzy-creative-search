package com.clamlum.fuzzycreativesearch.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class ItemSelectionHistory {

    private static final Map<String, Integer> selectionCounts = new HashMap<>();
    private static final Path COUNTS_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("fuzzycreativesearch_counts.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ItemSelectionHistory() {}

    public static void load() {
        try {
            if (Files.exists(COUNTS_PATH)) {
                String json = Files.readString(COUNTS_PATH);
                Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                Map<String, Integer> loaded = GSON.fromJson(json, type);
                if (loaded != null) {
                    selectionCounts.putAll(loaded);
                }
            }
        } catch (Exception ignored) {}
    }

    public static void record(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        selectionCounts.merge(id, 1, Integer::sum);
        try {
            Files.writeString(COUNTS_PATH, GSON.toJson(selectionCounts));
        } catch (Exception ignored) {}
    }

    public static int getCount(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return selectionCounts.getOrDefault(id, 0);
    }
}