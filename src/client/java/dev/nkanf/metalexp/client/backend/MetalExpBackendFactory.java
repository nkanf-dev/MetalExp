package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.opengl.GlBackend;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.vulkan.VulkanBackend;
import dev.nkanf.metalexp.config.BackendKind;

import java.util.ArrayList;
import java.util.List;

public final class MetalExpBackendFactory {
	private MetalExpBackendFactory() {
	}

	public static GpuBackend[] createBackends(List<BackendKind> backendKinds) {
		if (backendKinds == null || backendKinds.isEmpty()) {
			return new GpuBackend[0];
		}

		ArrayList<GpuBackend> backends = new ArrayList<>(backendKinds.size());
		for (BackendKind kind : backendKinds) {
			switch (kind) {
				case METAL -> backends.add(new MetalBackend());
				case VULKAN -> backends.add(new VulkanBackend());
				case OPENGL -> backends.add(new GlBackend());
			}
		}

		return backends.toArray(new GpuBackend[0]);
	}
}
