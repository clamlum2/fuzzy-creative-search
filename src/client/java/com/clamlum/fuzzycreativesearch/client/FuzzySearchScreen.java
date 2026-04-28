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
import java.util.HashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
            String q = query.toLowerCase();

            filteredItems = allItems.stream()
                    .filter(item -> wordMatch(q, item.getName(new ItemStack(item)).getString().toLowerCase()))
                    .sorted(Comparator.comparingInt(item -> {
                        String id = BuiltInRegistries.ITEM.getKey(item).toString();
                        String name = item.getName(new ItemStack(item)).getString().toLowerCase();
                        int freq = selectionCounts.getOrDefault(id, 0);
                        int prefixBonus = name.startsWith(q) ? 50 : 0;
                        return -(freq * 10 + prefixBonus);
                    }))
                    .limit(MAX_RESULTS)
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

    private static boolean wordMatch(String query, String target) {
        String[] words = query.split("\\s+");
        for (String word : words) {
            if (!target.contains(word)) return false;
        }
        return true;
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
}