#include "metalexp_common.h"

JNIEXPORT jobject JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_probe0(JNIEnv *env, jclass clazz) {
	(void) clazz;

	@autoreleasepool {
		return metalexp_probe(env);
	}
}

JNIEXPORT jobject JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_probeSurface0(JNIEnv *env, jclass clazz, jlong cocoa_window_handle, jlong cocoa_view_handle) {
	(void) clazz;

	@autoreleasepool {
		return metalexp_probe_surface(env, cocoa_window_handle, cocoa_view_handle);
	}
}

JNIEXPORT jobject JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_bootstrapSurface0(JNIEnv *env, jclass clazz, jlong cocoa_window_handle, jlong cocoa_view_handle) {
	(void) clazz;

	@autoreleasepool {
		return metalexp_bootstrap_surface(env, cocoa_window_handle, cocoa_view_handle);
	}
}

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_releaseSurface0(JNIEnv *env, jclass clazz, jlong native_surface_handle) {
	(void) env;
	(void) clazz;

	if (native_surface_handle == 0) {
		return;
	}

	@autoreleasepool {
		metalexp_restore_bootstrapped_surface((metalexp_host_surface *)(uintptr_t)native_surface_handle);
	}
}

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_configureSurface0(JNIEnv *env, jclass clazz, jlong native_surface_handle, jint width, jint height, jboolean vsync) {
	(void) env;
	(void) clazz;

	@autoreleasepool {
		metalexp_configure_surface(native_surface_handle, width, height, vsync);
	}
}

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_acquireSurface0(JNIEnv *env, jclass clazz, jlong native_surface_handle) {
	(void) env;
	(void) clazz;

	@autoreleasepool {
		metalexp_acquire_surface(native_surface_handle);
	}
}

JNIEXPORT jlong JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_createCommandContext0(JNIEnv *env, jclass clazz) {
	(void) env;
	(void) clazz;

	@autoreleasepool {
		return metalexp_create_command_context();
	}
}

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_submitCommandContext0(JNIEnv *env, jclass clazz, jlong native_command_context_handle) {
	(void) env;
	(void) clazz;

	@autoreleasepool {
		metalexp_submit_command_context(native_command_context_handle);
	}
}

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_releaseCommandContext0(JNIEnv *env, jclass clazz, jlong native_command_context_handle) {
	(void) env;
	(void) clazz;

	@autoreleasepool {
		metalexp_release_command_context(native_command_context_handle);
	}
}

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_blitSurfaceRgba80(JNIEnv *env, jclass clazz, jlong native_surface_handle, jobject rgba_pixels, jint width, jint height) {
	(void) clazz;

	@autoreleasepool {
		void *pixels = (*env)->GetDirectBufferAddress(env, rgba_pixels);
		jlong capacity = rgba_pixels == NULL ? 0L : (*env)->GetDirectBufferCapacity(env, rgba_pixels);
		metalexp_blit_surface_rgba8(native_surface_handle, pixels, width, height, capacity);
	}
}

JNIEXPORT jlong JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_createTexture0(
	JNIEnv *env,
	jclass clazz,
	jint width,
	jint height,
	jint depth_or_layers,
	jint mip_levels,
	jboolean render_attachment,
	jboolean shader_read,
	jboolean cubemap_compatible
) {
	(void) env;
	(void) clazz;

	@autoreleasepool {
		return metalexp_create_texture(width, height, depth_or_layers, mip_levels, render_attachment, shader_read, cubemap_compatible);
	}
}

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_uploadTextureRgba80(
	JNIEnv *env,
	jclass clazz,
	jlong native_texture_handle,
	jint mip_level,
	jint layer,
	jobject rgba_pixels,
	jint width,
	jint height
) {
	(void) clazz;

	@autoreleasepool {
		void *pixels = (*env)->GetDirectBufferAddress(env, rgba_pixels);
		jlong capacity = rgba_pixels == NULL ? 0L : (*env)->GetDirectBufferCapacity(env, rgba_pixels);
		metalexp_upload_texture_rgba8(native_texture_handle, mip_level, layer, pixels, width, height, capacity);
	}
}

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_releaseTexture0(JNIEnv *env, jclass clazz, jlong native_texture_handle) {
	(void) env;
	(void) clazz;

	@autoreleasepool {
		metalexp_release_texture(native_texture_handle);
	}
}

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_drawGuiPass0(
	JNIEnv *env,
	jclass clazz,
	jlong native_command_context_handle,
	jlong native_target_texture_handle,
	jint pipeline_kind,
	jobject vertex_data,
	jint vertex_stride,
	jint base_vertex,
	jobject index_data,
	jint index_type_bytes,
	jint first_index,
	jint index_count,
	jobject projection_uniform,
	jobject dynamic_transforms_uniform,
	jlong native_sampler0_texture_handle,
	jboolean linear_filtering,
	jboolean repeat_u,
	jboolean repeat_v,
	jboolean scissor_enabled,
	jint scissor_x,
	jint scissor_y,
	jint scissor_width,
	jint scissor_height
) {
	(void) clazz;

	@autoreleasepool {
		void *vertex_bytes = (*env)->GetDirectBufferAddress(env, vertex_data);
		jlong vertex_capacity = vertex_data == NULL ? 0L : (*env)->GetDirectBufferCapacity(env, vertex_data);
		void *index_bytes = (*env)->GetDirectBufferAddress(env, index_data);
		jlong index_capacity = index_data == NULL ? 0L : (*env)->GetDirectBufferCapacity(env, index_data);
		void *projection_bytes = (*env)->GetDirectBufferAddress(env, projection_uniform);
		jlong projection_capacity = projection_uniform == NULL ? 0L : (*env)->GetDirectBufferCapacity(env, projection_uniform);
		void *dynamic_bytes = (*env)->GetDirectBufferAddress(env, dynamic_transforms_uniform);
		jlong dynamic_capacity = dynamic_transforms_uniform == NULL ? 0L : (*env)->GetDirectBufferCapacity(env, dynamic_transforms_uniform);

		metalexp_draw_gui_pass(
			native_command_context_handle,
			native_target_texture_handle,
			pipeline_kind,
			vertex_bytes,
			vertex_capacity,
			vertex_stride,
			base_vertex,
			index_bytes,
			index_capacity,
			index_type_bytes,
			first_index,
			index_count,
			projection_bytes,
			projection_capacity,
			dynamic_bytes,
			dynamic_capacity,
			native_sampler0_texture_handle,
			linear_filtering,
			repeat_u,
			repeat_v,
			scissor_enabled,
			scissor_x,
			scissor_y,
			scissor_width,
			scissor_height
		);
	}
}

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_blitAnimatedSprite0(
	JNIEnv *env,
	jclass clazz,
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
) {
	(void) env;
	(void) clazz;

	@autoreleasepool {
		metalexp_blit_animated_sprite(
			native_command_context_handle,
			native_target_texture_handle,
			target_mip_level,
			native_source_texture_handle,
			source_mip_level,
			dst_x,
			dst_y,
			dst_width,
			dst_height,
			local_u_min,
			local_v_min,
			local_u_max,
			local_v_max,
			u_padding,
			v_padding,
			linear_filtering,
			repeat_u,
			repeat_v
		);
	}
}

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_blitSurfaceTexture0(
	JNIEnv *env,
	jclass clazz,
	jlong native_command_context_handle,
	jlong native_surface_handle,
	jlong native_texture_handle
) {
	(void) env;
	(void) clazz;

	@autoreleasepool {
		metalexp_blit_surface_texture(native_command_context_handle, native_surface_handle, native_texture_handle);
	}
}

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_presentSurface0(JNIEnv *env, jclass clazz, jlong native_surface_handle) {
	(void) env;
	(void) clazz;

	@autoreleasepool {
		metalexp_present_surface(native_surface_handle);
	}
}
