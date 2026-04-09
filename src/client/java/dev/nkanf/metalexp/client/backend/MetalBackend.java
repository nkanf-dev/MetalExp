package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.GLFWErrorCapture;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import dev.nkanf.metalexp.bridge.MetalBridge;
import dev.nkanf.metalexp.bridge.NativeMetalBridge;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Function;

public final class MetalBackend implements GpuBackend {
	private static final String WINDOW_CREATION_FAILURE_MESSAGE = "Failed to create window for Metal";
	private final MetalBridge metalBridge;
	private final CocoaHostSurfaceResolver cocoaHostSurfaceResolver;
	private final Function<MetalSurfaceLease, GpuDevice> gpuDeviceFactory;

	public MetalBackend() {
		this(
			NativeMetalBridge.getInstance(),
			new LwjglCocoaHostSurfaceResolver(),
			surfaceLease -> new GpuDevice(new MetalDeviceBackend(surfaceLease))
		);
	}

	MetalBackend(MetalBridge metalBridge) {
		this(
			metalBridge,
			new LwjglCocoaHostSurfaceResolver(),
			surfaceLease -> new GpuDevice(new MetalDeviceBackend(surfaceLease))
		);
	}

	MetalBackend(MetalBridge metalBridge, CocoaHostSurfaceResolver cocoaHostSurfaceResolver) {
		this(
			metalBridge,
			cocoaHostSurfaceResolver,
			surfaceLease -> new GpuDevice(new MetalDeviceBackend(surfaceLease))
		);
	}

	MetalBackend(MetalBridge metalBridge, CocoaHostSurfaceResolver cocoaHostSurfaceResolver, Function<MetalSurfaceLease, GpuDevice> gpuDeviceFactory) {
		this.metalBridge = Objects.requireNonNull(metalBridge, "metalBridge");
		this.cocoaHostSurfaceResolver = Objects.requireNonNull(cocoaHostSurfaceResolver, "cocoaHostSurfaceResolver");
		this.gpuDeviceFactory = Objects.requireNonNull(gpuDeviceFactory, "gpuDeviceFactory");
	}

	@Override
	public String getName() {
		return "Metal";
	}

	@Override
	public void setWindowHints() {
		GLFW.glfwDefaultWindowHints();
		GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
	}

	@Override
	public void handleWindowCreationErrors(GLFWErrorCapture.Error error) throws BackendCreationException {
		if (error != null) {
			throw new BackendCreationException(error.toString(), BackendCreationException.Reason.GLFW_ERROR);
		}

		throw new BackendCreationException(WINDOW_CREATION_FAILURE_MESSAGE, BackendCreationException.Reason.GLFW_ERROR);
	}

	@Override
	public GpuDevice createDevice(long window, ShaderSource defaultShaderSource, GpuDebugOptions debugOptions) throws BackendCreationException {
		try (MetalBackendBootstrapContext bootstrapContext = MetalBackendBootstrapContext.bootstrap(
			this.metalBridge,
			this.cocoaHostSurfaceResolver,
			window
		)) {
			MetalSurfaceLease surfaceLease = bootstrapContext.detachSurfaceLease();

			try {
				return this.gpuDeviceFactory.apply(surfaceLease);
			} catch (RuntimeException | Error error) {
				surfaceLease.close();
				throw error;
			}
		}
	}
}
