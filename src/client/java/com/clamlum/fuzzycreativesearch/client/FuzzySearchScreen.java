package com.clamlum.fuzzycreativesearch.client;

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

import java.util.ArrayList;
import java.util.List;

public class FuzzySearchScreen extends Screen {

    private static final int PANEL_WIDTH   = 200;
    private static final int PANEL_HEIGHT  = 95;
    private static final int SEARCH_HEIGHT = 16;
    private static final int PADDING       = 4;

    private List<Item> allItems;
    private List<Item> filteredItems = new ArrayList<>();
    private int selectedIndex = 0;

    private EditBox searchBox;
    private InventoryItemSwapper swapper;

    public FuzzySearchScreen() {
        super(Component.literal("Item Search"));
    }

    @Override
    protected void init() {
        allItems = BuiltInRegistries.ITEM.stream().toList();
        swapper  = new InventoryItemSwapper(minecraft);

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
            filteredItems = ItemSearchEngine.search(query, allItems);
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

        int rowY = panelY + PADDING + SEARCH_HEIGHT + PADDING + 1;
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
                ItemSelectionHistory.record(selected);
                assert minecraft.player != null;
                if (minecraft.player.isCreative()) {
                    ItemStack stack = new ItemStack(selected);
                    int slot = swapper.getTargetCreativeSlot();
                    minecraft.player.connection.send(new ServerboundSetCreativeModeSlotPacket(slot, stack));
                    minecraft.player.getInventory().setItem(slot - 36, stack);
                    minecraft.player.getInventory().setSelectedSlot(slot - 36);
                    onClose();
                } else {
                    swapper.swapSurvival(selected);
                    // todo - add failure feedback
                    onClose();
                }
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
    protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {}

    @Override
    protected void extractMenuBackground(GuiGraphicsExtractor graphics) {}

    @Override
    public void removed() {
        GLFW.glfwSetInputMode(
                minecraft.getWindow().handle(),
                GLFW.GLFW_CURSOR,
                GLFW.GLFW_CURSOR_NORMAL
        );
    }
}