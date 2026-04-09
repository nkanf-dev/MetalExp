#include <jni.h>
#include <stdlib.h>
#include <stdint.h>

#import <AppKit/AppKit.h>
#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>

enum {
	METALEXP_OUTCOME_READY = 0,
	METALEXP_OUTCOME_STUB_UNIMPLEMENTED = 1,
	METALEXP_OUTCOME_ERROR = 2
};

typedef struct metalexp_host_surface {
	void *view;
	void *layer;
	void *original_layer;
	BOOL original_wants_layer;
} metalexp_host_surface;

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

static jobject metalexp_create_surface_bootstrap_result(JNIEnv *env, jint outcome_code, const char *detail, const char *missing_capability, jlong native_surface_handle) {
	jclass result_class = (*env)->FindClass(env, "dev/nkanf/metalexp/bridge/NativeMetalBridgeSurfaceBootstrapResult");
	if (result_class == NULL) {
		return NULL;
	}

	jmethodID constructor = (*env)->GetMethodID(env, result_class, "<init>", "(ILjava/lang/String;[Ljava/lang/String;J)V");
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

	return (*env)->NewObject(env, result_class, constructor, outcome_code, detail_string, missing, native_surface_handle);
}

static void metalexp_restore_bootstrapped_surface(metalexp_host_surface *surface) {
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
	if (surface->layer != NULL) {
		(void)CFBridgingRelease(surface->layer);
	}
	if (surface->view != NULL) {
		(void)CFBridgingRelease(surface->view);
	}

	free(surface);
}

static jobject metalexp_validate_surface_host(JNIEnv *env, jlong cocoa_window_handle, jlong cocoa_view_handle, NSWindow **window_out, NSView **view_out, id<MTLDevice> *device_out) {
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

static CAMetalLayer *metalexp_create_layer(NSWindow *window, id<MTLDevice> device) {
	CAMetalLayer *layer = [CAMetalLayer layer];
	if (layer == nil) {
		return nil;
	}

	layer.device = device;
	if (window.screen != nil) {
		layer.contentsScale = window.screen.backingScaleFactor;
	}
	return layer;
}

static jobject metalexp_probe_surface(JNIEnv *env, jlong cocoa_window_handle, jlong cocoa_view_handle) {
	NSWindow *window = nil;
	NSView *view = nil;
	id<MTLDevice> device = nil;
	jobject validation_error = metalexp_validate_surface_host(env, cocoa_window_handle, cocoa_view_handle, &window, &view, &device);
	if (validation_error != NULL) {
		return validation_error;
	}

	CAMetalLayer *layer = metalexp_create_layer(window, device);
	if (layer == nil) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"CAMetalLayer exists but could not be instantiated for the Cocoa host surface probe.",
			"ca_metal_layer_instance"
		);
	}

	BOOL originalWantsLayer = view.wantsLayer;
	CALayer *originalLayer = view.layer;
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
		[view setLayer:originalLayer];
		[view setWantsLayer:originalWantsLayer];
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

static jobject metalexp_bootstrap_surface(JNIEnv *env, jlong cocoa_window_handle, jlong cocoa_view_handle) {
	NSWindow *window = nil;
	NSView *view = nil;
	id<MTLDevice> device = nil;
	jobject validation_error = metalexp_validate_surface_host(env, cocoa_window_handle, cocoa_view_handle, &window, &view, &device);
	if (validation_error != NULL) {
		return metalexp_create_surface_bootstrap_result(
			env,
			METALEXP_OUTCOME_ERROR,
			((void) validation_error, "Metal surface bootstrap failed during Cocoa host validation."),
			"cocoa_surface_validation",
			0L
		);
	}

	CAMetalLayer *layer = metalexp_create_layer(window, device);
	if (layer == nil) {
		return metalexp_create_surface_bootstrap_result(
			env,
			METALEXP_OUTCOME_ERROR,
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
			"Failed to allocate native storage for the Metal host surface bootstrap.",
			"native_surface_storage",
			0L
		);
	}

	surface->view = (void *)CFBridgingRetain(view);
	surface->layer = (void *)CFBridgingRetain(layer);
	surface->original_layer = view.layer == nil ? NULL : (void *)CFBridgingRetain(view.layer);
	surface->original_wants_layer = view.wantsLayer;

	@try {
		[view setWantsLayer:YES];
		[view setLayer:layer];
		if (view.layer != layer) {
			metalexp_restore_bootstrapped_surface(surface);
			return metalexp_create_surface_bootstrap_result(
				env,
				METALEXP_OUTCOME_ERROR,
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
		detail.UTF8String,
		NULL,
		(jlong)(uintptr_t)surface
	);
}

JNIEXPORT jobject JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_probe0(JNIEnv *env, jclass clazz) {
	(void) clazz;

	@autoreleasepool {
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

		id<MTLCommandQueue> commandQueue = [device newCommandQueue];
		if (commandQueue == nil) {
			return metalexp_create_probe_result(
				env,
				METALEXP_OUTCOME_ERROR,
				"Default Metal device is available, but command queue creation failed.",
				"mtl_command_queue"
			);
		}

		NSString *deviceName = device.name;
		NSString *detail = [NSString stringWithFormat:
			@"Metal probe succeeded: device=%@, CAMetalLayer and MTLCommandQueue are available.",
			deviceName == nil || deviceName.length == 0 ? @"unknown" : deviceName
		];

		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_READY,
			detail.UTF8String,
			NULL
		);
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
