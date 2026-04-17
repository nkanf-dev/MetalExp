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
		"struct PositionTexVertexIn {\n"
		"    float3 position [[attribute(0)]];\n"
		"    float2 uv0 [[attribute(1)]];\n"
		"};\n"
		"struct PositionTexRasterOut {\n"
		"    float4 position [[position]];\n"
		"    float2 uv0;\n"
		"};\n"
		"struct PositionRasterOut {\n"
		"    float4 position [[position]];\n"
		"};\n"
		"struct PostBlitRasterOut {\n"
		"    float4 position [[position]];\n"
		"    float2 uv0;\n"
		"};\n"
		"struct BlitConfig {\n"
		"    float4 ColorModulate;\n"
		"};\n"
		"float4 metalexp_gl_clip_to_metal(float4 clip) {\n"
		"    clip.z = (clip.z + clip.w) * 0.5;\n"
		"    return clip;\n"
		"}\n"
		"vertex GuiRasterOut metalexp_gui_color_vertex(GuiColorVertexIn in [[stage_in]], constant DynamicTransforms& dynamicTransforms [[buffer(1)]], constant Projection& projection [[buffer(2)]]) {\n"
		"    GuiRasterOut out;\n"
		"    out.position = metalexp_gl_clip_to_metal(projection.ProjMat * dynamicTransforms.ModelViewMat * float4(in.position, 1.0));\n"
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
		"    out.position = metalexp_gl_clip_to_metal(projection.ProjMat * dynamicTransforms.ModelViewMat * float4(in.position, 1.0));\n"
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
		"    out.position = metalexp_gl_clip_to_metal(projection.ProjMat * dynamicTransforms.ModelViewMat * float4(in.position, 1.0));\n"
		"    out.texCoord0 = in.position;\n"
		"    return out;\n"
		"}\n"
		"fragment float4 metalexp_panorama_fragment(PanoramaRasterOut in [[stage_in]], texturecube<float> sampler0 [[texture(0)]], sampler samplerState [[sampler(0)]]) {\n"
		"    return sampler0.sample(samplerState, in.texCoord0);\n"
		"}\n"
		"vertex PositionRasterOut metalexp_position_vertex(PanoramaVertexIn in [[stage_in]], constant DynamicTransforms& dynamicTransforms [[buffer(1)]], constant Projection& projection [[buffer(2)]]) {\n"
		"    PositionRasterOut out;\n"
		"    out.position = metalexp_gl_clip_to_metal(projection.ProjMat * dynamicTransforms.ModelViewMat * float4(in.position, 1.0));\n"
		"    return out;\n"
		"}\n"
		"fragment float4 metalexp_sky_fragment(PositionRasterOut in [[stage_in]], constant DynamicTransforms& dynamicTransforms [[buffer(1)]]) {\n"
		"    return float4(dynamicTransforms.ColorModulator.rgb, 1.0);\n"
		"}\n"
		"fragment float4 metalexp_stars_fragment(PositionRasterOut in [[stage_in]], constant DynamicTransforms& dynamicTransforms [[buffer(1)]]) {\n"
		"    return dynamicTransforms.ColorModulator;\n"
		"}\n"
		"vertex PositionTexRasterOut metalexp_position_tex_vertex(PositionTexVertexIn in [[stage_in]], constant DynamicTransforms& dynamicTransforms [[buffer(1)]], constant Projection& projection [[buffer(2)]]) {\n"
		"    PositionTexRasterOut out;\n"
		"    out.position = metalexp_gl_clip_to_metal(projection.ProjMat * dynamicTransforms.ModelViewMat * float4(in.position, 1.0));\n"
		"    out.uv0 = in.uv0;\n"
		"    return out;\n"
		"}\n"
		"fragment float4 metalexp_position_tex_fragment(PositionTexRasterOut in [[stage_in]], constant DynamicTransforms& dynamicTransforms [[buffer(1)]], texture2d<float> sampler0 [[texture(0)]], sampler samplerState [[sampler(0)]]) {\n"
		"    float4 color = sampler0.sample(samplerState, in.uv0);\n"
		"    if (color.a == 0.0) { discard_fragment(); }\n"
		"    return color * dynamicTransforms.ColorModulator;\n"
		"}\n"
		"vertex PostBlitRasterOut metalexp_post_blit_vertex(uint vertexId [[vertex_id]]) {\n"
		"    PostBlitRasterOut out;\n"
		"    float2 uv = float2((vertexId << 1) & 2, vertexId & 2);\n"
		"    out.position = float4(uv * float2(2.0, 2.0) + float2(-1.0, -1.0), 0.0, 1.0);\n"
		"    out.uv0 = uv;\n"
		"    return out;\n"
		"}\n"
		"fragment float4 metalexp_post_blit_fragment(PostBlitRasterOut in [[stage_in]], constant BlitConfig& blitConfig [[buffer(1)]], texture2d<float> sampler0 [[texture(0)]], sampler samplerState [[sampler(0)]]) {\n"
		"    return sampler0.sample(samplerState, in.uv0) * blitConfig.ColorModulate;\n"
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
	static id<MTLRenderPipelineState> cached_sky_pipeline = nil;
	static id<MTLRenderPipelineState> cached_post_blit_pipeline = nil;
	static id<MTLRenderPipelineState> cached_vignette_pipeline = nil;
	static id<MTLRenderPipelineState> cached_crosshair_pipeline = nil;
	static id<MTLRenderPipelineState> cached_sunrise_sunset_pipeline = nil;
	static id<MTLRenderPipelineState> cached_celestial_pipeline = nil;
	static id<MTLRenderPipelineState> cached_stars_pipeline = nil;
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
		if (pipeline_kind == METALEXP_PIPELINE_SKY && cached_sky_pipeline != nil) {
			return cached_sky_pipeline;
		}
		if (pipeline_kind == METALEXP_PIPELINE_POST_BLIT && cached_post_blit_pipeline != nil) {
			return cached_post_blit_pipeline;
		}
		if (pipeline_kind == METALEXP_PIPELINE_VIGNETTE && cached_vignette_pipeline != nil) {
			return cached_vignette_pipeline;
		}
		if (pipeline_kind == METALEXP_PIPELINE_CROSSHAIR && cached_crosshair_pipeline != nil) {
			return cached_crosshair_pipeline;
		}
		if (pipeline_kind == METALEXP_PIPELINE_SUNRISE_SUNSET && cached_sunrise_sunset_pipeline != nil) {
			return cached_sunrise_sunset_pipeline;
		}
		if (pipeline_kind == METALEXP_PIPELINE_CELESTIAL && cached_celestial_pipeline != nil) {
			return cached_celestial_pipeline;
		}
		if (pipeline_kind == METALEXP_PIPELINE_STARS && cached_stars_pipeline != nil) {
			return cached_stars_pipeline;
		}
	}

	id<MTLLibrary> library = metalexp_gui_library(device);
	NSString *vertex_name = nil;
	NSString *fragment_name = nil;
	if (pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED
		|| pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_OPAQUE_BACKGROUND
		|| pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_PREMULTIPLIED_ALPHA
		|| pipeline_kind == METALEXP_PIPELINE_VIGNETTE
		|| pipeline_kind == METALEXP_PIPELINE_CROSSHAIR) {
		vertex_name = @"metalexp_gui_textured_vertex";
		fragment_name = @"metalexp_gui_textured_fragment";
	} else if (pipeline_kind == METALEXP_PIPELINE_CELESTIAL) {
		vertex_name = @"metalexp_position_tex_vertex";
		fragment_name = @"metalexp_position_tex_fragment";
	} else if (pipeline_kind == METALEXP_PIPELINE_POST_BLIT) {
		vertex_name = @"metalexp_post_blit_vertex";
		fragment_name = @"metalexp_post_blit_fragment";
	} else if (pipeline_kind == METALEXP_PIPELINE_SKY) {
		vertex_name = @"metalexp_position_vertex";
		fragment_name = @"metalexp_sky_fragment";
	} else if (pipeline_kind == METALEXP_PIPELINE_STARS) {
		vertex_name = @"metalexp_position_vertex";
		fragment_name = @"metalexp_stars_fragment";
	} else if (pipeline_kind == METALEXP_PIPELINE_PANORAMA) {
		vertex_name = @"metalexp_panorama_vertex";
		fragment_name = @"metalexp_panorama_fragment";
	} else {
		vertex_name = @"metalexp_gui_color_vertex";
		fragment_name = @"metalexp_gui_color_fragment";
	}

	MTLVertexDescriptor *vertex_descriptor = nil;
	if (pipeline_kind != METALEXP_PIPELINE_POST_BLIT) {
		vertex_descriptor = [[MTLVertexDescriptor alloc] init];
		vertex_descriptor.attributes[0].offset = 0;
		vertex_descriptor.attributes[0].bufferIndex = 0;
		vertex_descriptor.attributes[0].format = MTLVertexFormatFloat3;
	}
	if (pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED
		|| pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_OPAQUE_BACKGROUND
		|| pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_PREMULTIPLIED_ALPHA
		|| pipeline_kind == METALEXP_PIPELINE_VIGNETTE
		|| pipeline_kind == METALEXP_PIPELINE_CROSSHAIR) {
		vertex_descriptor.attributes[1].offset = 12;
		vertex_descriptor.attributes[1].bufferIndex = 0;
		vertex_descriptor.attributes[1].format = MTLVertexFormatFloat2;
		vertex_descriptor.attributes[2].offset = 20;
		vertex_descriptor.attributes[2].bufferIndex = 0;
		vertex_descriptor.attributes[2].format = MTLVertexFormatUChar4Normalized;
		vertex_descriptor.layouts[0].stride = 24;
	} else if (pipeline_kind == METALEXP_PIPELINE_CELESTIAL) {
		vertex_descriptor.attributes[1].offset = 12;
		vertex_descriptor.attributes[1].bufferIndex = 0;
		vertex_descriptor.attributes[1].format = MTLVertexFormatFloat2;
		vertex_descriptor.layouts[0].stride = 20;
	} else if (pipeline_kind == METALEXP_PIPELINE_PANORAMA || pipeline_kind == METALEXP_PIPELINE_SKY || pipeline_kind == METALEXP_PIPELINE_STARS) {
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
		|| pipeline_kind == METALEXP_PIPELINE_SKY
		|| pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_OPAQUE_BACKGROUND) {
		descriptor.colorAttachments[0].blendingEnabled = NO;
	} else if (pipeline_kind == METALEXP_PIPELINE_CELESTIAL || pipeline_kind == METALEXP_PIPELINE_STARS) {
		descriptor.colorAttachments[0].blendingEnabled = YES;
		descriptor.colorAttachments[0].rgbBlendOperation = MTLBlendOperationAdd;
		descriptor.colorAttachments[0].alphaBlendOperation = MTLBlendOperationAdd;
		descriptor.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorSourceAlpha;
		descriptor.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOne;
		descriptor.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorOne;
		descriptor.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorZero;
	} else if (pipeline_kind == METALEXP_PIPELINE_SUNRISE_SUNSET) {
		descriptor.colorAttachments[0].blendingEnabled = YES;
		descriptor.colorAttachments[0].rgbBlendOperation = MTLBlendOperationAdd;
		descriptor.colorAttachments[0].alphaBlendOperation = MTLBlendOperationAdd;
		descriptor.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorSourceAlpha;
		descriptor.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
		descriptor.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorOne;
		descriptor.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
	} else if (pipeline_kind == METALEXP_PIPELINE_VIGNETTE) {
		descriptor.colorAttachments[0].blendingEnabled = YES;
		descriptor.colorAttachments[0].rgbBlendOperation = MTLBlendOperationAdd;
		descriptor.colorAttachments[0].alphaBlendOperation = MTLBlendOperationAdd;
		descriptor.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorZero;
		descriptor.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceColor;
		descriptor.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorZero;
		descriptor.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOne;
	} else if (pipeline_kind == METALEXP_PIPELINE_CROSSHAIR) {
		descriptor.colorAttachments[0].blendingEnabled = YES;
		descriptor.colorAttachments[0].rgbBlendOperation = MTLBlendOperationAdd;
		descriptor.colorAttachments[0].alphaBlendOperation = MTLBlendOperationAdd;
		descriptor.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorOneMinusDestinationColor;
		descriptor.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceColor;
		descriptor.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorOne;
		descriptor.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorZero;
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
	} else if (pipeline_kind == METALEXP_PIPELINE_POST_BLIT) {
		cached_post_blit_pipeline = pipeline_state;
	} else if (pipeline_kind == METALEXP_PIPELINE_VIGNETTE) {
		cached_vignette_pipeline = pipeline_state;
	} else if (pipeline_kind == METALEXP_PIPELINE_CROSSHAIR) {
		cached_crosshair_pipeline = pipeline_state;
	} else if (pipeline_kind == METALEXP_PIPELINE_SUNRISE_SUNSET) {
		cached_sunrise_sunset_pipeline = pipeline_state;
	} else if (pipeline_kind == METALEXP_PIPELINE_CELESTIAL) {
		cached_celestial_pipeline = pipeline_state;
	} else if (pipeline_kind == METALEXP_PIPELINE_STARS) {
		cached_stars_pipeline = pipeline_state;
	} else if (pipeline_kind == METALEXP_PIPELINE_PANORAMA) {
		cached_panorama_pipeline = pipeline_state;
	} else if (pipeline_kind == METALEXP_PIPELINE_SKY) {
		cached_sky_pipeline = pipeline_state;
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

static MTLPrimitiveType metalexp_primitive_type(NSInteger primitive_kind) {
	switch (primitive_kind) {
		case METALEXP_PRIMITIVE_TRIANGLE:
			return MTLPrimitiveTypeTriangle;
		case METALEXP_PRIMITIVE_TRIANGLE_STRIP:
			return MTLPrimitiveTypeTriangleStrip;
		case METALEXP_PRIMITIVE_LINE:
			return MTLPrimitiveTypeLine;
		case METALEXP_PRIMITIVE_LINE_STRIP:
			return MTLPrimitiveTypeLineStrip;
		case METALEXP_PRIMITIVE_POINT:
			return MTLPrimitiveTypePoint;
		default:
			@throw [NSException exceptionWithName:@"MetalExpDrawException"
				reason:@"Metal GUI draw received an unsupported primitive kind."
				userInfo:nil];
	}
}

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
) {
	BOOL post_blit_pipeline = pipeline_kind == METALEXP_PIPELINE_POST_BLIT;
	if (index_bytes == NULL || dynamic_bytes == NULL || (!post_blit_pipeline && (vertex_bytes == NULL || projection_bytes == NULL))) {
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

	jlong required_projection_capacity = post_blit_pipeline ? 0L : (jlong)sizeof(metalexp_projection_uniform);
	jlong required_dynamic_capacity = post_blit_pipeline ? (jlong)sizeof(metalexp_blit_config_uniform) : (jlong)sizeof(metalexp_dynamic_transforms_uniform);
	if (projection_capacity < required_projection_capacity || dynamic_capacity < required_dynamic_capacity) {
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
	id<MTLBuffer> vertex_buffer = [device newBufferWithBytesNoCopy:(void *)vertex_bytes length:(NSUInteger)vertex_capacity options:MTLResourceStorageModeShared deallocator:nil];
	id<MTLBuffer> index_buffer = [device newBufferWithBytesNoCopy:(void *)index_bytes length:(NSUInteger)index_capacity options:MTLResourceStorageModeShared deallocator:nil];
	metalexp_staging_buffer_slice dynamic_slice = metalexp_stage_uniform_bytes(
		native_command_context_handle,
		device,
		dynamic_bytes,
		(NSUInteger)required_dynamic_capacity,
		post_blit_pipeline ? "post blit config stage" : "gui transform stage"
	);
	metalexp_staging_buffer_slice projection_slice = (metalexp_staging_buffer_slice){ .buffer = nil, .offset = 0 };
	if (!post_blit_pipeline) {
		projection_slice = metalexp_stage_uniform_bytes(
			native_command_context_handle,
			device,
			projection_bytes,
			(NSUInteger)sizeof(metalexp_projection_uniform),
			"gui projection stage"
		);
	}
	if (vertex_buffer == nil || index_buffer == nil) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal GUI draw could not wrap direct vertex or index memory as a shared MTLBuffer."
			userInfo:nil];
	}

	[encoder setRenderPipelineState:pipeline_state];
	[encoder setVertexBuffer:vertex_buffer offset:0 atIndex:0];
	if (!post_blit_pipeline) {
		[encoder setVertexBuffer:dynamic_slice.buffer offset:dynamic_slice.offset atIndex:1];
		[encoder setVertexBuffer:projection_slice.buffer offset:projection_slice.offset atIndex:2];
	}
	[encoder setFragmentBuffer:dynamic_slice.buffer offset:dynamic_slice.offset atIndex:1];
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
		|| pipeline_kind == METALEXP_PIPELINE_GUI_TEXTURED_PREMULTIPLIED_ALPHA
		|| pipeline_kind == METALEXP_PIPELINE_VIGNETTE
		|| pipeline_kind == METALEXP_PIPELINE_CROSSHAIR
		|| pipeline_kind == METALEXP_PIPELINE_CELESTIAL
		|| pipeline_kind == METALEXP_PIPELINE_POST_BLIT) {
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
	[encoder drawIndexedPrimitives:metalexp_primitive_type(primitive_kind)
		indexCount:(NSUInteger)index_count
		indexType:index_type
		indexBuffer:index_buffer
		indexBufferOffset:index_offset
		instanceCount:1
		baseVertex:base_vertex
		baseInstance:0];
	[encoder endEncoding];
}
