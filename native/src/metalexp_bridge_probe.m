#include <jni.h>
#include <stdlib.h>
#include <stdint.h>
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

enum {
	METALEXP_PIPELINE_GUI_COLOR = 1,
	METALEXP_PIPELINE_GUI_TEXTURED = 2,
	METALEXP_PIPELINE_PANORAMA = 3
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

static jobject metalexp_create_surface_bootstrap_result(JNIEnv *env, jint outcome_code, jint drawable_width, jint drawable_height, jdouble contents_scale, const char *detail, const char *missing_capability, jlong native_surface_handle) {
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

static CAMetalLayer *metalexp_create_layer(NSWindow *window, NSView *view, id<MTLDevice> device) {
	CAMetalLayer *layer = [CAMetalLayer layer];
	if (layer == nil) {
		return nil;
	}

	layer.device = device;
	CGFloat contents_scale = window.screen != nil ? window.screen.backingScaleFactor : 1.0;
	if (contents_scale <= 0.0) {
		contents_scale = 1.0;
	}

	layer.contentsScale = contents_scale;
	layer.frame = view.bounds;
	layer.drawableSize = CGSizeMake(view.bounds.size.width * contents_scale, view.bounds.size.height * contents_scale);
	return layer;
}

static metalexp_native_texture *metalexp_require_texture(jlong native_texture_handle, const char *operation_name) {
	if (native_texture_handle == 0) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:[NSString stringWithFormat:@"Metal texture %@ requires a non-zero native handle.", [NSString stringWithUTF8String:operation_name]]
			userInfo:nil];
	}

	metalexp_native_texture *native_texture = (metalexp_native_texture *)(uintptr_t)native_texture_handle;
	if (native_texture->texture == NULL || native_texture->width == 0 || native_texture->height == 0) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:[NSString stringWithFormat:@"Metal texture %@ received an incomplete native texture record.", [NSString stringWithUTF8String:operation_name]]
			userInfo:nil];
	}

	return native_texture;
}

static id<MTLTexture> metalexp_texture_object(metalexp_native_texture *native_texture) {
	return (__bridge id<MTLTexture>)native_texture->texture;
}

static NSString *metalexp_gui_shader_source(void) {
	return @"#include <metal_stdlib>\n"
		"using namespace metal;\n"
		"struct DynamicTransforms {\n"
		"    float4x4 ModelViewMat;\n"
		"    float4 ColorModulator;\n"
		"    float3 ModelOffset;\n"
		"    float _Padding;\n"
		"    float4x4 TextureMat;\n"
		"};\n"
		"struct Projection {\n"
		"    float4x4 ProjMat;\n"
		"};\n"
		"struct GuiColorVertexIn {\n"
		"    float3 position [[attribute(0)]];\n"
		"    float4 color [[attribute(1)]];\n"
		"};\n"
		"struct GuiTexturedVertexIn {\n"
		"    float3 position [[attribute(0)]];\n"
		"    float2 uv0 [[attribute(1)]];\n"
		"    float4 color [[attribute(2)]];\n"
		"};\n"
		"struct GuiRasterOut {\n"
		"    float4 position [[position]];\n"
		"    float4 color;\n"
		"    float2 uv0;\n"
		"};\n"
		"struct PanoramaVertexIn {\n"
		"    float3 position [[attribute(0)]];\n"
		"};\n"
		"struct PanoramaRasterOut {\n"
		"    float4 position [[position]];\n"
		"    float3 texCoord0;\n"
		"};\n"
		"vertex GuiRasterOut metalexp_gui_color_vertex(GuiColorVertexIn in [[stage_in]], constant DynamicTransforms& dynamicTransforms [[buffer(1)]], constant Projection& projection [[buffer(2)]]) {\n"
		"    GuiRasterOut out;\n"
		"    out.position = projection.ProjMat * dynamicTransforms.ModelViewMat * float4(in.position, 1.0);\n"
		"    out.color = in.color;\n"
		"    out.uv0 = float2(0.0, 0.0);\n"
		"    return out;\n"
		"}\n"
		"fragment float4 metalexp_gui_color_fragment(GuiRasterOut in [[stage_in]], constant DynamicTransforms& dynamicTransforms [[buffer(1)]]) {\n"
		"    float4 color = in.color * dynamicTransforms.ColorModulator;\n"
		"    if (color.a == 0.0) { discard_fragment(); }\n"
		"    return color;\n"
		"}\n"
		"vertex GuiRasterOut metalexp_gui_textured_vertex(GuiTexturedVertexIn in [[stage_in]], constant DynamicTransforms& dynamicTransforms [[buffer(1)]], constant Projection& projection [[buffer(2)]]) {\n"
		"    GuiRasterOut out;\n"
		"    out.position = projection.ProjMat * dynamicTransforms.ModelViewMat * float4(in.position, 1.0);\n"
		"    out.color = in.color;\n"
		"    out.uv0 = in.uv0;\n"
		"    return out;\n"
		"}\n"
		"fragment float4 metalexp_gui_textured_fragment(GuiRasterOut in [[stage_in]], constant DynamicTransforms& dynamicTransforms [[buffer(1)]], texture2d<float> sampler0 [[texture(0)]], sampler samplerState [[sampler(0)]]) {\n"
		"    float4 color = sampler0.sample(samplerState, in.uv0) * in.color;\n"
		"    if (color.a == 0.0) { discard_fragment(); }\n"
		"    return color * dynamicTransforms.ColorModulator;\n"
		"}\n"
		"vertex PanoramaRasterOut metalexp_panorama_vertex(PanoramaVertexIn in [[stage_in]], constant DynamicTransforms& dynamicTransforms [[buffer(1)]], constant Projection& projection [[buffer(2)]]) {\n"
		"    PanoramaRasterOut out;\n"
		"    out.position = projection.ProjMat * dynamicTransforms.ModelViewMat * float4(in.position, 1.0);\n"
		"    out.texCoord0 = in.position;\n"
		"    return out;\n"
		"}\n"
		"fragment float4 metalexp_panorama_fragment(PanoramaRasterOut in [[stage_in]], texturecube<float> sampler0 [[texture(0)]], sampler samplerState [[sampler(0)]]) {\n"
		"    return sampler0.sample(samplerState, in.texCoord0);\n"
		"}\n";
}

static id<MTLLibrary> metalexp_gui_library(id<MTLDevice> device) {
	static id<MTLDevice> cached_device = nil;
	static id<MTLLibrary> cached_library = nil;
	static dispatch_once_t token;
	dispatch_once(&token, ^{
		cached_device = nil;
		cached_library = nil;
	});

	if (cached_library != nil && cached_device == device) {
		return cached_library;
	}

	NSError *error = nil;
	id<MTLLibrary> library = [device newLibraryWithSource:metalexp_gui_shader_source() options:nil error:&error];
	if (library == nil) {
		@throw [NSException exceptionWithName:@"MetalExpPipelineException"
			reason:error.localizedDescription == nil ? @"Metal GUI shader library compilation failed." : error.localizedDescription
			userInfo:nil];
	}

	cached_device = device;
	cached_library = library;
	return cached_library;
}

static id<MTLRenderPipelineState> metalexp_gui_pipeline_state(id<MTLDevice> device, NSInteger pipeline_kind) {
	static id<MTLDevice> cached_device = nil;
	static id<MTLRenderPipelineState> cached_color_pipeline = nil;
	static id<MTLRenderPipelineState> cached_textured_pipeline = nil;
	static id<MTLRenderPipelineState> cached_panorama_pipeline = nil;
	if (cached_device == device) {
		if (pipeline_kind == METALEXP_PIPELINE_GUI_COLOR && cached_color_pipeline != nil) {
			return cached_color_pipeline;
		}
		if (pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED && cached_textured_pipeline != nil) {
			return cached_textured_pipeline;
		}
		if (pipeline_kind == METALEXP_PIPELINE_PANORAMA && cached_panorama_pipeline != nil) {
			return cached_panorama_pipeline;
		}
	}

	id<MTLLibrary> library = metalexp_gui_library(device);
	NSString *vertex_name = nil;
	NSString *fragment_name = nil;
	if (pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED) {
		vertex_name = @"metalexp_gui_textured_vertex";
		fragment_name = @"metalexp_gui_textured_fragment";
	} else if (pipeline_kind == METALEXP_PIPELINE_PANORAMA) {
		vertex_name = @"metalexp_panorama_vertex";
		fragment_name = @"metalexp_panorama_fragment";
	} else {
		vertex_name = @"metalexp_gui_color_vertex";
		fragment_name = @"metalexp_gui_color_fragment";
	}

	MTLVertexDescriptor *vertex_descriptor = [[MTLVertexDescriptor alloc] init];
	vertex_descriptor.attributes[0].offset = 0;
	vertex_descriptor.attributes[0].bufferIndex = 0;
	vertex_descriptor.attributes[0].format = MTLVertexFormatFloat3;
	if (pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED) {
		vertex_descriptor.attributes[1].offset = 12;
		vertex_descriptor.attributes[1].bufferIndex = 0;
		vertex_descriptor.attributes[1].format = MTLVertexFormatFloat2;
		vertex_descriptor.attributes[2].offset = 20;
		vertex_descriptor.attributes[2].bufferIndex = 0;
		vertex_descriptor.attributes[2].format = MTLVertexFormatUChar4Normalized;
		vertex_descriptor.layouts[0].stride = 24;
	} else if (pipeline_kind == METALEXP_PIPELINE_PANORAMA) {
		vertex_descriptor.layouts[0].stride = 12;
	} else {
		vertex_descriptor.attributes[1].offset = 12;
		vertex_descriptor.attributes[1].bufferIndex = 0;
		vertex_descriptor.attributes[1].format = MTLVertexFormatUChar4Normalized;
		vertex_descriptor.layouts[0].stride = 16;
	}

	MTLRenderPipelineDescriptor *descriptor = [[MTLRenderPipelineDescriptor alloc] init];
	descriptor.vertexFunction = [library newFunctionWithName:vertex_name];
	descriptor.fragmentFunction = [library newFunctionWithName:fragment_name];
	descriptor.vertexDescriptor = vertex_descriptor;
	descriptor.colorAttachments[0].pixelFormat = MTLPixelFormatRGBA8Unorm;
	if (pipeline_kind == METALEXP_PIPELINE_PANORAMA) {
		descriptor.colorAttachments[0].blendingEnabled = NO;
	} else {
		descriptor.colorAttachments[0].blendingEnabled = YES;
		descriptor.colorAttachments[0].rgbBlendOperation = MTLBlendOperationAdd;
		descriptor.colorAttachments[0].alphaBlendOperation = MTLBlendOperationAdd;
		descriptor.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorSourceAlpha;
		descriptor.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorOne;
		descriptor.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
		descriptor.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
	}

	NSError *error = nil;
	id<MTLRenderPipelineState> pipeline_state = [device newRenderPipelineStateWithDescriptor:descriptor error:&error];
	if (pipeline_state == nil) {
		@throw [NSException exceptionWithName:@"MetalExpPipelineException"
			reason:error.localizedDescription == nil ? @"Metal GUI render pipeline creation failed." : error.localizedDescription
			userInfo:nil];
	}

	cached_device = device;
	if (pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED) {
		cached_textured_pipeline = pipeline_state;
	} else if (pipeline_kind == METALEXP_PIPELINE_PANORAMA) {
		cached_panorama_pipeline = pipeline_state;
	} else {
		cached_color_pipeline = pipeline_state;
	}
	return pipeline_state;
}

static id<MTLSamplerState> metalexp_sampler_state(id<MTLDevice> device, BOOL linear_filtering, BOOL repeat_u, BOOL repeat_v) {
	MTLSamplerDescriptor *descriptor = [[MTLSamplerDescriptor alloc] init];
	descriptor.minFilter = linear_filtering ? MTLSamplerMinMagFilterLinear : MTLSamplerMinMagFilterNearest;
	descriptor.magFilter = linear_filtering ? MTLSamplerMinMagFilterLinear : MTLSamplerMinMagFilterNearest;
	descriptor.sAddressMode = repeat_u ? MTLSamplerAddressModeRepeat : MTLSamplerAddressModeClampToEdge;
	descriptor.tAddressMode = repeat_v ? MTLSamplerAddressModeRepeat : MTLSamplerAddressModeClampToEdge;
	return [device newSamplerStateWithDescriptor:descriptor];
}

static jobject metalexp_probe_surface(JNIEnv *env, jlong cocoa_window_handle, jlong cocoa_view_handle) {
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
			0,
			0,
			0.0,
			((void) validation_error, "Metal surface bootstrap failed during Cocoa host validation."),
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

static metalexp_host_surface *metalexp_require_surface(jlong native_surface_handle, const char *operation_name) {
	if (native_surface_handle == 0) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:[NSString stringWithFormat:@"Metal surface %@ requires a non-zero native handle.", [NSString stringWithUTF8String:operation_name]]
			userInfo:nil];
	}

	metalexp_host_surface *surface = (metalexp_host_surface *)(uintptr_t)native_surface_handle;
	if (surface->view == NULL || surface->layer == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:[NSString stringWithFormat:@"Metal surface %@ received an incomplete native surface record.", [NSString stringWithUTF8String:operation_name]]
			userInfo:nil];
	}

	return surface;
}

static void metalexp_configure_surface(jlong native_surface_handle, jint width, jint height, jboolean vsync) {
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

static void metalexp_acquire_surface(jlong native_surface_handle) {
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

	id<CAMetalDrawable> drawable = [layer nextDrawable];
	if (drawable == nil) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"CAMetalLayer did not provide a drawable during acquire."
			userInfo:nil];
	}

	surface->current_drawable = (void *)CFBridgingRetain(drawable);
}

static void metalexp_present_surface(jlong native_surface_handle) {
	if (![NSThread isMainThread]) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface present must run on the main AppKit thread."
			userInfo:nil];
	}

	metalexp_host_surface *surface = metalexp_require_surface(native_surface_handle, "present");
	if (surface->current_drawable == NULL) {
		return;
	}

	id<CAMetalDrawable> drawable = (__bridge id<CAMetalDrawable>)surface->current_drawable;
	[drawable present];
	(void)CFBridgingRelease(surface->current_drawable);
	surface->current_drawable = NULL;
}

static void metalexp_blit_surface_rgba8(jlong native_surface_handle, const void *pixels, jint width, jint height, jlong capacity) {
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

static jlong metalexp_create_texture(jint width, jint height, jint depth_or_layers, jint mip_levels, jboolean render_attachment, jboolean shader_read, jboolean cubemap_compatible) {
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
	descriptor.pixelFormat = MTLPixelFormatRGBA8Unorm;
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
	return (jlong)(uintptr_t)native_texture;
}

static void metalexp_upload_texture_rgba8(jlong native_texture_handle, jint mip_level, jint layer, const void *pixels, jint width, jint height, jlong capacity) {
	if (pixels == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpTextureException"
			reason:@"Metal texture upload requires a direct pixel buffer."
			userInfo:nil];
	}

	metalexp_native_texture *native_texture = metalexp_require_texture(native_texture_handle, "upload");
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

	NSUInteger mip_width = MAX(1U, native_texture->width >> mip_level);
	NSUInteger mip_height = MAX(1U, native_texture->height >> mip_level);
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

static void metalexp_release_texture(jlong native_texture_handle) {
	if (native_texture_handle == 0) {
		return;
	}

	metalexp_native_texture *native_texture = (metalexp_native_texture *)(uintptr_t)native_texture_handle;
	if (native_texture->texture != NULL) {
		(void)CFBridgingRelease(native_texture->texture);
	}
	free(native_texture);
}

static void metalexp_draw_gui_pass(
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
) {
	if (vertex_bytes == NULL || index_bytes == NULL || projection_bytes == NULL || dynamic_bytes == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal GUI draw requires direct vertex, index, projection, and transform buffers."
			userInfo:nil];
	}

	if (index_count <= 0) {
		return;
	}

	if (vertex_stride <= 0 || index_type_bytes <= 0) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal GUI draw requires positive vertex stride and index type width."
			userInfo:nil];
	}

	if (projection_capacity < (jlong)sizeof(metalexp_projection_uniform) || dynamic_capacity < (jlong)sizeof(metalexp_dynamic_transforms_uniform)) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal GUI draw uniform buffers are smaller than the required std140 payloads."
			userInfo:nil];
	}

	metalexp_native_texture *target_texture = metalexp_require_texture(native_target_texture_handle, "draw");
	id<MTLTexture> texture = metalexp_texture_object(target_texture);
	id<MTLDevice> device = texture.device;
	if (device == nil) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal GUI draw requires an active MTLDevice on the target texture."
			userInfo:nil];
	}

	id<MTLCommandQueue> command_queue = [device newCommandQueue];
	id<MTLCommandBuffer> command_buffer = [command_queue commandBuffer];
	if (command_buffer == nil) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal GUI draw could not allocate an MTLCommandBuffer."
			userInfo:nil];
	}

	MTLRenderPassDescriptor *pass_descriptor = [MTLRenderPassDescriptor renderPassDescriptor];
	pass_descriptor.colorAttachments[0].texture = texture;
	pass_descriptor.colorAttachments[0].loadAction = MTLLoadActionLoad;
	pass_descriptor.colorAttachments[0].storeAction = MTLStoreActionStore;

	id<MTLRenderCommandEncoder> encoder = [command_buffer renderCommandEncoderWithDescriptor:pass_descriptor];
	if (encoder == nil) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal GUI draw could not allocate an MTLRenderCommandEncoder."
			userInfo:nil];
	}

	id<MTLRenderPipelineState> pipeline_state = metalexp_gui_pipeline_state(device, pipeline_kind);
	id<MTLBuffer> vertex_buffer = [device newBufferWithBytes:vertex_bytes length:(NSUInteger)vertex_capacity options:MTLResourceStorageModeShared];
	id<MTLBuffer> index_buffer = [device newBufferWithBytes:index_bytes length:(NSUInteger)index_capacity options:MTLResourceStorageModeShared];
	id<MTLBuffer> projection_buffer = [device newBufferWithBytes:projection_bytes length:(NSUInteger)sizeof(metalexp_projection_uniform) options:MTLResourceStorageModeShared];
	id<MTLBuffer> dynamic_buffer = [device newBufferWithBytes:dynamic_bytes length:(NSUInteger)sizeof(metalexp_dynamic_transforms_uniform) options:MTLResourceStorageModeShared];
	if (vertex_buffer == nil || index_buffer == nil || projection_buffer == nil || dynamic_buffer == nil) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal GUI draw could not allocate staging MTLBuffers."
			userInfo:nil];
	}

	[encoder setRenderPipelineState:pipeline_state];
	[encoder setVertexBuffer:vertex_buffer offset:0 atIndex:0];
	[encoder setVertexBuffer:dynamic_buffer offset:0 atIndex:1];
	[encoder setVertexBuffer:projection_buffer offset:0 atIndex:2];
	[encoder setFragmentBuffer:dynamic_buffer offset:0 atIndex:1];
	[encoder setViewport:(MTLViewport){0.0, 0.0, (double)target_texture->width, (double)target_texture->height, 0.0, 1.0}];

	if (scissor_enabled == JNI_TRUE) {
		MTLScissorRect rect = {
			.x = (NSUInteger)MAX(scissor_x, 0),
			.y = (NSUInteger)MAX(scissor_y, 0),
			.width = (NSUInteger)MAX(scissor_width, 0),
			.height = (NSUInteger)MAX(scissor_height, 0)
		};
		[encoder setScissorRect:rect];
	}

	if (pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED) {
		metalexp_native_texture *sampler0_texture = metalexp_require_texture(native_sampler0_texture_handle, "sampler0");
		id<MTLTexture> sampler0 = metalexp_texture_object(sampler0_texture);
		id<MTLSamplerState> sampler_state = metalexp_sampler_state(device, linear_filtering == JNI_TRUE, repeat_u == JNI_TRUE, repeat_v == JNI_TRUE);
		[encoder setFragmentTexture:sampler0 atIndex:0];
		[encoder setFragmentSamplerState:sampler_state atIndex:0];
	} else if (pipeline_kind == METALEXP_PIPELINE_PANORAMA) {
		metalexp_native_texture *sampler0_texture = metalexp_require_texture(native_sampler0_texture_handle, "sampler0");
		if (!sampler0_texture->cubemap_compatible) {
			@throw [NSException exceptionWithName:@"MetalExpDrawException"
				reason:@"Metal panorama draw requires a cubemap-compatible Sampler0 texture."
				userInfo:nil];
		}

		id<MTLTexture> sampler0 = metalexp_texture_object(sampler0_texture);
		id<MTLSamplerState> sampler_state = metalexp_sampler_state(device, linear_filtering == JNI_TRUE, repeat_u == JNI_TRUE, repeat_v == JNI_TRUE);
		[encoder setFragmentTexture:sampler0 atIndex:0];
		[encoder setFragmentSamplerState:sampler_state atIndex:0];
	}

	MTLIndexType index_type = index_type_bytes == 2 ? MTLIndexTypeUInt16 : MTLIndexTypeUInt32;
	NSUInteger index_offset = (NSUInteger)first_index * (NSUInteger)index_type_bytes;
	[encoder drawIndexedPrimitives:MTLPrimitiveTypeTriangle
		indexCount:(NSUInteger)index_count
		indexType:index_type
		indexBuffer:index_buffer
		indexBufferOffset:index_offset
		instanceCount:1
		baseVertex:base_vertex
		baseInstance:0];
	[encoder endEncoding];
	[command_buffer commit];
	[command_buffer waitUntilCompleted];

	if (command_buffer.error != nil) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:command_buffer.error.localizedDescription == nil ? @"Metal GUI draw command buffer failed." : command_buffer.error.localizedDescription
			userInfo:nil];
	}
}

static void metalexp_blit_surface_texture(jlong native_surface_handle, jlong native_texture_handle) {
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
	id<MTLCommandQueue> command_queue = [device newCommandQueue];
	id<MTLCommandBuffer> command_buffer = [command_queue commandBuffer];
	id<MTLBlitCommandEncoder> blit_encoder = [command_buffer blitCommandEncoder];
	if (command_buffer == nil || blit_encoder == nil) {
		@throw [NSException exceptionWithName:@"MetalExpSurfaceException"
			reason:@"Metal surface texture blit could not allocate a command buffer or blit encoder."
			userInfo:nil];
	}

	NSUInteger copy_width = MIN((NSUInteger)native_texture->width, drawable.texture.width);
	NSUInteger copy_height = MIN((NSUInteger)native_texture->height, drawable.texture.height);
	[blit_encoder copyFromTexture:source_texture
		sourceSlice:0
		sourceLevel:0
		sourceOrigin:MTLOriginMake(0, 0, 0)
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
			reason:command_buffer.error.localizedDescription == nil ? @"Metal surface texture blit command buffer failed." : command_buffer.error.localizedDescription
			userInfo:nil];
	}
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

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_blitSurfaceRgba80(JNIEnv *env, jclass clazz, jlong native_surface_handle, jobject rgba_pixels, jint width, jint height) {
	(void) clazz;

	@autoreleasepool {
		void *pixels = (*env)->GetDirectBufferAddress(env, rgba_pixels);
		jlong capacity = rgba_pixels == NULL ? 0L : (*env)->GetDirectBufferCapacity(env, rgba_pixels);
		metalexp_blit_surface_rgba8(native_surface_handle, pixels, width, height, capacity);
	}
}

JNIEXPORT jlong JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_createTexture0(JNIEnv *env, jclass clazz, jint width, jint height, jint depth_or_layers, jint mip_levels, jboolean render_attachment, jboolean shader_read, jboolean cubemap_compatible) {
	(void) env;
	(void) clazz;

	@autoreleasepool {
		return metalexp_create_texture(width, height, depth_or_layers, mip_levels, render_attachment, shader_read, cubemap_compatible);
	}
}

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_uploadTextureRgba80(JNIEnv *env, jclass clazz, jlong native_texture_handle, jint mip_level, jint layer, jobject rgba_pixels, jint width, jint height) {
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

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_blitSurfaceTexture0(JNIEnv *env, jclass clazz, jlong native_surface_handle, jlong native_texture_handle) {
	(void) env;
	(void) clazz;

	@autoreleasepool {
		metalexp_blit_surface_texture(native_surface_handle, native_texture_handle);
	}
}

JNIEXPORT void JNICALL Java_dev_nkanf_metalexp_bridge_NativeMetalBridge_presentSurface0(JNIEnv *env, jclass clazz, jlong native_surface_handle) {
	(void) env;
	(void) clazz;

	@autoreleasepool {
		metalexp_present_surface(native_surface_handle);
	}
}
