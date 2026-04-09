package dev.nkanf.metalexp.client;

import dev.nkanf.metalexp.MetalExpMod;
import net.fabricmc.api.ClientModInitializer;

public final class MetalExpClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MetalExpMod.LOGGER.info("Initializing {} client hooks", MetalExpMod.MOD_ID);
	}
}
