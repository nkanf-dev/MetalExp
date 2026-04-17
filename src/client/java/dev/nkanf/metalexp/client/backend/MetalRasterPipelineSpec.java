package dev.nkanf.metalexp.client.backend;

record MetalRasterPipelineSpec(
	MetalRasterPipelineSpecialCase specialCase,
	MetalRasterPipelineSpec.ShaderFamily shaderFamily,
	MetalRasterPipelineSpec.VertexLayoutFamily vertexLayoutFamily,
	MetalRasterPipelineSpec.PrimitiveTopology primitiveTopology,
	boolean indexed,
	boolean sampler0Required,
	boolean sampler2Required,
	boolean wantsDepthTexture,
	boolean cull,
	MetalRasterPipelineSpec.CompareFunction depthCompare,
	boolean depthWrite,
	float depthBiasScaleFactor,
	float depthBiasConstant,
	int colorWriteMask,
	MetalRasterPipelineSpec.BlendState blendState
) {
	boolean usesSharedRasterPath() {
		return this.specialCase == MetalRasterPipelineSpecialCase.NONE;
	}

	enum ShaderFamily {
		GUI_COLOR,
		GUI_TEXTURED,
		PANORAMA,
		SKY,
		STARS,
		POSITION_COLOR,
		POSITION_TEX,
		LINE,
		UNKNOWN
	}

	enum VertexLayoutFamily {
		POSITION,
		POSITION_COLOR,
		POSITION_TEX,
		POSITION_TEX_COLOR,
		POSITION_COLOR_NORMAL_LINE_WIDTH,
		BLOCK,
		UNKNOWN
	}

	enum PrimitiveTopology {
		TRIANGLE,
		TRIANGLE_STRIP,
		LINE,
		LINE_STRIP,
		POINT
	}

	enum CompareFunction {
		ALWAYS_PASS,
		LESS_THAN,
		LESS_THAN_OR_EQUAL,
		EQUAL,
		NOT_EQUAL,
		GREATER_THAN_OR_EQUAL,
		GREATER_THAN,
		NEVER_PASS
	}

	enum BlendFactor {
		CONSTANT_ALPHA,
		CONSTANT_COLOR,
		DST_ALPHA,
		DST_COLOR,
		ONE,
		ONE_MINUS_CONSTANT_ALPHA,
		ONE_MINUS_CONSTANT_COLOR,
		ONE_MINUS_DST_ALPHA,
		ONE_MINUS_DST_COLOR,
		ONE_MINUS_SRC_ALPHA,
		ONE_MINUS_SRC_COLOR,
		SRC_ALPHA,
		SRC_ALPHA_SATURATE,
		SRC_COLOR,
		ZERO
	}

	enum BlendOperation {
		ADD,
		SUBTRACT,
		REVERSE_SUBTRACT,
		MIN,
		MAX
	}

	record BlendState(
		boolean enabled,
		BlendOperation colorOperation,
		BlendOperation alphaOperation,
		BlendFactor sourceColorFactor,
		BlendFactor destinationColorFactor,
		BlendFactor sourceAlphaFactor,
		BlendFactor destinationAlphaFactor
	) {
		static BlendState disabled() {
			return new BlendState(false, BlendOperation.ADD, BlendOperation.ADD, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE, BlendFactor.ZERO);
		}
	}
}
