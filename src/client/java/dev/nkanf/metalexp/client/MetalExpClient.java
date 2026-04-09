package dev.nkanf.metalexp.client;

import dev.nkanf.metalexp.MetalExpMod;
import dev.nkanf.metalexp.bootstrap.BootstrapState;
import dev.nkanf.metalexp.diag.DiagnosticsReporter;
import dev.nkanf.metalexp.diag.DiagnosticsSnapshot;
import net.fabricmc.api.ClientModInitializer;

public final class MetalExpClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BootstrapState state = MetalExpMod.bootstrapState();
		if (state == null) {
			MetalExpMod.LOGGER.warn("MetalExp bootstrap state not initialized; skipping startup diagnostics");
			return;
		}

		DiagnosticsReporter.logStartup(new DiagnosticsSnapshot(
			state.config(),
			state.backendPlan(),
			System.getProperty("os.name", "unknown"),
			System.getProperty("os.arch", "unknown")
		));
	}
}
