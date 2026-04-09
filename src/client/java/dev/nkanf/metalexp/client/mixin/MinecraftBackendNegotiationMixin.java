package dev.nkanf.metalexp.client.mixin;

import com.mojang.blaze3d.opengl.GlBackend;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.vulkan.VulkanBackend;
import dev.nkanf.metalexp.MetalExpMod;
import dev.nkanf.metalexp.bootstrap.BackendNegotiationResolver;
import dev.nkanf.metalexp.bootstrap.BootstrapState;
import dev.nkanf.metalexp.config.BackendKind;
import net.minecraft.client.PreferredGraphicsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
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

		List<BackendKind> resolvedKinds = BackendNegotiationResolver.resolveRunnableBackends(bootstrapState.backendPlan());
		ArrayList<GpuBackend> resolvedBackends = new ArrayList<>(resolvedKinds.size());

		for (BackendKind kind : resolvedKinds) {
			switch (kind) {
				case VULKAN -> resolvedBackends.add(new VulkanBackend());
				case OPENGL -> resolvedBackends.add(new GlBackend());
				case METAL -> {
				}
			}
		}

		if (resolvedBackends.isEmpty()) {
			return preferredGraphicsApi.getBackendsToTry();
		}

		MetalExpMod.LOGGER.info("MetalExp startup negotiation order override active: {}", resolvedKinds);
		return resolvedBackends.toArray(new GpuBackend[0]);
	}
}
