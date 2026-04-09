package dev.nkanf.metalexp.client.backend;

import org.lwjgl.glfw.GLFWNativeCocoa;

final class LwjglCocoaHostSurfaceResolver implements CocoaHostSurfaceResolver {
	@Override
	public CocoaHostSurface resolve(long glfwWindowHandle) {
		return new CocoaHostSurface(
			GLFWNativeCocoa.glfwGetCocoaWindow(glfwWindowHandle),
			GLFWNativeCocoa.glfwGetCocoaView(glfwWindowHandle)
		);
	}
}
