package dev.nkanf.metalexp.client.settings;

import dev.nkanf.metalexp.MetalExpMod;
import dev.nkanf.metalexp.bootstrap.BootstrapState;
import dev.nkanf.metalexp.config.GraphicsApiPreference;
import dev.nkanf.metalexp.config.MetalExpConfig;
import dev.nkanf.metalexp.config.MetalExpConfigStore;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class MetalExpGraphicsApiOptionFactory {
	private static final String OPTION_KEY = "metalexp.options.graphicsApi";

	private MetalExpGraphicsApiOptionFactory() {
	}

	public static OptionInstance<GraphicsApiPreference> create(Options options) {
		Path configPath = FabricLoader.getInstance().getConfigDir().resolve("metalexp.properties");
		BootstrapState bootstrapState = MetalExpMod.bootstrapState();
		MetalExpConfig fallbackConfig = bootstrapState == null ? MetalExpConfig.defaults() : bootstrapState.config();
		MetalExpConfig loadedConfig = new MetalExpConfigStore(configPath).load();
		final MetalExpConfig baselineConfig = loadedConfig == null ? fallbackConfig : loadedConfig;
		final Path finalConfigPath = configPath;

		GraphicsApiPreference initialValue = GraphicsApiPreference.fromBackendKind(baselineConfig.backend());
		MetalExpGraphicsApiSelectionState.onVideoSettingsOpened(initialValue.toBackendKind());

		return new OptionInstance<>(
			OPTION_KEY,
			OptionInstance.cachedConstantTooltip(Component.translatable("metalexp.options.graphicsApi.tooltip")),
			(caption, value) -> Options.genericValueLabel(caption, Component.translatable("metalexp.options.graphicsApi." + value.id())),
			new OptionInstance.Enum<>(List.of(GraphicsApiPreference.values()), GraphicsApiPreference.CODEC),
			GraphicsApiPreference.CODEC,
			initialValue,
			value -> persistSelection(value, baselineConfig, finalConfigPath)
		);
	}

	private static void persistSelection(GraphicsApiPreference selected, MetalExpConfig baselineConfig, Path configPath) {
		MetalExpConfig updatedConfig = new MetalExpConfig(
			selected.toBackendKind(),
			baselineConfig.failureMode(),
			baselineConfig.diagnosticsEnabled()
		);

		MetalExpGraphicsApiSelectionState.onSelectionApplied(updatedConfig.backend());
		MetalExpConfigStore store = new MetalExpConfigStore(configPath);

		try {
			store.save(updatedConfig);
		} catch (IOException exception) {
			MetalExpMod.LOGGER.warn("Failed to persist MetalExp graphics API selection to {}", configPath, exception);
		}
	}
}
