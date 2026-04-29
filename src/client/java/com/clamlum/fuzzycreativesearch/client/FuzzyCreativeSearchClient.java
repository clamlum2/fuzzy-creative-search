package com.clamlum.fuzzycreativesearch.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import static com.clamlum.fuzzycreativesearch.client.ItemSelectionHistory.load;

public class FuzzyCreativeSearchClient implements ClientModInitializer {

	public static KeyMapping openSearchKey;

	@Override
	public void onInitializeClient() {
		openSearchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.itemsearch.open",
				GLFW.GLFW_KEY_ENTER,
				KeyMapping.Category.MISC
		));

		load();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (openSearchKey.consumeClick()
					&& client.screen == null
					&& client.player != null) {
				client.setScreen(new FuzzySearchScreen());
			}
		});
	}
}