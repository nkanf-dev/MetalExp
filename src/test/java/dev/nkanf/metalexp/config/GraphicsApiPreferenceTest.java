package dev.nkanf.metalexp.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphicsApiPreferenceTest {

	@Test
	void fromBackendKindMapsAllSupportedBackends() {
		assertEquals(GraphicsApiPreference.METAL, GraphicsApiPreference.fromBackendKind(BackendKind.METAL));
		assertEquals(GraphicsApiPreference.VULKAN, GraphicsApiPreference.fromBackendKind(BackendKind.VULKAN));
		assertEquals(GraphicsApiPreference.OPENGL, GraphicsApiPreference.fromBackendKind(BackendKind.OPENGL));
	}

	@Test
	void toBackendKindPreservesSelection() {
		assertEquals(BackendKind.METAL, GraphicsApiPreference.METAL.toBackendKind());
		assertEquals(BackendKind.VULKAN, GraphicsApiPreference.VULKAN.toBackendKind());
		assertEquals(BackendKind.OPENGL, GraphicsApiPreference.OPENGL.toBackendKind());
	}

	@Test
	void fromIdFallsBackToMetalWhenInvalid() {
		assertEquals(GraphicsApiPreference.METAL, GraphicsApiPreference.fromId("invalid"));
		assertEquals(GraphicsApiPreference.METAL, GraphicsApiPreference.fromId(null));
	}
}
