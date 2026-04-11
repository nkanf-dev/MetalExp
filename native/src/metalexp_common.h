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
	METALEXP_PIPELINE_GUI_TEXTURED_PREMULTIPLIED_ALPHA = 5
};

typedef struct metalexp_host_surface {
	void *view;
	void *layer;
	void *original_layer;
	void *current_drawable;
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
} metalexp_native_texture;

typedef struct metalexp_command_context {
	void *command_queue;
	void *command_buffer;
} metalexp_command_context;

typedef struct metalexp_projection_uniform {
	matrix_float4x4 proj_mat;
} metalexp_projection_uniform;

typedef struct metalexp_dynamic_transforms_uniform {
	matrix_float4x4 model_view_mat;
	vector_float4 color_modulator;
	vector_float3 model_offset;
	float model_offset_padding;
	matrix_float4x4 texture_mat;
} metalexp_dynamic_transforms_uniform;

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
void metalexp_release_command_context(jlong native_command_context_handle);
id<MTLCommandBuffer> metalexp_command_buffer_for_context(jlong native_command_context_handle, id<MTLDevice> device, const char *operation_name);

jobject metalexp_probe(JNIEnv *env);
jobject metalexp_probe_surface(JNIEnv *env, jlong cocoa_window_handle, jlong cocoa_view_handle);
jobject metalexp_bootstrap_surface(JNIEnv *env, jlong cocoa_window_handle, jlong cocoa_view_handle);
void metalexp_restore_bootstrapped_surface(metalexp_host_surface *surface);
void metalexp_configure_surface(jlong native_surface_handle, jint width, jint height, jboolean vsync);
void metalexp_acquire_surface(jlong native_surface_handle);
void metalexp_present_surface(jlong native_surface_handle);
void metalexp_blit_surface_rgba8(jlong native_surface_handle, const void *pixels, jint width, jint height, jlong capacity);
void metalexp_blit_surface_texture(jlong native_command_context_handle, jlong native_surface_handle, jlong native_texture_handle);

jlong metalexp_create_texture(jint width, jint height, jint depth_or_layers, jint mip_levels, jboolean render_attachment, jboolean shader_read, jboolean cubemap_compatible);
void metalexp_upload_texture_rgba8(jlong native_texture_handle, jint mip_level, jint layer, const void *pixels, jint width, jint height, jlong capacity);
void metalexp_release_texture(jlong native_texture_handle);

id<MTLSamplerState> metalexp_sampler_state(id<MTLDevice> device, BOOL linear_filtering, BOOL repeat_u, BOOL repeat_v);
void metalexp_draw_gui_pass(
	jlong native_command_context_handle,
	jlong native_target_texture_handle,
	jint pipeline_kind,
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
