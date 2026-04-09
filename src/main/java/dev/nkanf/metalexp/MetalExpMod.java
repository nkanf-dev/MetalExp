package dev.nkanf.metalexp;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MetalExpMod implements ModInitializer {
	public static final String MOD_ID = "metalexp";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing {} bootstrap", MOD_ID);
	}
}
