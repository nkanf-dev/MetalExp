package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.SurfaceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetalDeviceBackendTest {
	@Test
	void exposesBootstrapDeviceInfoAndDescriptor() {
		MetalSurfaceDescriptor descriptor = new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D);
		MetalDeviceBackend backend = new MetalDeviceBackend(descriptor);

		assertEquals(descriptor, backend.surfaceDescriptor());
		assertEquals("Metal", backend.getDeviceInfo().backendName());
		assertEquals("Apple", backend.getDeviceInfo().vendorName());
		assertFalse(backend.isDebuggingEnabled());
	}

	@Test
	void createsSurfaceBackendWithBootstrapDescriptor() throws SurfaceException {
		MetalSurfaceDescriptor descriptor = new MetalSurfaceDescriptor(11L, 22L, 33L, 1280, 720, 2.0D);
		MetalDeviceBackend backend = new MetalDeviceBackend(descriptor);

		MetalSurfaceBackend surfaceBackend = (MetalSurfaceBackend) backend.createSurface(123L);
		surfaceBackend.configure(new GpuSurface.Configuration(1280, 720, true));

		assertEquals(descriptor, surfaceBackend.descriptor());
		assertFalse(surfaceBackend.isSuboptimal());
		assertThrows(SurfaceException.class, surfaceBackend::acquireNextTexture);
	}
}
