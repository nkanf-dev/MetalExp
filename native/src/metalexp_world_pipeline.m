#include "metalexp_common.h"

static NSString *metalexp_world_shader_source(void) {
	return @"#include <metal_stdlib>\n"
		"using namespace metal;\n"
		"struct Projection {\n"
		"    float4x4 ProjMat;\n"
		"};\n"
		"struct WorldUniforms {\n"
		"    float4x4 ModelViewMat;\n"
		"    float4 FogColor;\n"
		"    float4 ChunkPositionVisibility;\n"
		"    float4 CameraBlockPos;\n"
		"    float4 CameraOffset;\n"
		"    float4 FogParams;\n"
		"    float4 TerrainParams;\n"
		"};\n"
		"struct BlockVertexIn {\n"
		"    float3 position [[attribute(0)]];\n"
		"    float4 color [[attribute(1)]];\n"
		"    float2 uv0 [[attribute(2)]];\n"
		"    ushort2 uv2 [[attribute(3)]];\n"
		"};\n"
		"struct BlockRasterOut {\n"
		"    float4 position [[position]];\n"
		"    float4 color;\n"
		"    float2 uv0;\n"
		"    float sphericalVertexDistance;\n"
		"    float cylindricalVertexDistance;\n"
		"    float chunkVisibility;\n"
		"};\n"
		"float4 metalexp_gl_clip_to_metal(float4 clip) {\n"
		"    clip.z = (clip.z + clip.w) * 0.5;\n"
		"    return clip;\n"
		"}\n"
		"float metalexp_linear_fog_value(float vertexDistance, float fogStart, float fogEnd) {\n"
		"    if (vertexDistance <= fogStart) {\n"
		"        return 0.0;\n"
		"    }\n"
		"    if (vertexDistance >= fogEnd) {\n"
		"        return 1.0;\n"
		"    }\n"
		"    return (vertexDistance - fogStart) / (fogEnd - fogStart);\n"
		"}\n"
		"float metalexp_total_fog_value(float sphericalVertexDistance, float cylindricalVertexDistance, float environmentalStart, float environmentalEnd, float renderDistanceStart, float renderDistanceEnd) {\n"
		"    return max(metalexp_linear_fog_value(sphericalVertexDistance, environmentalStart, environmentalEnd), metalexp_linear_fog_value(cylindricalVertexDistance, renderDistanceStart, renderDistanceEnd));\n"
		"}\n"
		"float2 metalexp_lightmap_uv(ushort2 uv2) {\n"
		"    float2 uv = float2(uv2) / 256.0 + float2(0.5 / 16.0);\n"
		"    return clamp(uv, float2(0.5 / 16.0), float2(15.5 / 16.0));\n"
		"}\n"
		"float2 metalexp_terrain_pixel_size(constant WorldUniforms& world) {\n"
		"    float2 textureSize = world.TerrainParams.xy;\n"
		"    if (textureSize.x <= 0.0 || textureSize.y <= 0.0) {\n"
		"        return float2(0.0, 0.0);\n"
		"    }\n"
		"    return 1.0 / textureSize;\n"
		"}\n"
		"float4 metalexp_sample_nearest(texture2d<float> source, sampler samplerState, float2 uv, float2 pixelSize, float2 du, float2 dv, float2 texelScreenSize) {\n"
		"    float2 uvTexelCoords = uv / pixelSize;\n"
		"    float2 texelCenter = round(uvTexelCoords) - 0.5;\n"
		"    float2 texelOffset = uvTexelCoords - texelCenter;\n"
		"    texelOffset = (texelOffset - 0.5) * pixelSize / texelScreenSize + 0.5;\n"
		"    texelOffset = clamp(texelOffset, 0.0, 1.0);\n"
		"    float2 adjustedUv = (texelCenter + texelOffset) * pixelSize;\n"
		"    return source.sample(samplerState, adjustedUv, gradient2d(du, dv));\n"
		"}\n"
		"float4 metalexp_sample_nearest(texture2d<float> source, sampler samplerState, float2 uv, float2 pixelSize) {\n"
		"    float2 du = dfdx(uv);\n"
		"    float2 dv = dfdy(uv);\n"
		"    float2 texelScreenSize = sqrt(du * du + dv * dv);\n"
		"    return metalexp_sample_nearest(source, samplerState, uv, pixelSize, du, dv, texelScreenSize);\n"
		"}\n"
		"float4 metalexp_sample_rgss(texture2d<float> source, sampler samplerState, float2 uv, float2 pixelSize) {\n"
		"    float2 du = dfdx(uv);\n"
		"    float2 dv = dfdy(uv);\n"
		"    float2 texelScreenSize = sqrt(du * du + dv * dv);\n"
		"    float maxTexelSize = max(texelScreenSize.x, texelScreenSize.y);\n"
		"    float minPixelSize = min(pixelSize.x, pixelSize.y);\n"
		"    float blendFactor = smoothstep(minPixelSize, minPixelSize * 2.0, maxTexelSize);\n"
		"    float duLength = length(du);\n"
		"    float dvLength = length(dv);\n"
		"    float effectiveDerivative = sqrt(min(duLength, dvLength) * max(duLength, dvLength));\n"
		"    float mipLevelExact = max(0.0, log2(effectiveDerivative / minPixelSize));\n"
		"    float mipLevelLow = floor(mipLevelExact);\n"
		"    float mipLevelHigh = mipLevelLow + 1.0;\n"
		"    float mipBlend = fract(mipLevelExact);\n"
		"    float2 offsets[4] = {\n"
		"        float2(0.125, 0.375),\n"
		"        float2(-0.125, -0.375),\n"
		"        float2(0.375, -0.125),\n"
		"        float2(-0.375, 0.125)\n"
		"    };\n"
		"    float4 rgssColorLow = float4(0.0);\n"
		"    float4 rgssColorHigh = float4(0.0);\n"
		"    for (uint index = 0; index < 4; index++) {\n"
		"        float2 sampleUv = uv + offsets[index] * pixelSize;\n"
		"        rgssColorLow += source.sample(samplerState, sampleUv, level(mipLevelLow));\n"
		"        rgssColorHigh += source.sample(samplerState, sampleUv, level(mipLevelHigh));\n"
		"    }\n"
		"    rgssColorLow *= 0.25;\n"
		"    rgssColorHigh *= 0.25;\n"
		"    float4 rgssColor = mix(rgssColorLow, rgssColorHigh, mipBlend);\n"
		"    float4 nearestColor = metalexp_sample_nearest(source, samplerState, uv, pixelSize, du, dv, texelScreenSize);\n"
		"    return mix(nearestColor, rgssColor, blendFactor);\n"
		"}\n"
		"float4 metalexp_sample_terrain(texture2d<float> sampler0, sampler samplerState, float2 uv, constant WorldUniforms& world) {\n"
		"    float2 pixelSize = metalexp_terrain_pixel_size(world);\n"
		"    if (pixelSize.x <= 0.0 || pixelSize.y <= 0.0) {\n"
		"        return sampler0.sample(samplerState, uv);\n"
		"    }\n"
		"    return world.TerrainParams.z >= 0.5 ? metalexp_sample_rgss(sampler0, samplerState, uv, pixelSize) : metalexp_sample_nearest(sampler0, samplerState, uv, pixelSize);\n"
		"}\n"
		"vertex BlockRasterOut metalexp_world_terrain_vertex(BlockVertexIn in [[stage_in]], constant WorldUniforms& world [[buffer(1)]], constant Projection& projection [[buffer(2)]], texture2d<float> sampler2 [[texture(0)]], sampler lightmapSampler [[sampler(0)]]) {\n"
		"    BlockRasterOut out;\n"
		"    float3 pos = in.position + (world.ChunkPositionVisibility.xyz - world.CameraBlockPos.xyz) + world.CameraOffset.xyz;\n"
		"    out.position = metalexp_gl_clip_to_metal(projection.ProjMat * world.ModelViewMat * float4(pos, 1.0));\n"
		"    out.color = in.color * sampler2.sample(lightmapSampler, metalexp_lightmap_uv(in.uv2));\n"
		"    out.uv0 = in.uv0;\n"
		"    out.sphericalVertexDistance = length(pos);\n"
		"    out.cylindricalVertexDistance = max(length(pos.xz), abs(pos.y));\n"
		"    out.chunkVisibility = world.ChunkPositionVisibility.w;\n"
		"    return out;\n"
		"}\n"
		"float4 metalexp_world_terrain_color(BlockRasterOut in, constant WorldUniforms& world, texture2d<float> sampler0, sampler samplerState) {\n"
		"    float4 color = metalexp_sample_terrain(sampler0, samplerState, in.uv0, world) * in.color;\n"
		"    color = mix(world.FogColor * float4(1.0, 1.0, 1.0, color.a), color, in.chunkVisibility);\n"
		"    float fogValue = metalexp_total_fog_value(in.sphericalVertexDistance, in.cylindricalVertexDistance, world.FogParams.x, world.FogParams.y, world.FogParams.z, world.FogParams.w);\n"
		"    return float4(mix(color.rgb, world.FogColor.rgb, fogValue * world.FogColor.a), color.a);\n"
		"}\n"
		"fragment float4 metalexp_world_terrain_fragment(BlockRasterOut in [[stage_in]], constant WorldUniforms& world [[buffer(1)]], texture2d<float> sampler0 [[texture(0)]], sampler samplerState [[sampler(0)]]) {\n"
		"    return metalexp_world_terrain_color(in, world, sampler0, samplerState);\n"
		"}\n"
		"fragment float4 metalexp_world_terrain_cutout_fragment(BlockRasterOut in [[stage_in]], constant WorldUniforms& world [[buffer(1)]], texture2d<float> sampler0 [[texture(0)]], sampler samplerState [[sampler(0)]]) {\n"
		"    float4 color = metalexp_world_terrain_color(in, world, sampler0, samplerState);\n"
		"    if (color.a < 0.5) {\n"
		"        discard_fragment();\n"
		"    }\n"
		"    return color;\n"
		"}\n"
		"fragment float4 metalexp_world_terrain_translucent_fragment(BlockRasterOut in [[stage_in]], constant WorldUniforms& world [[buffer(1)]], texture2d<float> sampler0 [[texture(0)]], sampler samplerState [[sampler(0)]]) {\n"
		"    float4 color = metalexp_world_terrain_color(in, world, sampler0, samplerState);\n"
		"    if (color.a < 0.01) {\n"
		"        discard_fragment();\n"
		"    }\n"
		"    return color;\n"
		"}\n";
}

static id<MTLLibrary> metalexp_world_library(id<MTLDevice> device) {
	static id<MTLDevice> cached_device = nil;
	static id<MTLLibrary> cached_library = nil;

	if (cached_library != nil && cached_device == device) {
		return cached_library;
	}

	NSError *error = nil;
	id<MTLLibrary> library = [device newLibraryWithSource:metalexp_world_shader_source() options:nil error:&error];
	if (library == nil) {
		@throw [NSException exceptionWithName:@"MetalExpWorldPipelineException"
			reason:error.localizedDescription == nil ? @"Metal world shader library compilation failed." : error.localizedDescription
			userInfo:nil];
	}

	cached_device = device;
	cached_library = library;
	return cached_library;
}

static id<MTLDepthStencilState> metalexp_world_depth_stencil_state(id<MTLDevice> device, BOOL depth_write_enabled) {
	static id<MTLDevice> cached_device = nil;
	static id<MTLDepthStencilState> cached_depth_write_state = nil;
	static id<MTLDepthStencilState> cached_depth_read_state = nil;

	if (cached_device == device) {
		if (depth_write_enabled && cached_depth_write_state != nil) {
			return cached_depth_write_state;
		}
		if (!depth_write_enabled && cached_depth_read_state != nil) {
			return cached_depth_read_state;
		}
	}

	MTLDepthStencilDescriptor *descriptor = [[MTLDepthStencilDescriptor alloc] init];
	descriptor.depthCompareFunction = MTLCompareFunctionLessEqual;
	descriptor.depthWriteEnabled = depth_write_enabled;
	id<MTLDepthStencilState> depth_state = [device newDepthStencilStateWithDescriptor:descriptor];
	if (depth_state == nil) {
		@throw [NSException exceptionWithName:@"MetalExpWorldPipelineException"
			reason:@"Metal world pipeline could not create a depth stencil state."
			userInfo:nil];
	}

	cached_device = device;
	if (depth_write_enabled) {
		cached_depth_write_state = depth_state;
	} else {
		cached_depth_read_state = depth_state;
	}

	return depth_state;
}

static id<MTLRenderPipelineState> metalexp_world_pipeline_state(id<MTLDevice> device, jint pipeline_kind) {
	static id<MTLDevice> cached_device = nil;
	static id<MTLRenderPipelineState> cached_solid_pipeline = nil;
	static id<MTLRenderPipelineState> cached_cutout_pipeline = nil;
	static id<MTLRenderPipelineState> cached_translucent_pipeline = nil;

	if (cached_device == device) {
		if (pipeline_kind == METALEXP_PIPELINE_WORLD_TERRAIN_SOLID && cached_solid_pipeline != nil) {
			return cached_solid_pipeline;
		}
		if (pipeline_kind == METALEXP_PIPELINE_WORLD_TERRAIN_CUTOUT && cached_cutout_pipeline != nil) {
			return cached_cutout_pipeline;
		}
		if (pipeline_kind == METALEXP_PIPELINE_WORLD_TERRAIN_TRANSLUCENT && cached_translucent_pipeline != nil) {
			return cached_translucent_pipeline;
		}
	}

	id<MTLLibrary> library = metalexp_world_library(device);
	NSString *fragment_name = @"metalexp_world_terrain_fragment";
	if (pipeline_kind == METALEXP_PIPELINE_WORLD_TERRAIN_CUTOUT) {
		fragment_name = @"metalexp_world_terrain_cutout_fragment";
	} else if (pipeline_kind == METALEXP_PIPELINE_WORLD_TERRAIN_TRANSLUCENT) {
		fragment_name = @"metalexp_world_terrain_translucent_fragment";
	}

	MTLVertexDescriptor *vertex_descriptor = [[MTLVertexDescriptor alloc] init];
	vertex_descriptor.attributes[0].offset = 0;
	vertex_descriptor.attributes[0].bufferIndex = 0;
	vertex_descriptor.attributes[0].format = MTLVertexFormatFloat3;
	vertex_descriptor.attributes[1].offset = 12;
	vertex_descriptor.attributes[1].bufferIndex = 0;
	vertex_descriptor.attributes[1].format = MTLVertexFormatUChar4Normalized;
	vertex_descriptor.attributes[2].offset = 16;
	vertex_descriptor.attributes[2].bufferIndex = 0;
	vertex_descriptor.attributes[2].format = MTLVertexFormatFloat2;
	vertex_descriptor.attributes[3].offset = 24;
	vertex_descriptor.attributes[3].bufferIndex = 0;
	vertex_descriptor.attributes[3].format = MTLVertexFormatUShort2;
	vertex_descriptor.layouts[0].stride = 28;

	MTLRenderPipelineDescriptor *descriptor = [[MTLRenderPipelineDescriptor alloc] init];
	descriptor.vertexFunction = [library newFunctionWithName:@"metalexp_world_terrain_vertex"];
	descriptor.fragmentFunction = [library newFunctionWithName:fragment_name];
	descriptor.vertexDescriptor = vertex_descriptor;
	descriptor.colorAttachments[0].pixelFormat = MTLPixelFormatRGBA8Unorm;
	descriptor.depthAttachmentPixelFormat = MTLPixelFormatDepth32Float;

	if (pipeline_kind == METALEXP_PIPELINE_WORLD_TERRAIN_TRANSLUCENT) {
		descriptor.colorAttachments[0].blendingEnabled = YES;
		descriptor.colorAttachments[0].rgbBlendOperation = MTLBlendOperationAdd;
		descriptor.colorAttachments[0].alphaBlendOperation = MTLBlendOperationAdd;
		descriptor.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorSourceAlpha;
		descriptor.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorOne;
		descriptor.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
		descriptor.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
	} else {
		descriptor.colorAttachments[0].blendingEnabled = NO;
	}

	NSError *error = nil;
	id<MTLRenderPipelineState> pipeline_state = [device newRenderPipelineStateWithDescriptor:descriptor error:&error];
	if (pipeline_state == nil) {
		@throw [NSException exceptionWithName:@"MetalExpWorldPipelineException"
			reason:error.localizedDescription == nil ? @"Metal world render pipeline creation failed." : error.localizedDescription
			userInfo:nil];
	}

	cached_device = device;
	if (pipeline_kind == METALEXP_PIPELINE_WORLD_TERRAIN_SOLID) {
		cached_solid_pipeline = pipeline_state;
	} else if (pipeline_kind == METALEXP_PIPELINE_WORLD_TERRAIN_CUTOUT) {
		cached_cutout_pipeline = pipeline_state;
	} else if (pipeline_kind == METALEXP_PIPELINE_WORLD_TERRAIN_TRANSLUCENT) {
		cached_translucent_pipeline = pipeline_state;
	}

	return pipeline_state;
}

void metalexp_draw_world_pass(
	jlong native_command_context_handle,
	jlong native_color_texture_handle,
	jlong native_depth_texture_handle,
	jboolean clear_depth,
	jfloat clear_depth_value,
	jint pipeline_kind,
	jlong native_vertex_buffer_handle,
	jint vertex_stride,
	jint base_vertex,
	jlong native_index_buffer_handle,
	jint index_type_bytes,
	jint first_index,
	jint index_count,
	const void *projection_bytes,
	jlong projection_capacity,
	const void *world_bytes,
	jlong world_capacity,
	jlong native_sampler0_texture_handle,
	jlong native_sampler2_texture_handle,
	jboolean linear_filtering,
	jboolean repeat_u,
	jboolean repeat_v,
	jboolean sampler2_linear_filtering,
	jboolean sampler2_repeat_u,
	jboolean sampler2_repeat_v
) {
	if (projection_bytes == NULL || world_bytes == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpWorldPipelineException"
			reason:@"Metal world draw requires native vertex/index buffers plus direct projection and world uniform buffers."
			userInfo:nil];
	}
	if (index_count <= 0) {
		return;
	}
	if (vertex_stride != 28) {
		@throw [NSException exceptionWithName:@"MetalExpWorldPipelineException"
			reason:@"Metal world terrain draw currently requires the DefaultVertexFormat.BLOCK stride."
			userInfo:nil];
	}
	if (index_type_bytes <= 0 || projection_capacity < (jlong)sizeof(metalexp_projection_uniform) || world_capacity < (jlong)sizeof(metalexp_world_uniform)) {
		@throw [NSException exceptionWithName:@"MetalExpWorldPipelineException"
			reason:@"Metal world draw buffers are smaller than the required native payloads."
			userInfo:nil];
	}

	metalexp_native_texture *color_texture = metalexp_require_texture(native_color_texture_handle, "world color draw");
	metalexp_native_texture *depth_texture = metalexp_require_texture(native_depth_texture_handle, "world depth draw");
	metalexp_native_texture *sampler0_texture = metalexp_require_texture(native_sampler0_texture_handle, "world sampler0");
	metalexp_native_texture *sampler2_texture = metalexp_require_texture(native_sampler2_texture_handle, "world sampler2");
	metalexp_native_buffer *vertex_native_buffer = metalexp_require_buffer(native_vertex_buffer_handle, "world vertex draw");
	metalexp_native_buffer *index_native_buffer = metalexp_require_buffer(native_index_buffer_handle, "world index draw");
	id<MTLTexture> color = metalexp_texture_object(color_texture);
	id<MTLTexture> depth = metalexp_texture_object(depth_texture);
	id<MTLTexture> sampler0 = metalexp_texture_object(sampler0_texture);
	id<MTLTexture> sampler2 = metalexp_texture_object(sampler2_texture);
	id<MTLBuffer> vertex_buffer = metalexp_buffer_object(vertex_native_buffer);
	id<MTLBuffer> index_buffer = metalexp_buffer_object(index_native_buffer);
	id<MTLDevice> device = color.device;
	if (device == nil || depth.device != device || sampler0.device != device || sampler2.device != device || vertex_buffer.device != device || index_buffer.device != device) {
		@throw [NSException exceptionWithName:@"MetalExpWorldPipelineException"
			reason:@"Metal world draw requires color, depth, sampler textures, and buffers on the same MTLDevice."
			userInfo:nil];
	}

	id<MTLCommandBuffer> command_buffer = metalexp_command_buffer_for_context(native_command_context_handle, device, "world draw");
	MTLRenderPassDescriptor *pass_descriptor = [MTLRenderPassDescriptor renderPassDescriptor];
	pass_descriptor.colorAttachments[0].texture = color;
	pass_descriptor.colorAttachments[0].loadAction = MTLLoadActionLoad;
	pass_descriptor.colorAttachments[0].storeAction = MTLStoreActionStore;
	pass_descriptor.depthAttachment.texture = depth;
	pass_descriptor.depthAttachment.loadAction = clear_depth == JNI_TRUE ? MTLLoadActionClear : MTLLoadActionLoad;
	pass_descriptor.depthAttachment.storeAction = MTLStoreActionStore;
	if (clear_depth == JNI_TRUE) {
		pass_descriptor.depthAttachment.clearDepth = (double)clear_depth_value;
	}

	id<MTLRenderCommandEncoder> encoder = [command_buffer renderCommandEncoderWithDescriptor:pass_descriptor];
	if (encoder == nil) {
		@throw [NSException exceptionWithName:@"MetalExpWorldPipelineException"
			reason:@"Metal world draw could not allocate an MTLRenderCommandEncoder."
			userInfo:nil];
	}

	id<MTLRenderPipelineState> pipeline_state = metalexp_world_pipeline_state(device, pipeline_kind);
	metalexp_staging_buffer_slice projection_slice = metalexp_stage_uniform_bytes(
		native_command_context_handle,
		device,
		projection_bytes,
		(NSUInteger)sizeof(metalexp_projection_uniform),
		"world projection stage"
	);
	metalexp_staging_buffer_slice world_slice = metalexp_stage_uniform_bytes(
		native_command_context_handle,
		device,
		world_bytes,
		(NSUInteger)sizeof(metalexp_world_uniform),
		"world uniform stage"
	);

	[encoder setRenderPipelineState:pipeline_state];
	[encoder setDepthStencilState:metalexp_world_depth_stencil_state(device, pipeline_kind != METALEXP_PIPELINE_WORLD_TERRAIN_TRANSLUCENT)];
	[encoder setVertexBuffer:vertex_buffer offset:0 atIndex:0];
	[encoder setVertexBuffer:world_slice.buffer offset:world_slice.offset atIndex:1];
	[encoder setVertexBuffer:projection_slice.buffer offset:projection_slice.offset atIndex:2];
	[encoder setVertexTexture:sampler2 atIndex:0];
	[encoder setVertexSamplerState:metalexp_sampler_state(device, sampler2_linear_filtering == JNI_TRUE, sampler2_repeat_u == JNI_TRUE, sampler2_repeat_v == JNI_TRUE) atIndex:0];
	[encoder setFragmentBuffer:world_slice.buffer offset:world_slice.offset atIndex:1];
	[encoder setFragmentTexture:sampler0 atIndex:0];
	[encoder setFragmentSamplerState:metalexp_sampler_state(device, linear_filtering == JNI_TRUE, repeat_u == JNI_TRUE, repeat_v == JNI_TRUE) atIndex:0];
	[encoder setViewport:(MTLViewport){0.0, 0.0, (double)color_texture->width, (double)color_texture->height, 0.0, 1.0}];

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
