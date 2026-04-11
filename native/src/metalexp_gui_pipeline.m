#include "metalexp_common.h"

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
	static id<MTLRenderPipelineState> cached_textured_opaque_background_pipeline = nil;
	static id<MTLRenderPipelineState> cached_textured_premultiplied_pipeline = nil;
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
		if (pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_OPAQUE_BACKGROUND && cached_textured_opaque_background_pipeline != nil) {
			return cached_textured_opaque_background_pipeline;
		}
		if (pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_PREMULTIPLIED_ALPHA && cached_textured_premultiplied_pipeline != nil) {
			return cached_textured_premultiplied_pipeline;
		}
	}

	id<MTLLibrary> library = metalexp_gui_library(device);
	NSString *vertex_name = nil;
	NSString *fragment_name = nil;
	if (pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED
		|| pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_OPAQUE_BACKGROUND
		|| pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_PREMULTIPLIED_ALPHA) {
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
	if (pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED
		|| pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_OPAQUE_BACKGROUND
		|| pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_PREMULTIPLIED_ALPHA) {
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
	if (pipeline_kind == METALEXP_PIPELINE_PANORAMA
		|| pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_OPAQUE_BACKGROUND) {
		descriptor.colorAttachments[0].blendingEnabled = NO;
	} else if (pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED
		|| pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_PREMULTIPLIED_ALPHA) {
		descriptor.colorAttachments[0].blendingEnabled = YES;
		descriptor.colorAttachments[0].rgbBlendOperation = MTLBlendOperationAdd;
		descriptor.colorAttachments[0].alphaBlendOperation = MTLBlendOperationAdd;
		descriptor.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorOne;
		descriptor.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorOne;
		descriptor.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
		descriptor.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
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
	} else if (pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_OPAQUE_BACKGROUND) {
		cached_textured_opaque_background_pipeline = pipeline_state;
	} else if (pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_PREMULTIPLIED_ALPHA) {
		cached_textured_premultiplied_pipeline = pipeline_state;
	} else if (pipeline_kind == METALEXP_PIPELINE_PANORAMA) {
		cached_panorama_pipeline = pipeline_state;
	} else {
		cached_color_pipeline = pipeline_state;
	}

	return pipeline_state;
}

id<MTLSamplerState> metalexp_sampler_state(id<MTLDevice> device, BOOL linear_filtering, BOOL repeat_u, BOOL repeat_v) {
	MTLSamplerDescriptor *descriptor = [[MTLSamplerDescriptor alloc] init];
	descriptor.minFilter = linear_filtering ? MTLSamplerMinMagFilterLinear : MTLSamplerMinMagFilterNearest;
	descriptor.magFilter = linear_filtering ? MTLSamplerMinMagFilterLinear : MTLSamplerMinMagFilterNearest;
	descriptor.sAddressMode = repeat_u ? MTLSamplerAddressModeRepeat : MTLSamplerAddressModeClampToEdge;
	descriptor.tAddressMode = repeat_v ? MTLSamplerAddressModeRepeat : MTLSamplerAddressModeClampToEdge;
	return [device newSamplerStateWithDescriptor:descriptor];
}

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

	id<MTLCommandBuffer> command_buffer = metalexp_command_buffer_for_context(native_command_context_handle, device, "gui draw");

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

	if (pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED
		|| pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_OPAQUE_BACKGROUND
		|| pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_PREMULTIPLIED_ALPHA) {
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
}
