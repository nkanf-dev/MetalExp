#include "metalexp_common.h"

typedef struct metalexp_sprite_vertex {
	vector_float2 position;
	vector_float2 local_uv;
} metalexp_sprite_vertex;

typedef struct metalexp_sprite_blit_uniform {
	vector_float2 padding;
	float source_mip_level;
	float reserved;
} metalexp_sprite_blit_uniform;

static NSString *metalexp_sprite_blit_shader_source(void) {
	return @"#include <metal_stdlib>\n"
		"using namespace metal;\n"
		"struct SpriteVertexIn {\n"
		"    float2 position [[attribute(0)]];\n"
		"    float2 localUv [[attribute(1)]];\n"
		"};\n"
		"struct SpriteRasterOut {\n"
		"    float4 position [[position]];\n"
		"    float2 localUv;\n"
		"};\n"
		"struct SpriteBlitUniform {\n"
		"    float2 padding;\n"
		"    float sourceMipLevel;\n"
		"    float reserved;\n"
		"};\n"
		"vertex SpriteRasterOut metalexp_sprite_blit_vertex(SpriteVertexIn in [[stage_in]]) {\n"
		"    SpriteRasterOut out;\n"
		"    out.position = float4(in.position, 0.0, 1.0);\n"
		"    out.localUv = in.localUv;\n"
		"    return out;\n"
		"}\n"
		"fragment float4 metalexp_sprite_blit_fragment(SpriteRasterOut in [[stage_in]], constant SpriteBlitUniform& uniforms [[buffer(0)]], texture2d<float> spriteTexture [[texture(0)]], sampler spriteSampler [[sampler(0)]]) {\n"
		"    float2 paddedUv = float2(\n"
		"        in.localUv.x + uniforms.padding.x * (in.localUv.x * 2.0 - 1.0),\n"
		"        in.localUv.y + uniforms.padding.y * (in.localUv.y * 2.0 - 1.0)\n"
		"    );\n"
		"    return spriteTexture.sample(spriteSampler, paddedUv, level(uniforms.sourceMipLevel));\n"
		"}\n";
}

static id<MTLLibrary> metalexp_sprite_blit_library(id<MTLDevice> device) {
	static id<MTLDevice> cached_device = nil;
	static id<MTLLibrary> cached_library = nil;
	if (cached_library != nil && cached_device == device) {
		return cached_library;
	}

	NSError *error = nil;
	id<MTLLibrary> library = [device newLibraryWithSource:metalexp_sprite_blit_shader_source() options:nil error:&error];
	if (library == nil) {
		@throw [NSException exceptionWithName:@"MetalExpPipelineException"
			reason:error.localizedDescription == nil ? @"Metal sprite blit shader library compilation failed." : error.localizedDescription
			userInfo:nil];
	}

	cached_device = device;
	cached_library = library;
	return cached_library;
}

static id<MTLRenderPipelineState> metalexp_sprite_blit_pipeline_state(id<MTLDevice> device) {
	static id<MTLDevice> cached_device = nil;
	static id<MTLRenderPipelineState> cached_pipeline = nil;
	if (cached_pipeline != nil && cached_device == device) {
		return cached_pipeline;
	}

	id<MTLLibrary> library = metalexp_sprite_blit_library(device);
	MTLVertexDescriptor *vertex_descriptor = [[MTLVertexDescriptor alloc] init];
	vertex_descriptor.attributes[0].offset = 0;
	vertex_descriptor.attributes[0].bufferIndex = 0;
	vertex_descriptor.attributes[0].format = MTLVertexFormatFloat2;
	vertex_descriptor.attributes[1].offset = sizeof(vector_float2);
	vertex_descriptor.attributes[1].bufferIndex = 0;
	vertex_descriptor.attributes[1].format = MTLVertexFormatFloat2;
	vertex_descriptor.layouts[0].stride = sizeof(metalexp_sprite_vertex);

	MTLRenderPipelineDescriptor *descriptor = [[MTLRenderPipelineDescriptor alloc] init];
	descriptor.vertexFunction = [library newFunctionWithName:@"metalexp_sprite_blit_vertex"];
	descriptor.fragmentFunction = [library newFunctionWithName:@"metalexp_sprite_blit_fragment"];
	descriptor.vertexDescriptor = vertex_descriptor;
	descriptor.colorAttachments[0].pixelFormat = MTLPixelFormatRGBA8Unorm;
	descriptor.colorAttachments[0].blendingEnabled = NO;

	NSError *error = nil;
	id<MTLRenderPipelineState> pipeline_state = [device newRenderPipelineStateWithDescriptor:descriptor error:&error];
	if (pipeline_state == nil) {
		@throw [NSException exceptionWithName:@"MetalExpPipelineException"
			reason:error.localizedDescription == nil ? @"Metal sprite blit pipeline creation failed." : error.localizedDescription
			userInfo:nil];
	}

	cached_device = device;
	cached_pipeline = pipeline_state;
	return pipeline_state;
}

void metalexp_blit_animated_sprite(
	jlong native_command_context_handle,
	jlong native_target_texture_handle,
	jint target_mip_level,
	jlong native_source_texture_handle,
	jint source_mip_level,
	jint dst_x,
	jint dst_y,
	jint dst_width,
	jint dst_height,
	jfloat local_u_min,
	jfloat local_v_min,
	jfloat local_u_max,
	jfloat local_v_max,
	jfloat u_padding,
	jfloat v_padding,
	jboolean linear_filtering,
	jboolean repeat_u,
	jboolean repeat_v
) {
	if (dst_width <= 0 || dst_height <= 0) {
		return;
	}

	metalexp_native_texture *target_texture = metalexp_require_texture(native_target_texture_handle, "animated sprite target");
	metalexp_native_texture *source_texture = metalexp_require_texture(native_source_texture_handle, "animated sprite source");
	if (target_mip_level < 0 || (uint32_t)target_mip_level >= target_texture->mip_levels) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal animated sprite blit received an invalid target mip level."
			userInfo:nil];
	}
	if (source_mip_level < 0 || (uint32_t)source_mip_level >= source_texture->mip_levels) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal animated sprite blit received an invalid source mip level."
			userInfo:nil];
	}
	if (target_texture->depth_or_layers != 1 || target_texture->cubemap_compatible) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal animated sprite blit requires a 2D target texture."
			userInfo:nil];
	}
	if (source_texture->depth_or_layers != 1 || source_texture->cubemap_compatible) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal animated sprite blit requires a 2D source texture."
			userInfo:nil];
	}

	id<MTLTexture> target = metalexp_texture_object(target_texture);
	id<MTLTexture> source = metalexp_texture_object(source_texture);
	id<MTLDevice> device = target.device;
	if (device == nil || source.device == nil || source.device != device) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal animated sprite blit requires source and target textures on the same active MTLDevice."
			userInfo:nil];
	}

	NSUInteger target_width = metalexp_mip_dimension(target_texture->width, target_mip_level);
	NSUInteger target_height = metalexp_mip_dimension(target_texture->height, target_mip_level);
	if (dst_x < 0 || dst_y < 0 || (NSUInteger)(dst_x + dst_width) > target_width || (NSUInteger)(dst_y + dst_height) > target_height) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal animated sprite blit destination rectangle exceeded the target mip bounds."
			userInfo:nil];
	}

	float left = ((float)dst_x / (float)target_width) * 2.0F - 1.0F;
	float right = ((float)(dst_x + dst_width) / (float)target_width) * 2.0F - 1.0F;
	float top = 1.0F - ((float)dst_y / (float)target_height) * 2.0F;
	float bottom = 1.0F - ((float)(dst_y + dst_height) / (float)target_height) * 2.0F;
	metalexp_sprite_vertex vertices[4] = {
		{ { left, top }, { local_u_min, local_v_min } },
		{ { right, top }, { local_u_max, local_v_min } },
		{ { left, bottom }, { local_u_min, local_v_max } },
		{ { right, bottom }, { local_u_max, local_v_max } }
	};
	metalexp_sprite_blit_uniform uniforms = {
		.padding = { u_padding, v_padding },
		.source_mip_level = (float)source_mip_level,
		.reserved = 0.0F
	};

	id<MTLCommandBuffer> command_buffer = metalexp_command_buffer_for_context(native_command_context_handle, device, "animated sprite blit");

	MTLRenderPassDescriptor *pass_descriptor = [MTLRenderPassDescriptor renderPassDescriptor];
	pass_descriptor.colorAttachments[0].texture = target;
	pass_descriptor.colorAttachments[0].level = (NSUInteger)target_mip_level;
	pass_descriptor.colorAttachments[0].loadAction = MTLLoadActionLoad;
	pass_descriptor.colorAttachments[0].storeAction = MTLStoreActionStore;

	id<MTLRenderCommandEncoder> encoder = [command_buffer renderCommandEncoderWithDescriptor:pass_descriptor];
	if (encoder == nil) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal animated sprite blit could not allocate an MTLRenderCommandEncoder."
			userInfo:nil];
	}

	id<MTLRenderPipelineState> pipeline_state = metalexp_sprite_blit_pipeline_state(device);
	id<MTLSamplerState> sampler_state = metalexp_sampler_state(device, linear_filtering == JNI_TRUE, repeat_u == JNI_TRUE, repeat_v == JNI_TRUE);
	if (sampler_state == nil) {
		@throw [NSException exceptionWithName:@"MetalExpDrawException"
			reason:@"Metal animated sprite blit could not allocate an MTLSamplerState."
			userInfo:nil];
	}

	[encoder setRenderPipelineState:pipeline_state];
	[encoder setViewport:(MTLViewport){0.0, 0.0, (double)target_width, (double)target_height, 0.0, 1.0}];
	[encoder setScissorRect:(MTLScissorRect){
		.x = (NSUInteger)dst_x,
		.y = (NSUInteger)dst_y,
		.width = (NSUInteger)dst_width,
		.height = (NSUInteger)dst_height
	}];
	[encoder setVertexBytes:vertices length:sizeof(vertices) atIndex:0];
	[encoder setFragmentBytes:&uniforms length:sizeof(uniforms) atIndex:0];
	[encoder setFragmentTexture:source atIndex:0];
	[encoder setFragmentSamplerState:sampler_state atIndex:0];
	[encoder drawPrimitives:MTLPrimitiveTypeTriangleStrip vertexStart:0 vertexCount:4];
	[encoder endEncoding];
}
