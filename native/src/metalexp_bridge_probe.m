#include <jni.h>
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

static jobject metalexp_probe_surface(JNIEnv *env, jlong cocoa_window_handle, jlong cocoa_view_handle) {
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

	CAMetalLayer *layer = [CAMetalLayer layer];
	if (layer == nil) {
		return metalexp_create_probe_result(
			env,
			METALEXP_OUTCOME_ERROR,
			"CAMetalLayer exists but could not be instantiated for the Cocoa host surface probe.",
			"ca_metal_layer_instance"
		);
	}

	layer.device = device;
	if (window.screen != nil) {
		layer.contentsScale = window.screen.backingScaleFactor;
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
