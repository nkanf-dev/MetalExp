#include <jni.h>

static jobject metalexp_create_probe_result(JNIEnv *env, jint outcome_code, const char *detail, const char *missing_capability) {
	jclass result_class = (*env)->FindClass(env, "dev/nkanf/metalexp/bridge/NativeMetalBridgeProbeResult");
	if (result_class == NULL) {
		return NULL;
	}

	jmethodID constructor = (*env)->GetMethodID(env, result_class, "<init>", "(ILjava/lang/String;[Ljava/lang/String;)V");
	if (constructor == NULL) {
		return NULL;
	}

	jclass string_class = (*env)->FindClass(env, "java/lang/String");
	if (string_class == NULL) {
		return NULL;
	}

	jobjectArray missing = (*env)->NewObjectArray(env, 1, string_class, NULL);
	if (missing == NULL) {
		return NULL;
	}

	jstring capability = (*env)->NewStringUTF(env, missing_capability);
	if (capability == NULL) {
		return NULL;
	}
	(*env)->SetObjectArrayElement(env, missing, 0, capability);

	jstring detail_string = (*env)->NewStringUTF(env, detail);
	if (detail_string == NULL) {
		return NULL;
	}

	return (*env)->NewObject(env, result_class, constructor, outcome_code, detail_string, missing);
}

JNIEXPORT jobject JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_probe0(JNIEnv *env, jclass clazz) {
	(void) clazz;
	return metalexp_create_probe_result(
		env,
		1,
		"Metal native bridge stub reached JNI successfully, but Cocoa, CAMetalLayer, and Metal device bring-up are not implemented yet.",
		"native_bridge_stub"
	);
}
