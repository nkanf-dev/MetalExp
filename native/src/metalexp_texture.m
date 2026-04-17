#include "metalexp_common.h"

jlong metalexp_create_texture(
	jint width,
	jint height,
	jint depth_or_layers,
	jint mip_levels,
	jboolean render_attachment,
	jboolean shader_read,
	jboolean cubemap_compatible,
	jboolean depth_texture
) {
	if (width <= 0 || height <= 0 || depth_or_layers <= 0 || mip_levels <= 0) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture creation requires positive width, height, layer count, and mip level count."
			userInfo:nil];
	}

	if (cubemap_compatible == JNI_TRUE && depth_or_layers != 6) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal cubemap texture creation requires exactly 6 layers."
			userInfo:nil];
	}

	id<MTLDevice> device = MTLCreateSystemDefaultDevice();
	if (device == nil) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture creation requires a default MTLDevice."
			userInfo:nil];
	}

	MTLTextureDescriptor *descriptor = [[MTLTextureDescriptor alloc] init];
	descriptor.pixelFormat = depth_texture == JNI_TRUE ? MTLPixelFormatDepth32Float : MTLPixelFormatRGBA8Unorm;
	descriptor.width = (NSUInteger)width;
	descriptor.height = (NSUInteger)height;
	descriptor.mipmapLevelCount = (NSUInteger)mip_levels;
	descriptor.storageMode = MTLStorageModeShared;
	descriptor.usage = 0;
	if (render_attachment == JNI_TRUE) {
		descriptor.usage |= MTLTextureUsageRenderTarget;
	}
	if (shader_read == JNI_TRUE) {
		descriptor.usage |= MTLTextureUsageShaderRead;
	}
	if (cubemap_compatible == JNI_TRUE) {
		descriptor.textureType = MTLTextureTypeCube;
	} else if (depth_or_layers > 1) {
		descriptor.textureType = MTLTextureType2DArray;
		descriptor.arrayLength = (NSUInteger)depth_or_layers;
	} else {
		descriptor.textureType = MTLTextureType2D;
	}

	id<MTLTexture> texture = [device newTextureWithDescriptor:descriptor];
	if (texture == nil) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture creation could not allocate an MTLTexture."
			userInfo:nil];
	}

	metalexp_native_texture *native_texture = calloc(1, sizeof(metalexp_native_texture));
	if (native_texture == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture creation could not allocate native texture storage."
			userInfo:nil];
	}

	native_texture->texture = (void *)CFBridgingRetain(texture);
	native_texture->width = (uint32_t)width;
	native_texture->height = (uint32_t)height;
	native_texture->depth_or_layers = (uint32_t)depth_or_layers;
	native_texture->mip_levels = (uint32_t)mip_levels;
	native_texture->cubemap_compatible = cubemap_compatible == JNI_TRUE;
	native_texture->depth_texture = depth_texture == JNI_TRUE;
	return (jlong)(uintptr_t)native_texture;
}

void metalexp_upload_texture_rgba8(jlong native_texture_handle, jint mip_level, jint layer, const void *pixels, jint width, jint height, jlong capacity) {
	if (pixels == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture upload requires a direct pixel buffer."
			userInfo:nil];
	}

	metalexp_native_texture *native_texture = metalexp_require_texture(native_texture_handle, "upload");
	if (native_texture->depth_texture) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal RGBA8 upload cannot target a depth texture."
			userInfo:nil];
	}
	id<MTLTexture> texture = metalexp_texture_object(native_texture);
	if (mip_level < 0 || (uint32_t)mip_level >= native_texture->mip_levels) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture upload received an invalid mip level."
			userInfo:nil];
	}
	if (layer < 0 || (uint32_t)layer >= native_texture->depth_or_layers) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture upload received an invalid layer."
			userInfo:nil];
	}

	NSUInteger mip_width = metalexp_mip_dimension(native_texture->width, mip_level);
	NSUInteger mip_height = metalexp_mip_dimension(native_texture->height, mip_level);
	if ((NSUInteger)width != mip_width || (NSUInteger)height != mip_height) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture upload dimensions do not match the requested mip level."
			userInfo:nil];
	}

	jlong required_bytes = (jlong)width * (jlong)height * 4L;
	if (capacity < required_bytes) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture upload pixel buffer is smaller than the requested RGBA8 mip payload."
			userInfo:nil];
	}

	if (native_texture->cubemap_compatible || native_texture->depth_or_layers > 1) {
		[texture replaceRegion:MTLRegionMake2D(0, 0, mip_width, mip_height)
			mipmapLevel:(NSUInteger)mip_level
			slice:(NSUInteger)layer
			withBytes:pixels
			bytesPerRow:(NSUInteger)width * 4U
			bytesPerImage:(NSUInteger)width * (NSUInteger)height * 4U];
	} else {
		[texture replaceRegion:MTLRegionMake2D(0, 0, mip_width, mip_height)
			mipmapLevel:(NSUInteger)mip_level
			withBytes:pixels
			bytesPerRow:(NSUInteger)width * 4U];
	}
}

void metalexp_release_texture(jlong native_texture_handle) {
	if (native_texture_handle == 0) {
		return;
	}

	metalexp_native_texture *native_texture = (metalexp_native_texture *)(uintptr_t)native_texture_handle;
	if (native_texture->texture != NULL) {
		(void)CFBridgingRelease(native_texture->texture);
	}
	free(native_texture);
}
