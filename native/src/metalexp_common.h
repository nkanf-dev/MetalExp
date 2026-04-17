#ifndef METALEXP_COMMON_H
#define METALEXP_COMMON_H

#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <simd/simd.h>

#import <AppKit/AppKit.h>
#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>

enum {
	METALEXP_OUTCOME_READY = 0,
	METALEXP_OUTCOME_STUB_UNIMPLEMENTED = 1,
	METALEXP_OUTCOME_ERROR = 2
};

enum {
	METALEXP_PIPELINE_GUI_COLOR = 1,
	METALEXP_PIPELINE_GUI_TEXTURED = 2,
	METALEXP_PIPELINE_PANORAMA = 3,
	METALEXP_PIPELINE_GUI_TEXTURED_OPAQUE_BACKGROUND = 4,
	METALEXP_PIPELINE_GUI_TEXTURED_PREMULTIPLIED_ALPHA = 5,
	METALEXP_PIPELINE_WORLD_OPAQUE = 6,
	METALEXP_PIPELINE_SKY = 7,
	METALEXP_PIPELINE_POST_BLIT = 8,
	METALEXP_PIPELINE_WORLD_TERRAIN_SOLID = 9,
	METALEXP_PIPELINE_WORLD_TERRAIN_CUTOUT = 10,
	METALEXP_PIPELINE_WORLD_TERRAIN_TRANSLUCENT = 11,
	METALEXP_PIPELINE_VIGNETTE = 12,
	METALEXP_PIPELINE_CROSSHAIR = 13,
	METALEXP_PIPELINE_SUNRISE_SUNSET = 14,
	METALEXP_PIPELINE_CELESTIAL = 15,
	METALEXP_PIPELINE_STARS = 16
};

enum {
	METALEXP_PRIMITIVE_TRIANGLE = 1,
	METALEXP_PRIMITIVE_TRIANGLE_STRIP = 2,
	METALEXP_PRIMITIVE_LINE = 3,
	METALEXP_PRIMITIVE_LINE_STRIP = 4,
	METALEXP_PRIMITIVE_POINT = 5
};

enum {
	METALEXP_RASTER_SHADER_UNKNOWN = 0,
	METALEXP_RASTER_SHADER_GUI_COLOR = 1,
	METALEXP_RASTER_SHADER_GUI_TEXTURED = 2,
	METALEXP_RASTER_SHADER_PANORAMA = 3,
	METALEXP_RASTER_SHADER_SKY = 4,
	METALEXP_RASTER_SHADER_STARS = 5,
	METALEXP_RASTER_SHADER_POSITION_COLOR = 6,
	METALEXP_RASTER_SHADER_POSITION_TEX = 7,
	METALEXP_RASTER_SHADER_LINE = 8
};

enum {
	METALEXP_RASTER_LAYOUT_UNKNOWN = 0,
	METALEXP_RASTER_LAYOUT_POSITION = 1,
	METALEXP_RASTER_LAYOUT_POSITION_COLOR = 2,
	METALEXP_RASTER_LAYOUT_POSITION_TEX = 3,
	METALEXP_RASTER_LAYOUT_POSITION_TEX_COLOR = 4,
	METALEXP_RASTER_LAYOUT_POSITION_COLOR_NORMAL_LINE_WIDTH = 5,
	METALEXP_RASTER_LAYOUT_BLOCK = 6
};

enum {
	METALEXP_RASTER_COMPARE_ALWAYS_PASS = 1,
	METALEXP_RASTER_COMPARE_LESS_THAN = 2,
	METALEXP_RASTER_COMPARE_LESS_THAN_OR_EQUAL = 3,
	METALEXP_RASTER_COMPARE_EQUAL = 4,
	METALEXP_RASTER_COMPARE_NOT_EQUAL = 5,
	METALEXP_RASTER_COMPARE_GREATER_THAN_OR_EQUAL = 6,
	METALEXP_RASTER_COMPARE_GREATER_THAN = 7,
	METALEXP_RASTER_COMPARE_NEVER_PASS = 8
};

enum {
	METALEXP_RASTER_BLEND_OP_ADD = 1,
	METALEXP_RASTER_BLEND_OP_SUBTRACT = 2,
	METALEXP_RASTER_BLEND_OP_REVERSE_SUBTRACT = 3,
	METALEXP_RASTER_BLEND_OP_MIN = 4,
	METALEXP_RASTER_BLEND_OP_MAX = 5
};

enum {
	METALEXP_RASTER_BLEND_FACTOR_CONSTANT_ALPHA = 1,
	METALEXP_RASTER_BLEND_FACTOR_CONSTANT_COLOR = 2,
	METALEXP_RASTER_BLEND_FACTOR_DST_ALPHA = 3,
	METALEXP_RASTER_BLEND_FACTOR_DST_COLOR = 4,
	METALEXP_RASTER_BLEND_FACTOR_ONE = 5,
	METALEXP_RASTER_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA = 6,
	METALEXP_RASTER_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR = 7,
	METALEXP_RASTER_BLEND_FACTOR_ONE_MINUS_DST_ALPHA = 8,
	METALEXP_RASTER_BLEND_FACTOR_ONE_MINUS_DST_COLOR = 9,
	METALEXP_RASTER_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA = 10,
	METALEXP_RASTER_BLEND_FACTOR_ONE_MINUS_SRC_COLOR = 11,
	METALEXP_RASTER_BLEND_FACTOR_SRC_ALPHA = 12,
	METALEXP_RASTER_BLEND_FACTOR_SRC_ALPHA_SATURATE = 13,
	METALEXP_RASTER_BLEND_FACTOR_SRC_COLOR = 14,
	METALEXP_RASTER_BLEND_FACTOR_ZERO = 15
};

enum {
	METALEXP_RASTER_FLAG_BLEND_ENABLED = 1 << 0,
	METALEXP_RASTER_FLAG_SAMPLER0_REQUIRED = 1 << 1,
	METALEXP_RASTER_FLAG_SAMPLER2_REQUIRED = 1 << 2,
	METALEXP_RASTER_FLAG_WANTS_DEPTH_TEXTURE = 1 << 3,
	METALEXP_RASTER_FLAG_CULL = 1 << 4,
	METALEXP_RASTER_FLAG_DEPTH_WRITE = 1 << 5
};

typedef struct metalexp_raster_pipeline_desc {
	uint32_t shader_family;
	uint32_t vertex_layout;
	uint32_t primitive_topology;
	uint32_t color_write_mask;
	uint32_t flags;
	uint32_t depth_compare;
	float depth_bias_scale_factor;
	float depth_bias_constant;
	uint32_t color_blend_op;
	uint32_t alpha_blend_op;
	uint32_t src_color_factor;
	uint32_t dst_color_factor;
	uint32_t src_alpha_factor;
	uint32_t dst_alpha_factor;
} metalexp_raster_pipeline_desc;

typedef struct metalexp_host_surface {
	void *view;
	void *layer;
	void *original_layer;
	void *current_drawable;
	BOOL current_drawable_present_scheduled;
	BOOL original_wants_layer;
	BOOL display_sync_enabled;
} metalexp_host_surface;

typedef struct metalexp_native_texture {
	void *texture;
	uint32_t width;
	uint32_t height;
	uint32_t depth_or_layers;
	uint32_t mip_levels;
	BOOL cubemap_compatible;
	BOOL depth_texture;
} metalexp_native_texture;

typedef struct metalexp_native_buffer {
	void *buffer;
	uint32_t length;
} metalexp_native_buffer;

typedef struct metalexp_command_context {
	void *command_queue;
	void *command_buffer;
	void *vertex_staging_blocks;
	void *index_staging_blocks;
	void *uniform_staging_blocks;
} metalexp_command_context;

typedef struct metalexp_staging_buffer_slice {
	id<MTLBuffer> buffer;
	NSUInteger offset;
} metalexp_staging_buffer_slice;

typedef struct metalexp_projection_uniform {
	matrix_float4x4 proj_mat;
} metalexp_projection_uniform;

typedef struct metalexp_dynamic_transforms_uniform {
	matrix_float4x4 model_view_mat;
	vector_float4 color_modulator;
	vector_float4 model_offset;
	matrix_float4x4 texture_mat;
} metalexp_dynamic_transforms_uniform;

typedef struct metalexp_blit_config_uniform {
	vector_float4 color_modulate;
} metalexp_blit_config_uniform;

typedef struct metalexp_world_uniform {
	matrix_float4x4 model_view_mat;
	vector_float4 fog_color;
	vector_float4 chunk_position_visibility;
	vector_float4 camera_block_pos;
	vector_float4 camera_offset;
	vector_float4 fog_params;
	vector_float4 terrain_params;
} metalexp_world_uniform;

static inline NSUInteger metalexp_mip_dimension(uint32_t base_dimension, jint mip_level) {
	return MAX(1U, base_dimension >> mip_level);
}

static inline metalexp_native_texture *metalexp_require_texture(jlong native_texture_handle, const char *operation_name) {
	if (native_texture_handle == 0) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:[NSString stringWithFormat:@"Metal texture %s requires a non-zero native handle.", operation_name]
			userInfo:nil];
	}

	metalexp_native_texture *native_texture = (metalexp_native_texture *)(uintptr_t)native_texture_handle;
	if (native_texture->texture == NULL || native_texture->width == 0 || native_texture->height == 0) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:[NSString stringWithFormat:@"Metal texture %s received an incomplete native texture record.", operation_name]
			userInfo:nil];
	}

	return native_texture;
}

static inline id<MTLTexture> metalexp_texture_object(metalexp_native_texture *native_texture) {
	return (__bridge id<MTLTexture>)native_texture->texture;
}

static inline metalexp_native_buffer *metalexp_require_buffer(jlong native_buffer_handle, const char *operation_name) {
	if (native_buffer_handle == 0) {
		@throw [NSException exceptionWithName:@"MetalExpBufferException"
			reason:[NSString stringWithFormat:@"Metal buffer %s requires a non-zero native handle.", operation_name]
			userInfo:nil];
	}

	metalexp_native_buffer *native_buffer = (metalexp_native_buffer *)(uintptr_t)native_buffer_handle;
	if (native_buffer->buffer == NULL || native_buffer->length == 0) {
		@throw [NSException exceptionWithName:@"MetalExpBufferException"
			reason:[NSString stringWithFormat:@"Metal buffer %s received an incomplete native buffer record.", operation_name]
			userInfo:nil];
	}

	return native_buffer;
}

static inline id<MTLBuffer> metalexp_buffer_object(metalexp_native_buffer *native_buffer) {
	return (__bridge id<MTLBuffer>)native_buffer->buffer;
}

static inline metalexp_command_context *metalexp_require_command_context(jlong native_command_context_handle, const char *operation_name) {
	if (native_command_context_handle == 0) {
		@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
			reason:[NSString stringWithFormat:@"Metal command context %s requires a non-zero native handle.", operation_name]
			userInfo:nil];
	}

	metalexp_command_context *command_context = (metalexp_command_context *)(uintptr_t)native_command_context_handle;
	if (command_context->command_queue == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
			reason:[NSString stringWithFormat:@"Metal command context %s received an incomplete native command context record.", operation_name]
			userInfo:nil];
	}

	return command_context;
}

jlong metalexp_create_command_context(void);
void metalexp_submit_command_context(jlong native_command_context_handle);
BOOL metalexp_is_command_context_complete(jlong native_command_context_handle);
void metalexp_wait_for_command_context(jlong native_command_context_handle);
void metalexp_release_command_context(jlong native_command_context_handle);
id<MTLCommandBuffer> metalexp_command_buffer_for_context(jlong native_command_context_handle, id<MTLDevice> device, const char *operation_name);
metalexp_staging_buffer_slice metalexp_stage_vertex_bytes(jlong native_command_context_handle, id<MTLDevice> device, const void *bytes, NSUInteger length, const char *operation_name);
metalexp_staging_buffer_slice metalexp_stage_index_bytes(jlong native_command_context_handle, id<MTLDevice> device, const void *bytes, NSUInteger length, const char *operation_name);
metalexp_staging_buffer_slice metalexp_stage_uniform_bytes(jlong native_command_context_handle, id<MTLDevice> device, const void *bytes, NSUInteger length, const char *operation_name);

jobject metalexp_probe(JNIEnv *env);
jobject metalexp_probe_surface(JNIEnv *env, jlong cocoa_window_handle, jlong cocoa_view_handle);
jobject metalexp_bootstrap_surface(JNIEnv *env, jlong cocoa_window_handle, jlong cocoa_view_handle);
void metalexp_restore_bootstrapped_surface(metalexp_host_surface *surface);
void metalexp_configure_surface(jlong native_surface_handle, jint width, jint height, jboolean vsync);
void metalexp_acquire_surface(jlong native_surface_handle);
void metalexp_present_surface(jlong native_surface_handle);
void metalexp_blit_surface_rgba8(jlong native_surface_handle, const void *pixels, jint width, jint height, jlong capacity);
void metalexp_blit_surface_texture(jlong native_command_context_handle, jlong native_surface_handle, jlong native_texture_handle);
void metalexp_present_texture_to_drawable(id<MTLCommandBuffer> command_buffer, id<MTLTexture> source_texture, id<MTLTexture> drawable_texture);

jlong metalexp_create_buffer(jlong length);
void metalexp_upload_buffer(jlong native_buffer_handle, jlong offset, const void *bytes, jlong capacity);
void metalexp_release_buffer(jlong native_buffer_handle);

jlong metalexp_create_texture(
	jint width,
	jint height,
	jint depth_or_layers,
	jint mip_levels,
	jboolean render_attachment,
	jboolean shader_read,
	jboolean cubemap_compatible,
	jboolean depth_texture
);
void metalexp_upload_texture_rgba8(jlong native_texture_handle, jint mip_level, jint layer, const void *pixels, jint width, jint height, jlong capacity);
void metalexp_read_texture_rgba8(jlong native_texture_handle, jint mip_level, jint layer, jint src_x, jint src_y, jint width, jint height, void *pixels, jlong capacity);
void metalexp_release_texture(jlong native_texture_handle);

id<MTLSamplerState> metalexp_sampler_state(id<MTLDevice> device, BOOL linear_filtering, BOOL repeat_u, BOOL repeat_v);
void metalexp_draw_gui_pass(
	jlong native_command_context_handle,
	jlong native_target_texture_handle,
	jint pipeline_kind,
	jint primitive_kind,
	const void *vertex_bytes,
	jlong vertex_capacity,
	jint vertex_stride,
	jint base_vertex,
	const void *index_bytes,
	jlong index_capacity,
	jint index_type_bytes,
	jint first_index,
	jint index_count,
	const void *projection_bytes,
	jlong projection_capacity,
	const void *dynamic_bytes,
	jlong dynamic_capacity,
	jlong native_sampler0_texture_handle,
	jboolean linear_filtering,
	jboolean repeat_u,
	jboolean repeat_v,
	jboolean scissor_enabled,
	jint scissor_x,
	jint scissor_y,
	jint scissor_width,
	jint scissor_height
);

void metalexp_draw_raster_pass(
	jlong native_command_context_handle,
	jlong native_color_texture_handle,
	jlong native_depth_texture_handle,
	const metalexp_raster_pipeline_desc *pipeline_desc,
	const void *vertex_bytes,
	jlong vertex_capacity,
	jint vertex_stride,
	jint base_vertex,
	const void *index_bytes,
	jlong index_capacity,
	jint index_type_bytes,
	jint first_index,
	jint index_count,
	const void *projection_bytes,
	jlong projection_capacity,
	const void *dynamic_bytes,
	jlong dynamic_capacity,
	jlong native_sampler0_texture_handle,
	jboolean linear_filtering,
	jboolean repeat_u,
	jboolean repeat_v,
	jboolean scissor_enabled,
	jint scissor_x,
	jint scissor_y,
	jint scissor_width,
	jint scissor_height
);

void metalexp_draw_world_pass(
	jlong native_command_context_handle,
	jlong native_color_texture_handle,
	jlong native_depth_texture_handle,
	jboolean clear_depth,
	jfloat clear_depth_value,
	jint pipeline_kind,
	jlong native_vertex_buffer_handle,
	jint vertex_stride,
	jint base_vertex,
	jlong native_index_buffer_handle,
	jint index_type_bytes,
	jint first_index,
	jint index_count,
	const void *projection_bytes,
	jlong projection_capacity,
	const void *world_bytes,
	jlong world_capacity,
	jlong native_sampler0_texture_handle,
	jlong native_sampler2_texture_handle,
	jboolean linear_filtering,
	jboolean repeat_u,
	jboolean repeat_v,
	jboolean sampler2_linear_filtering,
	jboolean sampler2_repeat_u,
	jboolean sampler2_repeat_v
);

void metalexp_blit_animated_sprite(
	jlong native_command_context_handle,
	jlong native_target_texture_handle,
	jint target_mip_level,
	jlong native_source_texture_handle,
	jint source_mip_level,
	jint dst_x,
	jint dst_y,
	jint dst_width,
	jint dst_height,
	jfloat local_u_min,
	jfloat local_v_min,
	jfloat local_u_max,
	jfloat local_v_max,
	jfloat u_padding,
	jfloat v_padding,
	jboolean linear_filtering,
	jboolean repeat_u,
	jboolean repeat_v
);

#endif
