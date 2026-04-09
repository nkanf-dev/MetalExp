package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.GLFWErrorCapture;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import dev.nkanf.metalexp.bridge.MetalBridge;
import dev.nkanf.metalexp.bridge.MetalBridgeProbe;
import dev.nkanf.metalexp.bridge.NativeMetalBridge;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;

public final class MetalBackend implements GpuBackend {
	private static final String WINDOW_CREATION_FAILURE_MESSAGE = "Failed to create window for Metal";
	private static final String WINDOW_HANDLE_MISSING_MESSAGE = "Metal backend requires a live GLFW window handle.";
	private static final String DEVICE_BACKEND_UNIMPLEMENTED_MESSAGE = "Metal bridge probe succeeded, but the Java Metal device backend is not implemented yet.";
	private final MetalBridge metalBridge;
	private final CocoaHostSurfaceResolver cocoaHostSurfaceResolver;

	public MetalBackend() {
		this(NativeMetalBridge.getInstance(), new LwjglCocoaHostSurfaceResolver());
	}

	MetalBackend(MetalBridge metalBridge) {
		this(metalBridge, new LwjglCocoaHostSurfaceResolver());
	}

	MetalBackend(MetalBridge metalBridge, CocoaHostSurfaceResolver cocoaHostSurfaceResolver) {
		this.metalBridge = Objects.requireNonNull(metalBridge, "metalBridge");
		this.cocoaHostSurfaceResolver = Objects.requireNonNull(cocoaHostSurfaceResolver, "cocoaHostSurfaceResolver");
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
		if (window == 0L) {
			throw new BackendCreationException(
				WINDOW_HANDLE_MISSING_MESSAGE,
				BackendCreationException.Reason.OTHER,
				List.of("glfw_window_handle")
			);
		}

		MetalBridgeProbe probe = this.metalBridge.probe();
		if (!probe.isReady()) {
			throw new BackendCreationException(
				probe.detail(),
				BackendCreationException.Reason.OTHER,
				probe.missingCapabilities()
			);
		}

		CocoaHostSurface cocoaHostSurface = this.cocoaHostSurfaceResolver.resolve(window);
		MetalBridgeProbe surfaceProbe = this.metalBridge.probeSurface(
			cocoaHostSurface.cocoaWindowHandle(),
			cocoaHostSurface.cocoaViewHandle()
		);
		if (!surfaceProbe.isReady()) {
			throw new BackendCreationException(
				surfaceProbe.detail(),
				BackendCreationException.Reason.OTHER,
				surfaceProbe.missingCapabilities()
			);
		}

		throw new BackendCreationException(
			DEVICE_BACKEND_UNIMPLEMENTED_MESSAGE,
			BackendCreationException.Reason.OTHER,
			List.of("metal_device_backend")
		);
	}
}
