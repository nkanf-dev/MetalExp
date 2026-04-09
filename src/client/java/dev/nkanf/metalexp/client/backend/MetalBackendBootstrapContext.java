package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.systems.BackendCreationException;
import dev.nkanf.metalexp.bridge.MetalBridge;
import dev.nkanf.metalexp.bridge.MetalBridgeProbe;
import dev.nkanf.metalexp.bridge.MetalHostSurfaceBootstrap;

import java.util.List;
import java.util.Objects;

final class MetalBackendBootstrapContext implements AutoCloseable {
	private static final String WINDOW_HANDLE_MISSING_MESSAGE = "Metal backend requires a live GLFW window handle.";

	private final MetalBridge metalBridge;
	private final CocoaHostSurface cocoaHostSurface;
	private final MetalHostSurfaceBootstrap hostSurfaceBootstrap;
	private boolean closed;

	private MetalBackendBootstrapContext(MetalBridge metalBridge, CocoaHostSurface cocoaHostSurface, MetalHostSurfaceBootstrap hostSurfaceBootstrap) {
		this.metalBridge = Objects.requireNonNull(metalBridge, "metalBridge");
		this.cocoaHostSurface = Objects.requireNonNull(cocoaHostSurface, "cocoaHostSurface");
		this.hostSurfaceBootstrap = Objects.requireNonNull(hostSurfaceBootstrap, "hostSurfaceBootstrap");
	}

	static MetalBackendBootstrapContext bootstrap(MetalBridge metalBridge, CocoaHostSurfaceResolver cocoaHostSurfaceResolver, long window) throws BackendCreationException {
		Objects.requireNonNull(metalBridge, "metalBridge");
		Objects.requireNonNull(cocoaHostSurfaceResolver, "cocoaHostSurfaceResolver");

		if (window == 0L) {
			throw new BackendCreationException(
				WINDOW_HANDLE_MISSING_MESSAGE,
				BackendCreationException.Reason.OTHER,
				List.of("glfw_window_handle")
			);
		}

		MetalBridgeProbe systemProbe = metalBridge.probe();
		if (!systemProbe.isReady()) {
			throw new BackendCreationException(
				systemProbe.detail(),
				BackendCreationException.Reason.OTHER,
				systemProbe.missingCapabilities()
			);
		}

		CocoaHostSurface cocoaHostSurface = cocoaHostSurfaceResolver.resolve(window);
		MetalBridgeProbe surfaceProbe = metalBridge.probeSurface(
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

		MetalHostSurfaceBootstrap hostSurfaceBootstrap = metalBridge.bootstrapSurface(
			cocoaHostSurface.cocoaWindowHandle(),
			cocoaHostSurface.cocoaViewHandle()
		);
		if (!hostSurfaceBootstrap.isReady()) {
			throw new BackendCreationException(
				hostSurfaceBootstrap.detail(),
				BackendCreationException.Reason.OTHER,
				hostSurfaceBootstrap.missingCapabilities()
			);
		}

		return new MetalBackendBootstrapContext(metalBridge, cocoaHostSurface, hostSurfaceBootstrap);
	}

	CocoaHostSurface cocoaHostSurface() {
		return this.cocoaHostSurface;
	}

	long nativeSurfaceHandle() {
		return this.hostSurfaceBootstrap.nativeSurfaceHandle();
	}

	int drawableWidth() {
		return this.hostSurfaceBootstrap.drawableWidth();
	}

	int drawableHeight() {
		return this.hostSurfaceBootstrap.drawableHeight();
	}

	double contentsScale() {
		return this.hostSurfaceBootstrap.contentsScale();
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}

		this.closed = true;
		this.metalBridge.releaseSurface(this.hostSurfaceBootstrap.nativeSurfaceHandle());
	}
}
