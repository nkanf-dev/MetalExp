package dev.nkanf.metalexp.client.mixin;

import com.mojang.blaze3d.systems.GpuBackend;
import dev.nkanf.metalexp.MetalExpMod;
import dev.nkanf.metalexp.bootstrap.BackendNegotiationResolver;
import dev.nkanf.metalexp.bootstrap.BootstrapState;
import dev.nkanf.metalexp.client.backend.MetalExpBackendFactory;
import dev.nkanf.metalexp.config.BackendKind;
import net.minecraft.client.PreferredGraphicsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(net.minecraft.client.Minecraft.class)
public abstract class MinecraftBackendNegotiationMixin {
	@Redirect(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/PreferredGraphicsApi;getBackendsToTry()[Lcom/mojang/blaze3d/systems/GpuBackend;"
		)
	)
	private GpuBackend[] metalExp$overrideBackendTryOrder(PreferredGraphicsApi preferredGraphicsApi) {
		BootstrapState bootstrapState = MetalExpMod.bootstrapState();
		if (bootstrapState == null || bootstrapState.backendPlan() == null) {
			return preferredGraphicsApi.getBackendsToTry();
		}

		List<BackendKind> resolvedKinds = BackendNegotiationResolver.resolveBackendTryOrder(bootstrapState.backendPlan());
		GpuBackend[] resolvedBackends = MetalExpBackendFactory.createBackends(resolvedKinds);

		if (resolvedBackends.length == 0) {
			return preferredGraphicsApi.getBackendsToTry();
		}

		MetalExpMod.LOGGER.info("MetalExp startup negotiation order override active: {}", resolvedKinds);
		return resolvedBackends;
	}
}
