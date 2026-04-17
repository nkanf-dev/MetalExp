package dev.nkanf.metalexp.client.backend;

enum MetalRasterPipelineSpecialCase {
	NONE,
	CLOUDS_SKIP,
	BLUR,
	LIGHTMAP,
	ANIMATE_SPRITE,
	POST_BLIT,
	NONCRITICAL_SKIP,
	WORLD_TERRAIN,
	WORLD_OPAQUE_UNSUPPORTED
}
