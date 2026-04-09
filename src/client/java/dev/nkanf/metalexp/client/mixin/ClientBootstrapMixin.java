package dev.nkanf.metalexp.client.mixin;

import dev.nkanf.metalexp.MetalExpMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientBootstrapMixin {
	@Inject(method = "run", at = @At("HEAD"))
	private void metalExp$onClientRun(CallbackInfo ci) {
		if (MetalExpMod.bootstrapState() == null) {
			MetalExpMod.LOGGER.warn("Client startup hook active before MetalExp bootstrap state was initialized");
			return;
		}

		MetalExpMod.LOGGER.info(
			"Client startup hook active; planned backends={}",
			MetalExpMod.bootstrapState().backendPlan().backendsToTry()
		);
	}
}
