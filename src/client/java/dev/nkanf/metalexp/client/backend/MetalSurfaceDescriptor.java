package dev.nkanf.metalexp.client.backend;

record MetalSurfaceDescriptor(
	long cocoaWindowHandle,
	long cocoaViewHandle,
	long nativeSurfaceHandle,
	int drawableWidth,
	int drawableHeight,
	double contentsScale
) {
}
