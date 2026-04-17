#include "metalexp_common.h"

void metalexp_read_texture_rgba8(
	jlong native_texture_handle,
	jint mip_level,
	jint layer,
	jint src_x,
	jint src_y,
	jint width,
	jint height,
	void *pixels,
	jlong capacity
) {
	if (pixels == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture readback requires a direct destination buffer."
			userInfo:nil];
	}

	metalexp_native_texture *native_texture = metalexp_require_texture(native_texture_handle, "readback");
	if (native_texture->depth_texture) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal RGBA8 readback cannot target a depth texture."
			userInfo:nil];
	}

	if (mip_level < 0 || (uint32_t)mip_level >= native_texture->mip_levels) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture readback received an invalid mip level."
			userInfo:nil];
	}
	if (layer < 0 || (uint32_t)layer >= native_texture->depth_or_layers) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture readback received an invalid layer."
			userInfo:nil];
	}
	if (width <= 0 || height <= 0) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture readback requires a positive width and height."
			userInfo:nil];
	}

	NSUInteger mip_width = metalexp_mip_dimension(native_texture->width, mip_level);
	NSUInteger mip_height = metalexp_mip_dimension(native_texture->height, mip_level);
	if (src_x < 0 || src_y < 0 || (NSUInteger)(src_x + width) > mip_width || (NSUInteger)(src_y + height) > mip_height) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture readback region exceeded the requested mip bounds."
			userInfo:nil];
	}

	jlong required_bytes = (jlong)width * (jlong)height * 4L;
	if (capacity < required_bytes) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture readback destination buffer is smaller than the requested RGBA8 payload."
			userInfo:nil];
	}

	id<MTLTexture> texture = metalexp_texture_object(native_texture);
	MTLRegion region = MTLRegionMake2D((NSUInteger)src_x, (NSUInteger)src_y, (NSUInteger)width, (NSUInteger)height);
	if (native_texture->cubemap_compatible || native_texture->depth_or_layers > 1) {
		[texture getBytes:pixels
			bytesPerRow:(NSUInteger)width * 4U
			bytesPerImage:(NSUInteger)width * (NSUInteger)height * 4U
			fromRegion:region
			mipmapLevel:(NSUInteger)mip_level
			slice:(NSUInteger)layer];
	} else {
		[texture getBytes:pixels
			bytesPerRow:(NSUInteger)width * 4U
			fromRegion:region
			mipmapLevel:(NSUInteger)mip_level];
	}
}
