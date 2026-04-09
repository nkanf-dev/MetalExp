package dev.nkanf.metalexp.client.mixin;

import dev.nkanf.metalexp.client.settings.MetalExpGraphicsApiSelectionState;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsRestartWarningMixin {
	@Redirect(
		method = {"addTitle", "tick"},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/Options;hasPreferredGraphicsBackendChanged()Z"
		)
	)
	private boolean metalExp$includeMetalExpGraphicsApiChange(Options options) {
		return options.hasPreferredGraphicsBackendChanged() || MetalExpGraphicsApiSelectionState.hasPendingRestart();
	}
}
