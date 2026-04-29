package com.clamlum.fuzzycreativesearch.client;

import com.google.gson.GsonBuilder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FuzzySearchScreen extends Screen {

    private static final int PANEL_WIDTH   = 200;
    private static final int PANEL_HEIGHT  = 100;
    private static final int SEARCH_HEIGHT = 16;
    private static final int PADDING       = 4;
    private static final int MAX_RESULTS   = 6;

    private static final Map<String, Integer> selectionCounts = new HashMap<>();
    private static final Path COUNTS_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("fuzzycreativesearch_counts.json");

    private List<Item> allItems;
    private List<Item> filteredItems = new ArrayList<>();
    private int selectedIndex = 0;

    private EditBox searchBox;

    public FuzzySearchScreen() {
        super(Component.literal("Item Search"));
    }

    @Override
    protected void init() {

        allItems = BuiltInRegistries.ITEM.stream().toList();

        int panelX = (width  - PANEL_WIDTH)  / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;

        searchBox = new EditBox(
                font,
                panelX + PADDING,
                panelY + PADDING,
                PANEL_WIDTH - PADDING * 2,
                SEARCH_HEIGHT,
                Component.literal("Search...")
        );
        searchBox.setMaxLength(64);
        searchBox.setResponder(query -> {
            String q = query.toLowerCase().trim();

            filteredItems = allItems.stream()
                    .map(item -> {
                        String id = BuiltInRegistries.ITEM.getKey(item).toString();
                        String name = item.getName(new ItemStack(item)).getString().toLowerCase();

                        int score = fuzzyScore(q, name);
                        if (score < 0) return null;

                        int freq = selectionCounts.getOrDefault(id, 0);
                        int prefixBonus = name.startsWith(q) ? 50 : 0;

                        score -= (freq * 10 + prefixBonus);

                        return new ItemScore(item, score);
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(a -> a.score))
                    .limit(MAX_RESULTS)
                    .map(a -> a.item)
                    .toList();

            selectedIndex = 0;
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        GLFW.glfwSetInputMode(
                minecraft.getWindow().handle(),
                GLFW.GLFW_CURSOR,
                GLFW.GLFW_CURSOR_HIDDEN
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int panelX = (width  - PANEL_WIDTH)  / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;

        graphics.fillGradient(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xCC1A1A1A, 0xCC1A1A1A);

        int dividerY = panelY + PADDING + SEARCH_HEIGHT + PADDING;
        graphics.fillGradient(panelX + PADDING, dividerY, panelX + PANEL_WIDTH - PADDING, dividerY + 1, 0xFF444444, 0xFF444444);

        int rowY = dividerY + 4;
        for (int i = 0; i < filteredItems.size(); i++) {
            if (i == selectedIndex) {
                graphics.fillGradient(panelX, rowY - 1, panelX + PANEL_WIDTH, rowY + 10, 0x44FFFFFF, 0x44FFFFFF);
            }
            int color = (i == selectedIndex) ? 0xFFFFFFFF : 0xFFAAAAAA;
            graphics.text(font, filteredItems.get(i).getName(new ItemStack(filteredItems.get(i))).getString(), panelX + PADDING, rowY, color);
            rowY += 12;
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || FuzzyCreativeSearchClient.openSearchKey.matches(event)) {
            if (searchBox.getValue().isEmpty() || filteredItems.isEmpty()) {
                onClose();
            } else {
                Item selected = filteredItems.get(selectedIndex);
                saveCount(selected);
                ItemStack stack = new ItemStack(selected);
                int slot = getTargetSlot();
                assert minecraft.player != null;
                minecraft.player.connection.send(new ServerboundSetCreativeModeSlotPacket(slot, stack));
                minecraft.player.getInventory().setItem(slot - 36, stack);
                minecraft.player.getInventory().setSelectedSlot(slot - 36);
                onClose();
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DOWN) {
            selectedIndex = Math.min(selectedIndex + 1, filteredItems.size() - 1);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_UP) {
            selectedIndex = Math.max(selectedIndex - 1, 0);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
    }

    @Override
    protected void extractMenuBackground(GuiGraphicsExtractor graphics) {
    }

    @Override
    public void removed() {
        GLFW.glfwSetInputMode(
                minecraft.getWindow().handle(),
                GLFW.GLFW_CURSOR,
                GLFW.GLFW_CURSOR_NORMAL
        );
    }

    private static void saveCount(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        selectionCounts.merge(id, 1, Integer::sum);
        try {
            Files.writeString(COUNTS_PATH, new GsonBuilder().setPrettyPrinting().create().toJson(selectionCounts));
        } catch (Exception ignored) {}
    }

    private int getTargetSlot() {
        assert minecraft.player != null;
        var inventory = minecraft.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).isEmpty()) {
                return i + 36;
            }
        }
        return inventory.getSelectedSlot() + 36;
    }

    private static final class ItemScore {
        final Item item;
        final int score;
        ItemScore(Item item, int score) {
            this.item = item;
            this.score = score;
        }
    }

    private static int fuzzyScore(String query, String target) {
        query = normalize(query);
        target = normalize(target);

        if (query.isEmpty()) return 0;

        String[] qWords = query.split("\\s+");
        String[] tWords = target.split("\\s+");

        int score = 0;
        boolean[] used = new boolean[tWords.length];

        for (String qw : qWords) {
            int best = Integer.MAX_VALUE;
            int bestIndex = -1;

            for (int i = 0; i < tWords.length; i++) {
                if (used[i]) continue;
                int s = wordScore(qw, tWords[i]);
                if (s < best) {
                    best = s;
                    bestIndex = i;
                }
            }

            if (bestIndex == -1 || best > 4) {
                return -1;
            }

            used[bestIndex] = true;
            score += best;
        }

        score += (tWords.length - qWords.length) * 2;

        return score;
    }

    private static int wordScore(String a, String b) {
        if (b.contains(a)) return 0;
        return levenshtein(a, b);
    }

    private static String normalize(String s) {
        return s.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static int subsequenceScore(String query, String target) {
        int qi = 0;
        int score = 0;
        int consecutive = 0;

        for (int ti = 0; ti < target.length() && qi < query.length(); ti++) {
            if (query.charAt(qi) == target.charAt(ti)) {
                qi++;
                consecutive++;
                score += 1;
            } else {
                consecutive = 0;
                score += 3;
            }
        }
        return (qi == query.length()) ? score : -1;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[b.length()];
    }
}