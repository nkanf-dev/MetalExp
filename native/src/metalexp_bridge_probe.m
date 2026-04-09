#include <jni.h>

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
