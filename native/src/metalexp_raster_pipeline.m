#include "metalexp_common.h"

static NSString *metalexp_raster_shader_source(void) {
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
		"struct PositionTexVertexIn {\n"
		"    float3 position [[attribute(0)]];\n"
		"    float2 uv0 [[attribute(1)]];\n"
		"};\n"
		"struct LineVertexIn {\n"
		"    float3 position [[attribute(0)]];\n"
		"    float4 color [[attribute(1)]];\n"
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
		"struct PositionTexRasterOut {\n"
		"    float4 position [[position]];\n"
		"    float2 uv0;\n"
		"};\n"
		"struct PositionRasterOut {\n"
		"    float4 position [[position]];\n"
		"};\n"
		"struct LineRasterOut {\n"
		"    float4 position [[position]];\n"
		"    float4 color;\n"
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
		"vertex LineRasterOut metalexp_line_vertex(LineVertexIn in [[stage_in]], constant DynamicTransforms& dynamicTransforms [[buffer(1)]], constant Projection& projection [[buffer(2)]]) {\n"
		"    LineRasterOut out;\n"
		"    out.position = metalexp_gl_clip_to_metal(projection.ProjMat * dynamicTransforms.ModelViewMat * float4(in.position, 1.0));\n"
		"    out.color = in.color * dynamicTransforms.ColorModulator;\n"
		"    return out;\n"
		"}\n"
		"fragment float4 metalexp_line_fragment(LineRasterOut in [[stage_in]]) {\n"
		"    if (in.color.a == 0.0) { discard_fragment(); }\n"
		"    return in.color;\n"
		"}\n";
}

static id<MTLLibrary> metalexp_raster_library(id<MTLDevice> device) {
	static id<MTLDevice> cached_device = nil;
	static id<MTLLibrary> cached_library = nil;
	if (cached_library != nil && cached_device == device) {
		return cached_library;
	}

	NSError *error = nil;
	id<MTLLibrary> library = [device newLibraryWithSource:metalexp_raster_shader_source() options:nil error:&error];
	if (library == nil) {
		@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
			reason:error.localizedDescription == nil ? @"Metal raster shader library compilation failed." : error.localizedDescription
			userInfo:nil];
	}

	cached_device = device;
	cached_library = library;
	return cached_library;
}

static NSString *metalexp_raster_pipeline_key(const metalexp_raster_pipeline_desc *pipeline_desc, MTLPixelFormat depth_format) {
	return [NSString stringWithFormat:@"%u|%u|%u|%u|%u|%u|%.4f|%.4f|%u|%u|%u|%u|%u|%u|%lu",
		pipeline_desc->shader_family,
		pipeline_desc->vertex_layout,
		pipeline_desc->primitive_topology,
		pipeline_desc->color_write_mask,
		pipeline_desc->flags,
		pipeline_desc->depth_compare,
		pipeline_desc->depth_bias_scale_factor,
		pipeline_desc->depth_bias_constant,
		pipeline_desc->color_blend_op,
		pipeline_desc->alpha_blend_op,
		pipeline_desc->src_color_factor,
		pipeline_desc->dst_color_factor,
		pipeline_desc->src_alpha_factor,
		pipeline_desc->dst_alpha_factor,
		(unsigned long)depth_format];
}

static NSString *metalexp_raster_depth_key(const metalexp_raster_pipeline_desc *pipeline_desc) {
	return [NSString stringWithFormat:@"%u|%u", pipeline_desc->depth_compare, (pipeline_desc->flags & METALEXP_RASTER_FLAG_DEPTH_WRITE) != 0 ? 1U : 0U];
}

static MTLPrimitiveType metalexp_raster_primitive_type(uint32_t primitive_topology) {
	switch (primitive_topology) {
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
			@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
				reason:@"Metal raster draw received an unsupported primitive topology."
				userInfo:nil];
	}
}

static MTLCompareFunction metalexp_raster_compare_function(uint32_t compare_function) {
	switch (compare_function) {
		case METALEXP_RASTER_COMPARE_ALWAYS_PASS:
			return MTLCompareFunctionAlways;
		case METALEXP_RASTER_COMPARE_LESS_THAN:
			return MTLCompareFunctionLess;
		case METALEXP_RASTER_COMPARE_LESS_THAN_OR_EQUAL:
			return MTLCompareFunctionLessEqual;
		case METALEXP_RASTER_COMPARE_EQUAL:
			return MTLCompareFunctionEqual;
		case METALEXP_RASTER_COMPARE_NOT_EQUAL:
			return MTLCompareFunctionNotEqual;
		case METALEXP_RASTER_COMPARE_GREATER_THAN_OR_EQUAL:
			return MTLCompareFunctionGreaterEqual;
		case METALEXP_RASTER_COMPARE_GREATER_THAN:
			return MTLCompareFunctionGreater;
		case METALEXP_RASTER_COMPARE_NEVER_PASS:
			return MTLCompareFunctionNever;
		default:
			@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
				reason:@"Metal raster draw received an unsupported depth compare function."
				userInfo:nil];
	}
}

static MTLBlendOperation metalexp_raster_blend_operation(uint32_t blend_operation) {
	switch (blend_operation) {
		case METALEXP_RASTER_BLEND_OP_ADD:
			return MTLBlendOperationAdd;
		case METALEXP_RASTER_BLEND_OP_SUBTRACT:
			return MTLBlendOperationSubtract;
		case METALEXP_RASTER_BLEND_OP_REVERSE_SUBTRACT:
			return MTLBlendOperationReverseSubtract;
		case METALEXP_RASTER_BLEND_OP_MIN:
			return MTLBlendOperationMin;
		case METALEXP_RASTER_BLEND_OP_MAX:
			return MTLBlendOperationMax;
		default:
			@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
				reason:@"Metal raster draw received an unsupported blend operation."
				userInfo:nil];
	}
}

static MTLBlendFactor metalexp_raster_blend_factor(uint32_t blend_factor) {
	switch (blend_factor) {
		case METALEXP_RASTER_BLEND_FACTOR_CONSTANT_ALPHA:
			return MTLBlendFactorBlendAlpha;
		case METALEXP_RASTER_BLEND_FACTOR_CONSTANT_COLOR:
			return MTLBlendFactorBlendColor;
		case METALEXP_RASTER_BLEND_FACTOR_DST_ALPHA:
			return MTLBlendFactorDestinationAlpha;
		case METALEXP_RASTER_BLEND_FACTOR_DST_COLOR:
			return MTLBlendFactorDestinationColor;
		case METALEXP_RASTER_BLEND_FACTOR_ONE:
			return MTLBlendFactorOne;
		case METALEXP_RASTER_BLEND_FACTOR_ONE_MINUS_CONSTANT_ALPHA:
			return MTLBlendFactorOneMinusBlendAlpha;
		case METALEXP_RASTER_BLEND_FACTOR_ONE_MINUS_CONSTANT_COLOR:
			return MTLBlendFactorOneMinusBlendColor;
		case METALEXP_RASTER_BLEND_FACTOR_ONE_MINUS_DST_ALPHA:
			return MTLBlendFactorOneMinusDestinationAlpha;
		case METALEXP_RASTER_BLEND_FACTOR_ONE_MINUS_DST_COLOR:
			return MTLBlendFactorOneMinusDestinationColor;
		case METALEXP_RASTER_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA:
			return MTLBlendFactorOneMinusSourceAlpha;
		case METALEXP_RASTER_BLEND_FACTOR_ONE_MINUS_SRC_COLOR:
			return MTLBlendFactorOneMinusSourceColor;
		case METALEXP_RASTER_BLEND_FACTOR_SRC_ALPHA:
			return MTLBlendFactorSourceAlpha;
		case METALEXP_RASTER_BLEND_FACTOR_SRC_ALPHA_SATURATE:
			return MTLBlendFactorSourceAlphaSaturated;
		case METALEXP_RASTER_BLEND_FACTOR_SRC_COLOR:
			return MTLBlendFactorSourceColor;
		case METALEXP_RASTER_BLEND_FACTOR_ZERO:
			return MTLBlendFactorZero;
		default:
			@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
				reason:@"Metal raster draw received an unsupported blend factor."
				userInfo:nil];
	}
}

static MTLColorWriteMask metalexp_raster_color_write_mask(uint32_t write_mask) {
	MTLColorWriteMask result = MTLColorWriteMaskNone;
	if ((write_mask & 0x1U) != 0U) {
		result |= MTLColorWriteMaskRed;
	}
	if ((write_mask & 0x2U) != 0U) {
		result |= MTLColorWriteMaskGreen;
	}
	if ((write_mask & 0x4U) != 0U) {
		result |= MTLColorWriteMaskBlue;
	}
	if ((write_mask & 0x8U) != 0U) {
		result |= MTLColorWriteMaskAlpha;
	}
	return result;
}

static void metalexp_configure_raster_vertex_descriptor(MTLVertexDescriptor *vertex_descriptor, uint32_t vertex_layout) {
	vertex_descriptor.attributes[0].offset = 0;
	vertex_descriptor.attributes[0].bufferIndex = 0;
	vertex_descriptor.attributes[0].format = MTLVertexFormatFloat3;
	switch (vertex_layout) {
		case METALEXP_RASTER_LAYOUT_POSITION:
			vertex_descriptor.layouts[0].stride = 12;
			return;
		case METALEXP_RASTER_LAYOUT_POSITION_COLOR:
			vertex_descriptor.attributes[1].offset = 12;
			vertex_descriptor.attributes[1].bufferIndex = 0;
			vertex_descriptor.attributes[1].format = MTLVertexFormatUChar4Normalized;
			vertex_descriptor.layouts[0].stride = 16;
			return;
		case METALEXP_RASTER_LAYOUT_POSITION_TEX:
			vertex_descriptor.attributes[1].offset = 12;
			vertex_descriptor.attributes[1].bufferIndex = 0;
			vertex_descriptor.attributes[1].format = MTLVertexFormatFloat2;
			vertex_descriptor.layouts[0].stride = 20;
			return;
		case METALEXP_RASTER_LAYOUT_POSITION_TEX_COLOR:
			vertex_descriptor.attributes[1].offset = 12;
			vertex_descriptor.attributes[1].bufferIndex = 0;
			vertex_descriptor.attributes[1].format = MTLVertexFormatFloat2;
			vertex_descriptor.attributes[2].offset = 20;
			vertex_descriptor.attributes[2].bufferIndex = 0;
			vertex_descriptor.attributes[2].format = MTLVertexFormatUChar4Normalized;
			vertex_descriptor.layouts[0].stride = 24;
			return;
		case METALEXP_RASTER_LAYOUT_POSITION_COLOR_NORMAL_LINE_WIDTH:
			vertex_descriptor.attributes[1].offset = 12;
			vertex_descriptor.attributes[1].bufferIndex = 0;
			vertex_descriptor.attributes[1].format = MTLVertexFormatUChar4Normalized;
			vertex_descriptor.layouts[0].stride = 24;
			return;
		default:
			@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
				reason:@"Metal generic raster path received an unsupported vertex layout."
				userInfo:nil];
	}
}

static void metalexp_raster_shader_names(uint32_t shader_family, NSString **vertex_name, NSString **fragment_name) {
	switch (shader_family) {
		case METALEXP_RASTER_SHADER_GUI_COLOR:
		case METALEXP_RASTER_SHADER_POSITION_COLOR:
			*vertex_name = @"metalexp_gui_color_vertex";
			*fragment_name = @"metalexp_gui_color_fragment";
			return;
		case METALEXP_RASTER_SHADER_GUI_TEXTURED:
			*vertex_name = @"metalexp_gui_textured_vertex";
			*fragment_name = @"metalexp_gui_textured_fragment";
			return;
		case METALEXP_RASTER_SHADER_PANORAMA:
			*vertex_name = @"metalexp_panorama_vertex";
			*fragment_name = @"metalexp_panorama_fragment";
			return;
		case METALEXP_RASTER_SHADER_SKY:
			*vertex_name = @"metalexp_position_vertex";
			*fragment_name = @"metalexp_sky_fragment";
			return;
		case METALEXP_RASTER_SHADER_STARS:
			*vertex_name = @"metalexp_position_vertex";
			*fragment_name = @"metalexp_stars_fragment";
			return;
		case METALEXP_RASTER_SHADER_POSITION_TEX:
			*vertex_name = @"metalexp_position_tex_vertex";
			*fragment_name = @"metalexp_position_tex_fragment";
			return;
		case METALEXP_RASTER_SHADER_LINE:
			*vertex_name = @"metalexp_line_vertex";
			*fragment_name = @"metalexp_line_fragment";
			return;
		default:
			@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
				reason:@"Metal generic raster path received an unknown shader family."
				userInfo:nil];
	}
}

static id<MTLRenderPipelineState> metalexp_raster_pipeline_state(id<MTLDevice> device, const metalexp_raster_pipeline_desc *pipeline_desc, MTLPixelFormat depth_format) {
	static id<MTLDevice> cached_device = nil;
	static NSMutableDictionary<NSString *, id<MTLRenderPipelineState>> *pipeline_cache = nil;
	if (cached_device != device || pipeline_cache == nil) {
		cached_device = device;
		pipeline_cache = [[NSMutableDictionary alloc] init];
	}

	NSString *cache_key = metalexp_raster_pipeline_key(pipeline_desc, depth_format);
	id<MTLRenderPipelineState> cached = pipeline_cache[cache_key];
	if (cached != nil) {
		return cached;
	}

	id<MTLLibrary> library = metalexp_raster_library(device);
	NSString *vertex_name = nil;
	NSString *fragment_name = nil;
	metalexp_raster_shader_names(pipeline_desc->shader_family, &vertex_name, &fragment_name);

	MTLVertexDescriptor *vertex_descriptor = [[MTLVertexDescriptor alloc] init];
	metalexp_configure_raster_vertex_descriptor(vertex_descriptor, pipeline_desc->vertex_layout);

	MTLRenderPipelineDescriptor *descriptor = [[MTLRenderPipelineDescriptor alloc] init];
	descriptor.vertexFunction = [library newFunctionWithName:vertex_name];
	descriptor.fragmentFunction = [library newFunctionWithName:fragment_name];
	descriptor.vertexDescriptor = vertex_descriptor;
	descriptor.colorAttachments[0].pixelFormat = MTLPixelFormatRGBA8Unorm;
	descriptor.colorAttachments[0].writeMask = metalexp_raster_color_write_mask(pipeline_desc->color_write_mask);
	if ((pipeline_desc->flags & METALEXP_RASTER_FLAG_BLEND_ENABLED) != 0U) {
		descriptor.colorAttachments[0].blendingEnabled = YES;
		descriptor.colorAttachments[0].rgbBlendOperation = metalexp_raster_blend_operation(pipeline_desc->color_blend_op);
		descriptor.colorAttachments[0].alphaBlendOperation = metalexp_raster_blend_operation(pipeline_desc->alpha_blend_op);
		descriptor.colorAttachments[0].sourceRGBBlendFactor = metalexp_raster_blend_factor(pipeline_desc->src_color_factor);
		descriptor.colorAttachments[0].destinationRGBBlendFactor = metalexp_raster_blend_factor(pipeline_desc->dst_color_factor);
		descriptor.colorAttachments[0].sourceAlphaBlendFactor = metalexp_raster_blend_factor(pipeline_desc->src_alpha_factor);
		descriptor.colorAttachments[0].destinationAlphaBlendFactor = metalexp_raster_blend_factor(pipeline_desc->dst_alpha_factor);
	}
	if (depth_format != MTLPixelFormatInvalid) {
		descriptor.depthAttachmentPixelFormat = depth_format;
	}

	NSError *error = nil;
	id<MTLRenderPipelineState> pipeline_state = [device newRenderPipelineStateWithDescriptor:descriptor error:&error];
	if (pipeline_state == nil) {
		@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
			reason:error.localizedDescription == nil ? @"Metal generic raster pipeline creation failed." : error.localizedDescription
			userInfo:nil];
	}

	pipeline_cache[cache_key] = pipeline_state;
	return pipeline_state;
}

static id<MTLDepthStencilState> metalexp_raster_depth_state(id<MTLDevice> device, const metalexp_raster_pipeline_desc *pipeline_desc) {
	static id<MTLDevice> cached_device = nil;
	static NSMutableDictionary<NSString *, id<MTLDepthStencilState>> *depth_cache = nil;
	if (cached_device != device || depth_cache == nil) {
		cached_device = device;
		depth_cache = [[NSMutableDictionary alloc] init];
	}

	NSString *cache_key = metalexp_raster_depth_key(pipeline_desc);
	id<MTLDepthStencilState> cached = depth_cache[cache_key];
	if (cached != nil) {
		return cached;
	}

	MTLDepthStencilDescriptor *descriptor = [[MTLDepthStencilDescriptor alloc] init];
	descriptor.depthCompareFunction = metalexp_raster_compare_function(pipeline_desc->depth_compare);
	descriptor.depthWriteEnabled = (pipeline_desc->flags & METALEXP_RASTER_FLAG_DEPTH_WRITE) != 0U;
	id<MTLDepthStencilState> depth_state = [device newDepthStencilStateWithDescriptor:descriptor];
	if (depth_state == nil) {
		@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
			reason:@"Metal generic raster path could not create a depth stencil state."
			userInfo:nil];
	}

	depth_cache[cache_key] = depth_state;
	return depth_state;
}

void metalexp_draw_raster_pass(
	jlong native_command_context_handle,
	jlong native_color_texture_handle,
	jlong native_depth_texture_handle,
	const metalexp_raster_pipeline_desc *pipeline_desc,
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
	if (pipeline_desc == NULL || vertex_bytes == NULL || index_bytes == NULL || projection_bytes == NULL || dynamic_bytes == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
			reason:@"Metal raster draw requires a pipeline description plus direct vertex, index, projection, and transform buffers."
			userInfo:nil];
	}
	if (index_count <= 0) {
		return;
	}
	if (vertex_stride <= 0 || index_type_bytes <= 0) {
		@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
			reason:@"Metal raster draw requires positive vertex stride and index type width."
			userInfo:nil];
	}
	if (projection_capacity < (jlong)sizeof(metalexp_projection_uniform) || dynamic_capacity < (jlong)sizeof(metalexp_dynamic_transforms_uniform)) {
		@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
			reason:@"Metal raster draw uniform buffers are smaller than the required std140 payloads."
			userInfo:nil];
	}
	if ((pipeline_desc->flags & METALEXP_RASTER_FLAG_SAMPLER2_REQUIRED) != 0U) {
		@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
			reason:@"Metal generic raster path does not support Sampler2."
			userInfo:nil];
	}
	if ((pipeline_desc->flags & METALEXP_RASTER_FLAG_SAMPLER0_REQUIRED) != 0U && native_sampler0_texture_handle == 0L) {
		@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
			reason:@"Metal raster draw requires a native-backed Sampler0 texture."
			userInfo:nil];
	}

	metalexp_native_texture *color_texture = metalexp_require_texture(native_color_texture_handle, "raster draw color");
	id<MTLTexture> target_texture = metalexp_texture_object(color_texture);
	id<MTLDevice> device = target_texture.device;
	if (device == nil) {
		@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
			reason:@"Metal raster draw requires an active MTLDevice on the target texture."
			userInfo:nil];
	}

	metalexp_native_texture *depth_texture = NULL;
	id<MTLTexture> depth_texture_object = nil;
	MTLPixelFormat depth_format = MTLPixelFormatInvalid;
	BOOL uses_depth_attachment = native_depth_texture_handle != 0L
		&& (pipeline_desc->shader_family == METALEXP_RASTER_SHADER_LINE
			|| (pipeline_desc->flags & METALEXP_RASTER_FLAG_WANTS_DEPTH_TEXTURE) != 0U
			|| (pipeline_desc->flags & METALEXP_RASTER_FLAG_DEPTH_WRITE) != 0U);
	if (uses_depth_attachment) {
		depth_texture = metalexp_require_texture(native_depth_texture_handle, "raster draw depth");
		depth_texture_object = metalexp_texture_object(depth_texture);
		depth_format = depth_texture_object.pixelFormat;
	}

	id<MTLCommandBuffer> command_buffer = metalexp_command_buffer_for_context(native_command_context_handle, device, "raster draw");
	MTLRenderPassDescriptor *pass_descriptor = [MTLRenderPassDescriptor renderPassDescriptor];
	pass_descriptor.colorAttachments[0].texture = target_texture;
	pass_descriptor.colorAttachments[0].loadAction = MTLLoadActionLoad;
	pass_descriptor.colorAttachments[0].storeAction = MTLStoreActionStore;
	if (depth_texture_object != nil) {
		pass_descriptor.depthAttachment.texture = depth_texture_object;
		pass_descriptor.depthAttachment.loadAction = MTLLoadActionLoad;
		pass_descriptor.depthAttachment.storeAction = MTLStoreActionStore;
	}

	id<MTLRenderCommandEncoder> encoder = [command_buffer renderCommandEncoderWithDescriptor:pass_descriptor];
	if (encoder == nil) {
		@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
			reason:@"Metal raster draw could not allocate an MTLRenderCommandEncoder."
			userInfo:nil];
	}

	id<MTLRenderPipelineState> pipeline_state = metalexp_raster_pipeline_state(device, pipeline_desc, depth_format);
	id<MTLBuffer> vertex_buffer = [device newBufferWithBytesNoCopy:(void *)vertex_bytes length:(NSUInteger)vertex_capacity options:MTLResourceStorageModeShared deallocator:nil];
	id<MTLBuffer> index_buffer = [device newBufferWithBytesNoCopy:(void *)index_bytes length:(NSUInteger)index_capacity options:MTLResourceStorageModeShared deallocator:nil];
	metalexp_staging_buffer_slice dynamic_slice = metalexp_stage_uniform_bytes(
		native_command_context_handle,
		device,
		dynamic_bytes,
		(NSUInteger)sizeof(metalexp_dynamic_transforms_uniform),
		"raster transform stage"
	);
	metalexp_staging_buffer_slice projection_slice = metalexp_stage_uniform_bytes(
		native_command_context_handle,
		device,
		projection_bytes,
		(NSUInteger)sizeof(metalexp_projection_uniform),
		"raster projection stage"
	);
	if (vertex_buffer == nil || index_buffer == nil) {
		@throw [NSException exceptionWithName:@"MetalExpRasterPipelineException"
			reason:@"Metal raster draw could not wrap direct vertex or index memory as a shared MTLBuffer."
			userInfo:nil];
	}

	[encoder setRenderPipelineState:pipeline_state];
	[encoder setVertexBuffer:vertex_buffer offset:0 atIndex:0];
	[encoder setVertexBuffer:dynamic_slice.buffer offset:dynamic_slice.offset atIndex:1];
	[encoder setVertexBuffer:projection_slice.buffer offset:projection_slice.offset atIndex:2];
	[encoder setFragmentBuffer:dynamic_slice.buffer offset:dynamic_slice.offset atIndex:1];
	[encoder setViewport:(MTLViewport){0.0, 0.0, (double)color_texture->width, (double)color_texture->height, 0.0, 1.0}];
	[encoder setCullMode:(pipeline_desc->flags & METALEXP_RASTER_FLAG_CULL) != 0U ? MTLCullModeBack : MTLCullModeNone];
	if (depth_texture_object != nil) {
		[encoder setDepthStencilState:metalexp_raster_depth_state(device, pipeline_desc)];
	}
	if (pipeline_desc->depth_bias_scale_factor != 0.0F || pipeline_desc->depth_bias_constant != 0.0F) {
		[encoder setDepthBias:pipeline_desc->depth_bias_constant slopeScale:pipeline_desc->depth_bias_scale_factor clamp:0.0];
	}

	if (scissor_enabled == JNI_TRUE) {
		MTLScissorRect rect = {
			.x = (NSUInteger)MAX(scissor_x, 0),
			.y = (NSUInteger)MAX(scissor_y, 0),
			.width = (NSUInteger)MAX(scissor_width, 0),
			.height = (NSUInteger)MAX(scissor_height, 0)
		};
		[encoder setScissorRect:rect];
	}

	if ((pipeline_desc->flags & METALEXP_RASTER_FLAG_SAMPLER0_REQUIRED) != 0U) {
		metalexp_native_texture *sampler0_texture = metalexp_require_texture(native_sampler0_texture_handle, "raster sampler0");
		id<MTLSamplerState> sampler_state = metalexp_sampler_state(device, linear_filtering == JNI_TRUE, repeat_u == JNI_TRUE, repeat_v == JNI_TRUE);
		id<MTLTexture> texture_object = metalexp_texture_object(sampler0_texture);
		[encoder setFragmentTexture:texture_object atIndex:0];
		[encoder setFragmentSamplerState:sampler_state atIndex:0];
	}

	[encoder drawIndexedPrimitives:metalexp_raster_primitive_type(pipeline_desc->primitive_topology)
		indexCount:(NSUInteger)index_count
		indexType:index_type_bytes == 2 ? MTLIndexTypeUInt16 : MTLIndexTypeUInt32
		indexBuffer:index_buffer
		indexBufferOffset:(NSUInteger)MAX(first_index, 0) * (NSUInteger)index_type_bytes
		instanceCount:1
		baseVertex:(NSInteger)base_vertex
		baseInstance:0];
	[encoder endEncoding];
}
