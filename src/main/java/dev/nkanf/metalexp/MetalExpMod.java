package dev.nkanf.metalexp;

import dev.nkanf.metalexp.bootstrap.BackendPlan;
import dev.nkanf.metalexp.bootstrap.BackendPlanner;
import dev.nkanf.metalexp.bootstrap.BootstrapState;
import dev.nkanf.metalexp.config.MetalExpConfig;
import dev.nkanf.metalexp.config.MetalExpConfigStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MetalExpMod implements ModInitializer {
	public static final String MOD_ID = "metalexp";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static BootstrapState bootstrapState;

	@Override
	public void onInitialize() {
		MetalExpConfigStore store = new MetalExpConfigStore(
			FabricLoader.getInstance().getConfigDir().resolve("metalexp.properties")
		);
		MetalExpConfig config = store.load();
		BackendPlan plan = BackendPlanner.plan(config, isMacOs());

		bootstrapState = new BootstrapState(config, plan);
		LOGGER.info("Initializing {} bootstrap", MOD_ID);
	}

	public static BootstrapState bootstrapState() {
		return bootstrapState;
	}

	private static boolean isMacOs() {
		return System.getProperty("os.name", "").toLowerCase().contains("mac");
	}
}
