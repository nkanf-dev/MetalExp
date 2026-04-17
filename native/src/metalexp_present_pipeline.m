#include "metalexp_common.h"

static NSString *metalexp_present_shader_source(void) {
	return @"#include <metal_stdlib>\n"
		"using namespace metal;\n"
		"struct PresentRasterOut {\n"
		"    float4 position [[position]];\n"
		"    float2 uv;\n"
		"};\n"
		"vertex PresentRasterOut metalexp_present_vertex(uint vertexId [[vertex_id]]) {\n"
		"    PresentRasterOut out;\n"
		"    float2 uv = float2((vertexId << 1) & 2, vertexId & 2);\n"
		"    out.position = float4(uv * 2.0 - 1.0, 0.0, 1.0);\n"
		"    out.uv = float2(uv.x, 1.0 - uv.y);\n"
		"    return out;\n"
		"}\n"
		"fragment float4 metalexp_present_fragment(PresentRasterOut in [[stage_in]], texture2d<float> sourceTexture [[texture(0)]], sampler sourceSampler [[sampler(0)]]) {\n"
		"    float4 color = sourceTexture.sample(sourceSampler, in.uv);\n"
		"    return float4(color.rgb, 1.0);\n"
		"}\n";
}

static id<MTLLibrary> metalexp_present_library(id<MTLDevice> device) {
	static id<MTLDevice> cached_device = nil;
	static id<MTLLibrary> cached_library = nil;

	if (cached_library != nil && cached_device == device) {
		return cached_library;
	}

	NSError *error = nil;
	id<MTLLibrary> library = [device newLibraryWithSource:metalexp_present_shader_source() options:nil error:&error];
	if (library == nil) {
		@throw [NSException exceptionWithName:@"MetalExpPresentPipelineException"
			reason:error.localizedDescription == nil ? @"Metal present shader library compilation failed." : error.localizedDescription
			userInfo:nil];
	}

	cached_device = device;
	cached_library = library;
	return cached_library;
}

static id<MTLRenderPipelineState> metalexp_present_pipeline_state(id<MTLDevice> device, MTLPixelFormat pixel_format) {
	static id<MTLDevice> cached_device = nil;
	static MTLPixelFormat cached_pixel_format = MTLPixelFormatInvalid;
	static id<MTLRenderPipelineState> cached_pipeline = nil;

	if (cached_pipeline != nil && cached_device == device && cached_pixel_format == pixel_format) {
		return cached_pipeline;
	}

	id<MTLLibrary> library = metalexp_present_library(device);
	MTLRenderPipelineDescriptor *descriptor = [[MTLRenderPipelineDescriptor alloc] init];
	descriptor.vertexFunction = [library newFunctionWithName:@"metalexp_present_vertex"];
	descriptor.fragmentFunction = [library newFunctionWithName:@"metalexp_present_fragment"];
	descriptor.colorAttachments[0].pixelFormat = pixel_format;
	descriptor.colorAttachments[0].blendingEnabled = NO;

	NSError *error = nil;
	id<MTLRenderPipelineState> pipeline = [device newRenderPipelineStateWithDescriptor:descriptor error:&error];
	if (pipeline == nil) {
		@throw [NSException exceptionWithName:@"MetalExpPresentPipelineException"
			reason:error.localizedDescription == nil ? @"Metal present pipeline creation failed." : error.localizedDescription
			userInfo:nil];
	}

	cached_device = device;
	cached_pixel_format = pixel_format;
	cached_pipeline = pipeline;
	return cached_pipeline;
}

void metalexp_present_texture_to_drawable(id<MTLCommandBuffer> command_buffer, id<MTLTexture> source_texture, id<MTLTexture> drawable_texture) {
	if (command_buffer == nil || source_texture == nil || drawable_texture == nil) {
		@throw [NSException exceptionWithName:@"MetalExpPresentPipelineException"
			reason:@"Metal present pass requires a command buffer plus source and drawable textures."
			userInfo:nil];
	}

	id<MTLDevice> device = drawable_texture.device;
	if (device == nil || source_texture.device != device) {
		@throw [NSException exceptionWithName:@"MetalExpPresentPipelineException"
			reason:@"Metal present pass requires source and drawable textures on the same MTLDevice."
			userInfo:nil];
	}

	MTLRenderPassDescriptor *pass_descriptor = [MTLRenderPassDescriptor renderPassDescriptor];
	pass_descriptor.colorAttachments[0].texture = drawable_texture;
	pass_descriptor.colorAttachments[0].loadAction = MTLLoadActionDontCare;
	pass_descriptor.colorAttachments[0].storeAction = MTLStoreActionStore;

	id<MTLRenderCommandEncoder> encoder = [command_buffer renderCommandEncoderWithDescriptor:pass_descriptor];
	if (encoder == nil) {
		@throw [NSException exceptionWithName:@"MetalExpPresentPipelineException"
			reason:@"Metal present pass could not allocate an MTLRenderCommandEncoder."
			userInfo:nil];
	}

	MTLSamplerDescriptor *sampler_descriptor = [[MTLSamplerDescriptor alloc] init];
	sampler_descriptor.minFilter = MTLSamplerMinMagFilterLinear;
	sampler_descriptor.magFilter = MTLSamplerMinMagFilterLinear;
	sampler_descriptor.sAddressMode = MTLSamplerAddressModeClampToEdge;
	sampler_descriptor.tAddressMode = MTLSamplerAddressModeClampToEdge;
	id<MTLSamplerState> sampler_state = [device newSamplerStateWithDescriptor:sampler_descriptor];
	if (sampler_state == nil) {
		@throw [NSException exceptionWithName:@"MetalExpPresentPipelineException"
			reason:@"Metal present pass could not create a sampler state."
			userInfo:nil];
	}

	[encoder setRenderPipelineState:metalexp_present_pipeline_state(device, drawable_texture.pixelFormat)];
	[encoder setFragmentTexture:source_texture atIndex:0];
	[encoder setFragmentSamplerState:sampler_state atIndex:0];
	[encoder setViewport:(MTLViewport){0.0, 0.0, (double)drawable_texture.width, (double)drawable_texture.height, 0.0, 1.0}];
	[encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:3];
	[encoder endEncoding];
}
