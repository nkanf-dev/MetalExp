#include "metalexp_common.h"

static jobjectArray metalexp_create_string_array(JNIEnv *env, const char *capability) {
	jclass string_class = (*env)->FindClass(env, "java/lang/String");
	if (string_class == NULL) {
		return NULL;
	}

	jsize size = capability == NULL ? 0 : 1;
	jobjectArray result = (*env)->NewObjectArray(env, size, string_class, NULL);
	if (result == NULL || capability == NULL) {
		return result;
	}

	jstring value = (*env)->NewStringUTF(env, capability);
	if (value == NULL) {
		return NULL;
	}

	(*env)->SetObjectArrayElement(env, result, 0, value);
	return result;
}

static jobject metalexp_create_probe_result(JNIEnv *env, jint outcome_code, const char *detail, const char *missing_capability) {
	jclass result_class = (*env)->FindClass(env, "dev/nkanf/metalexp/bridge/NativeMetalBridgeProbeResult");
	if (result_class == NULL) {
		return NULL;
	}

	jmethodID constructor = (*env)->GetMethodID(env, result_class, "<init>", "(ILjava/lang/String;[Ljava/lang/String;)V");
	if (constructor == NULL) {
		return NULL;
	}

	jobjectArray missing = metalexp_create_string_array(env, missing_capability);
	if (missing == NULL) {
		return NULL;
	}

	jstring detail_string = (*env)->NewStringUTF(env, detail);
	if (detail_string == NULL) {
		return NULL;
	}

	return (*env)->NewObject(env, result_class, constructor, outcome_code, detail_string, missing);
}

static jobject metalexp_create_surface_bootstrap_result(
	JNIEnv *env,
	jint outcome_code,
	jint drawable_width,
	jint drawable_height,
	jdouble contents_scale,
	const char *detail,
	const char *missing_capability,
	jlong native_surface_handle
) {
	jclass result_class = (*env)->FindClass(env, "dev/nkanf/metalexp/bridge/NativeMetalBridgeSurfaceBootstrapResult");
	if (result_class == NULL) {
		return NULL;
	}

	jmethodID constructor = (*env)->GetMethodID(env, result_class, "<init>", "(IIIDLjava/lang/String;[Ljava/lang/String;J)V");
	if (constructor == NULL) {
		return NULL;
	}

	jobjectArray missing = metalexp_create_string_array(env, missing_capability);
	if (missing == NULL) {
		return NULL;
	}

	jstring detail_string = (*env)->NewStringUTF(env, detail);
	if (detail_string == NULL) {
		return NULL;
	}

	return (*env)->NewObject(env, result_class, constructor, outcome_code, drawable_width, drawable_height, contents_scale, detail_string, missing, native_surface_handle);
}

static jobject metalexp_validate_surface_host(
	JNIEnv *env,
	jlong cocoa_window_handle,
	jlong cocoa_view_handle,
	NSWindow **window_out,
	NSView **view_out,
	id<MTLDevice> *device_out
) {
	if (cocoa_window_handle == 0) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"GLFW did not expose a Cocoa NSWindow handle for the Metal backend.",
			"cocoa_window_handle"
		);
	}

	if (cocoa_view_handle == 0) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"GLFW did not expose a Cocoa NSView handle for the Metal backend.",
			"cocoa_view_handle"
		);
	}

	NSWindow *window = (__bridge NSWindow *)(void *)(uintptr_t)cocoa_window_handle;
	NSView *view = (__bridge NSView *)(void *)(uintptr_t)cocoa_view_handle;
	if (window == nil) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"Cocoa NSWindow handle resolved to nil.",
			"cocoa_window"
		);
	}

	if (view == nil) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"Cocoa NSView handle resolved to nil.",
			"cocoa_view"
		);
	}

	if (view.window == nil) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"Cocoa NSView is not attached to an NSWindow.",
			"cocoa_view_window_link"
		);
	}

	if (view.window != window) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"GLFW Cocoa NSView is not attached to the expected NSWindow.",
			"cocoa_view_window_mismatch"
		);
	}

	if (![NSThread isMainThread]) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"Metal surface probing must run on the main AppKit thread.",
			"cocoa_main_thread"
		);
	}

	Class metalLayerClass = [CAMetalLayer class];
	if (metalLayerClass == Nil) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"CAMetalLayer is unavailable on this host.",
			"ca_metal_layer"
		);
	}

	if (![view respondsToSelector:@selector(setLayer:)] || ![view respondsToSelector:@selector(setWantsLayer:)]) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"Cocoa NSView does not support layer-backed CAMetalLayer hosting.",
			"ca_metal_layer_host_view"
		);
	}

	id<MTLDevice> device = MTLCreateSystemDefaultDevice();
	if (device == nil) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"No default Metal device is available while probing the Cocoa host surface.",
			"mtl_device"
		);
	}

	*window_out = window;
	*view_out = view;
	*device_out = device;
	return NULL;
}

static CAMetalLayer *metalexp_create_layer(NSWindow *window, NSView *view, id<MTLDevice> device) {
	CAMetalLayer *layer = [CAMetalLayer layer];
	if (layer == nil) {
		return nil;
	}

	layer.device = device;
	layer.opaque = YES;
	CGFloat contents_scale = window.screen != nil ? window.screen.backingScaleFactor : 1.0;
	if (contents_scale <= 0.0) {
		contents_scale = 1.0;
	}

	layer.contentsScale = contents_scale;
	layer.frame = view.bounds;
	layer.drawableSize = CGSizeMake(view.bounds.size.width * contents_scale, view.bounds.size.height * contents_scale);
	return layer;
}

static metalexp_host_surface *metalexp_require_surface(jlong native_surface_handle, const char *operation_name) {
	if (native_surface_handle == 0) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:[NSString stringWithFormat:@"Metal surface %s requires a non-zero native handle.", operation_name]
			userInfo:nil];
	}

	metalexp_host_surface *surface = (metalexp_host_surface *)(uintptr_t)native_surface_handle;
	if (surface->view == NULL || surface->layer == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:[NSString stringWithFormat:@"Metal surface %s received an incomplete native surface record.", operation_name]
			userInfo:nil];
	}

	return surface;
}

jobject metalexp_probe(JNIEnv *env) {
	Class metalLayerClass = [CAMetalLayer class];
	if (metalLayerClass == Nil) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"CAMetalLayer is unavailable on this host.",
			"ca_metal_layer"
		);
	}

	id<MTLDevice> device = MTLCreateSystemDefaultDevice();
	if (device == nil) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"No default Metal device is available on this host.",
			"mtl_device"
		);
	}

	CAMetalLayer *layer = [CAMetalLayer layer];
	if (layer == nil) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"CAMetalLayer exists but could not be instantiated.",
			"ca_metal_layer_instance"
		);
	}

	layer.device = device;

	id<MTLCommandQueue> command_queue = [device newCommandQueue];
	if (command_queue == nil) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"Default Metal device is available, but command queue creation failed.",
			"mtl_command_queue"
		);
	}

	NSString *device_name = device.name;
	NSString *detail = [NSString stringWithFormat:
		@"Metal probe succeeded: device=%@, CAMetalLayer and MTLCommandQueue are available.",
		device_name == nil || device_name.length == 0 ? @"unknown" : device_name
	];

	return metalexp_create_probe_result(
		env,
		METALEXP_OUTCOME_READY,
		detail.UTF8String,
		NULL
	);
}

jobject metalexp_probe_surface(JNIEnv *env, jlong cocoa_window_handle, jlong cocoa_view_handle) {
	NSWindow *window = nil;
	NSView *view = nil;
	id<MTLDevice> device = nil;
	jobject validation_error = metalexp_validate_surface_host(env, cocoa_window_handle, cocoa_view_handle, &window, &view, &device);
	if (validation_error != NULL) {
		return validation_error;
	}

	CAMetalLayer *layer = metalexp_create_layer(window, view, device);
	if (layer == nil) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"CAMetalLayer exists but could not be instantiated for the Cocoa host surface probe.",
			"ca_metal_layer_instance"
		);
	}

	BOOL original_wants_layer = view.wantsLayer;
	CALayer *original_layer = view.layer;
	@try {
		[view setWantsLayer:YES];
		[view setLayer:layer];
		if (view.layer != layer) {
			return metalexp_create_probe_result(
				env,
				METALEXP_OUTCOME_ERROR,
				"Temporary CAMetalLayer attachment did not stick on the Cocoa NSView host.",
				"ca_metal_layer_attach"
			);
		}
	} @finally {
		[view setLayer:original_layer];
		[view setWantsLayer:original_wants_layer];
	}

	NSString *detail = [NSString stringWithFormat:
		@"Metal surface probe succeeded: window=%@, view=%@, temporary CAMetalLayer attachment and restoration both succeeded.",
		NSStringFromClass([window class]),
		NSStringFromClass([view class])
	];

	return metalexp_create_probe_result(
		env,
		METALEXP_OUTCOME_READY,
		detail.UTF8String,
		NULL
	);
}

jobject metalexp_bootstrap_surface(JNIEnv *env, jlong cocoa_window_handle, jlong cocoa_view_handle) {
	NSWindow *window = nil;
	NSView *view = nil;
	id<MTLDevice> device = nil;
	jobject validation_error = metalexp_validate_surface_host(env, cocoa_window_handle, cocoa_view_handle, &window, &view, &device);
	if (validation_error != NULL) {
		return metalexp_create_surface_bootstrap_result(
			env,
			METALEXP_OUTCOME_ERROR,
			0,
			0,
			0.0,
			"Metal surface bootstrap failed during Cocoa host validation.",
			"cocoa_surface_validation",
			0L
		);
	}

	CAMetalLayer *layer = metalexp_create_layer(window, view, device);
	if (layer == nil) {
		return metalexp_create_surface_bootstrap_result(
			env,
			METALEXP_OUTCOME_ERROR,
			0,
			0,
			0.0,
			"CAMetalLayer exists but could not be instantiated for the Cocoa host surface bootstrap.",
			"ca_metal_layer_instance",
			0L
		);
	}

	metalexp_host_surface *surface = calloc(1, sizeof(metalexp_host_surface));
	if (surface == NULL) {
		return metalexp_create_surface_bootstrap_result(
			env,
			METALEXP_OUTCOME_ERROR,
			0,
			0,
			0.0,
			"Failed to allocate native storage for the Metal host surface bootstrap.",
			"native_surface_storage",
			0L
		);
	}

	surface->view = (void *)CFBridgingRetain(view);
	surface->layer = (void *)CFBridgingRetain(layer);
	surface->original_layer = view.layer == nil ? NULL : (void *)CFBridgingRetain(view.layer);
	surface->original_wants_layer = view.wantsLayer;
	surface->current_drawable = NULL;
	surface->current_drawable_present_scheduled = NO;
	surface->display_sync_enabled = YES;

	@try {
		[view setWantsLayer:YES];
		[view setLayer:layer];
		if (view.layer != layer) {
			metalexp_restore_bootstrapped_surface(surface);
			return metalexp_create_surface_bootstrap_result(
				env,
				METALEXP_OUTCOME_ERROR,
				0,
				0,
				0.0,
				"Persistent CAMetalLayer attachment did not stick on the Cocoa NSView host.",
				"ca_metal_layer_attach",
				0L
			);
		}
	} @catch (NSException *exception) {
		metalexp_restore_bootstrapped_surface(surface);
		return metalexp_create_surface_bootstrap_result(
			env,
			METALEXP_OUTCOME_ERROR,
			0,
			0,
			0.0,
			exception.reason == nil ? "Metal surface bootstrap threw an Objective-C exception." : exception.reason.UTF8String,
			"cocoa_surface_bootstrap_exception",
			0L
		);
	}

	NSString *detail = [NSString stringWithFormat:
		@"Metal surface bootstrap succeeded: window=%@, view=%@, native CAMetalLayer host surface retained.",
		NSStringFromClass([window class]),
		NSStringFromClass([view class])
	];

	return metalexp_create_surface_bootstrap_result(
		env,
		METALEXP_OUTCOME_READY,
		(jint)(layer.drawableSize.width + 0.5),
		(jint)(layer.drawableSize.height + 0.5),
		layer.contentsScale,
		detail.UTF8String,
		NULL,
		(jlong)(uintptr_t)surface
	);
}

void metalexp_restore_bootstrapped_surface(metalexp_host_surface *surface) {
	if (surface == NULL) {
		return;
	}

	NSView *view = surface->view == NULL ? nil : (__bridge NSView *)surface->view;
	CALayer *original_layer = surface->original_layer == NULL ? nil : (__bridge CALayer *)surface->original_layer;
	if (view != nil) {
		[view setLayer:original_layer];
		[view setWantsLayer:surface->original_wants_layer];
	}

	if (surface->original_layer != NULL) {
		(void)CFBridgingRelease(surface->original_layer);
	}
	if (surface->current_drawable != NULL) {
		(void)CFBridgingRelease(surface->current_drawable);
	}
	if (surface->layer != NULL) {
		(void)CFBridgingRelease(surface->layer);
	}
	if (surface->view != NULL) {
		(void)CFBridgingRelease(surface->view);
	}

	free(surface);
}

void metalexp_configure_surface(jlong native_surface_handle, jint width, jint height, jboolean vsync) {
	if (![NSThread isMainThread]) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface configure must run on the main AppKit thread."
			userInfo:nil];
	}

	if (width <= 0 || height <= 0) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface configure requires positive drawable dimensions."
			userInfo:nil];
	}

	metalexp_host_surface *surface = metalexp_require_surface(native_surface_handle, "configure");
	CAMetalLayer *layer = (__bridge CAMetalLayer *)surface->layer;
	NSView *view = (__bridge NSView *)surface->view;
	CGFloat contents_scale = layer.contentsScale > 0.0 ? layer.contentsScale : 1.0;

	layer.frame = view.bounds;
	layer.drawableSize = CGSizeMake((CGFloat)width, (CGFloat)height);
	layer.contentsScale = contents_scale;
	layer.displaySyncEnabled = vsync == JNI_TRUE;
	surface->display_sync_enabled = vsync == JNI_TRUE;
}

void metalexp_acquire_surface(jlong native_surface_handle) {
	if (![NSThread isMainThread]) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface acquire must run on the main AppKit thread."
			userInfo:nil];
	}

	metalexp_host_surface *surface = metalexp_require_surface(native_surface_handle, "acquire");
	CAMetalLayer *layer = (__bridge CAMetalLayer *)surface->layer;
	if (surface->current_drawable != NULL) {
		(void)CFBridgingRelease(surface->current_drawable);
		surface->current_drawable = NULL;
	}
	surface->current_drawable_present_scheduled = NO;

	id<CAMetalDrawable> drawable = [layer nextDrawable];
	if (drawable == nil) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"CAMetalLayer did not provide a drawable during acquire."
			userInfo:nil];
	}

	surface->current_drawable = (void *)CFBridgingRetain(drawable);
	surface->current_drawable_present_scheduled = NO;
}

void metalexp_present_surface(jlong native_surface_handle) {
	if (![NSThread isMainThread]) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface present must run on the main AppKit thread."
			userInfo:nil];
	}

	metalexp_host_surface *surface = metalexp_require_surface(native_surface_handle, "present");
	if (surface->current_drawable == NULL) {
		return;
	}

	if (surface->current_drawable_present_scheduled) {
		(void)CFBridgingRelease(surface->current_drawable);
		surface->current_drawable = NULL;
		surface->current_drawable_present_scheduled = NO;
		return;
	}

	id<CAMetalDrawable> drawable = (__bridge id<CAMetalDrawable>)surface->current_drawable;
	[drawable present];
	(void)CFBridgingRelease(surface->current_drawable);
	surface->current_drawable = NULL;
	surface->current_drawable_present_scheduled = NO;
}

void metalexp_blit_surface_rgba8(jlong native_surface_handle, const void *pixels, jint width, jint height, jlong capacity) {
	if (![NSThread isMainThread]) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface blit must run on the main AppKit thread."
			userInfo:nil];
	}

	if (pixels == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface blit requires a direct pixel buffer."
			userInfo:nil];
	}

	if (width <= 0 || height <= 0) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface blit requires positive texture dimensions."
			userInfo:nil];
	}

	jlong required_bytes = (jlong)width * (jlong)height * 4L;
	if (capacity < required_bytes) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface blit pixel buffer is smaller than the requested RGBA8 frame."
			userInfo:nil];
	}

	metalexp_host_surface *surface = metalexp_require_surface(native_surface_handle, "blit");
	if (surface->current_drawable == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface blit requires an acquired drawable."
			userInfo:nil];
	}

	CAMetalLayer *layer = (__bridge CAMetalLayer *)surface->layer;
	id<CAMetalDrawable> drawable = (__bridge id<CAMetalDrawable>)surface->current_drawable;
	id<MTLDevice> device = layer.device;
	if (device == nil) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface blit requires an active MTLDevice."
			userInfo:nil];
	}

	id<MTLCommandQueue> command_queue = [device newCommandQueue];
	if (command_queue == nil) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface blit could not create an MTLCommandQueue."
			userInfo:nil];
	}

	id<MTLBuffer> staging_buffer = [device newBufferWithBytes:pixels length:(NSUInteger)required_bytes options:MTLResourceStorageModeShared];
	if (staging_buffer == nil) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface blit could not allocate a staging MTLBuffer."
			userInfo:nil];
	}

	id<MTLCommandBuffer> command_buffer = [command_queue commandBuffer];
	if (command_buffer == nil) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface blit could not allocate an MTLCommandBuffer."
			userInfo:nil];
	}

	id<MTLBlitCommandEncoder> blit_encoder = [command_buffer blitCommandEncoder];
	if (blit_encoder == nil) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface blit could not allocate an MTLBlitCommandEncoder."
			userInfo:nil];
	}

	NSUInteger drawable_width = drawable.texture.width;
	NSUInteger drawable_height = drawable.texture.height;
	NSUInteger copy_width = MIN((NSUInteger)width, drawable_width);
	NSUInteger copy_height = MIN((NSUInteger)height, drawable_height);
	if (copy_width == 0 || copy_height == 0) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface blit requires a drawable with non-zero dimensions."
			userInfo:nil];
	}

	[blit_encoder copyFromBuffer:staging_buffer
		sourceOffset:0
		sourceBytesPerRow:(NSUInteger)width * 4U
		sourceBytesPerImage:(NSUInteger)width * (NSUInteger)height * 4U
		sourceSize:MTLSizeMake(copy_width, copy_height, 1)
		toTexture:drawable.texture
		destinationSlice:0
		destinationLevel:0
		destinationOrigin:MTLOriginMake(0, 0, 0)];
	[blit_encoder endEncoding];
	[command_buffer commit];
	[command_buffer waitUntilCompleted];

	if (command_buffer.error != nil) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:command_buffer.error.localizedDescription == nil ? @"Metal surface blit command buffer failed." : command_buffer.error.localizedDescription
			userInfo:nil];
	}
}

void metalexp_blit_surface_texture(jlong native_command_context_handle, jlong native_surface_handle, jlong native_texture_handle) {
	if (![NSThread isMainThread]) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface texture blit must run on the main AppKit thread."
			userInfo:nil];
	}

	metalexp_host_surface *surface = metalexp_require_surface(native_surface_handle, "texture blit");
	if (surface->current_drawable == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface texture blit requires an acquired drawable."
			userInfo:nil];
	}

	metalexp_native_texture *native_texture = metalexp_require_texture(native_texture_handle, "surface blit");
	id<MTLTexture> source_texture = metalexp_texture_object(native_texture);
	id<CAMetalDrawable> drawable = (__bridge id<CAMetalDrawable>)surface->current_drawable;
	id<MTLDevice> device = source_texture.device;
	id<MTLCommandBuffer> command_buffer = metalexp_command_buffer_for_context(native_command_context_handle, device, "surface texture blit");
	metalexp_present_texture_to_drawable(command_buffer, source_texture, drawable.texture);
	[command_buffer presentDrawable:drawable];
	surface->current_drawable_present_scheduled = YES;
}
