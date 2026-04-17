package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.pipeline.BlendEquation;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.BlendOp;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import java.util.Optional;
import java.util.Set;

final class MetalRasterPipelineSpecs {
	private static final Set<String> WORLD_TERRAIN_LOCATIONS = Set.of(
		"minecraft:pipeline/solid_terrain",
		"minecraft:pipeline/cutout_terrain",
		"minecraft:pipeline/translucent_terrain"
	);
	private static final Set<String> WORLD_OPAQUE_LOCATIONS = Set.of(
		"minecraft:pipeline/entity_solid",
		"minecraft:pipeline/solid_terrain",
		"minecraft:pipeline/cutout_terrain",
		"minecraft:pipeline/translucent_terrain"
	);

	private MetalRasterPipelineSpecs() {
	}

	static MetalRasterPipelineSpec describe(RenderPipeline pipeline, boolean indexed) {
		MetalRasterPipelineSpecialCase specialCase = resolveSpecialCase(pipeline);
		ColorTargetState colorTargetState = pipeline.getColorTargetState() == null ? ColorTargetState.DEFAULT : pipeline.getColorTargetState();
		DepthStencilState depthStencilState = pipeline.getDepthStencilState() == null ? DepthStencilState.DEFAULT : pipeline.getDepthStencilState();
		VertexFormat vertexFormat = pipeline.getVertexFormat();
		MetalRasterPipelineSpec.VertexLayoutFamily vertexLayoutFamily = resolveVertexLayoutFamily(vertexFormat);
		MetalRasterPipelineSpec.ShaderFamily shaderFamily = resolveShaderFamily(pipeline, specialCase, vertexLayoutFamily);
		if (specialCase == MetalRasterPipelineSpecialCase.NONE
			&& (shaderFamily == MetalRasterPipelineSpec.ShaderFamily.UNKNOWN || vertexLayoutFamily == MetalRasterPipelineSpec.VertexLayoutFamily.UNKNOWN)) {
			specialCase = MetalRasterPipelineSpecialCase.NONCRITICAL_SKIP;
		}
		return new MetalRasterPipelineSpec(
			specialCase,
			shaderFamily,
			vertexLayoutFamily,
			resolvePrimitiveTopology(pipeline.getVertexFormatMode()),
			indexed,
			pipeline.getSamplers().contains("Sampler0"),
			pipeline.getSamplers().contains("Sampler2"),
			resolveWantsDepthTexture(pipeline, specialCase, shaderFamily),
			resolveCull(pipeline),
			mapCompare(depthStencilState.depthTest()),
			resolveDepthWrite(pipeline, specialCase, shaderFamily, depthStencilState),
			depthStencilState.depthBiasScaleFactor(),
			depthStencilState.depthBiasConstant(),
			colorTargetState.writeMask(),
			mapBlend(colorTargetState.blendFunction())
		);
	}

	private static MetalRasterPipelineSpecialCase resolveSpecialCase(RenderPipeline pipeline) {
		String location = pipeline.getLocation().toString();
		if ("minecraft:pipeline/clouds".equals(location)) {
			return MetalRasterPipelineSpecialCase.CLOUDS_SKIP;
		}
		if (location.startsWith("minecraft:blur/")) {
			return MetalRasterPipelineSpecialCase.BLUR;
		}
		if ("minecraft:pipeline/lightmap".equals(location)) {
			return MetalRasterPipelineSpecialCase.LIGHTMAP;
		}
		if ("minecraft:pipeline/animate_sprite_blit".equals(location) || "minecraft:pipeline/animate_sprite_interpolate".equals(location)) {
			return MetalRasterPipelineSpecialCase.ANIMATE_SPRITE;
		}
		if ("minecraft:pipeline/entity_outline_blit".equals(location)) {
			return MetalRasterPipelineSpecialCase.POST_BLIT;
		}
		if (WORLD_TERRAIN_LOCATIONS.contains(location)) {
			return MetalRasterPipelineSpecialCase.WORLD_TERRAIN;
		}
		if (WORLD_OPAQUE_LOCATIONS.contains(location)) {
			return MetalRasterPipelineSpecialCase.WORLD_OPAQUE_UNSUPPORTED;
		}
		if (pipeline.getSamplers().contains("Sampler2")) {
			return MetalRasterPipelineSpecialCase.NONCRITICAL_SKIP;
		}
		return MetalRasterPipelineSpecialCase.NONE;
	}

	private static MetalRasterPipelineSpec.ShaderFamily resolveShaderFamily(
		RenderPipeline pipeline,
		MetalRasterPipelineSpecialCase specialCase,
		MetalRasterPipelineSpec.VertexLayoutFamily layoutFamily
	) {
		if (specialCase != MetalRasterPipelineSpecialCase.NONE) {
			return MetalRasterPipelineSpec.ShaderFamily.UNKNOWN;
		}

		if ("minecraft:pipeline/panorama".equals(pipeline.getLocation().toString())) {
			return MetalRasterPipelineSpec.ShaderFamily.PANORAMA;
		}

		String vertexShader = identifierString(pipeline.getVertexShader());
		String fragmentShader = identifierString(pipeline.getFragmentShader());

		if (vertexShader.endsWith("core/sky") && fragmentShader.endsWith("core/sky")) {
			return MetalRasterPipelineSpec.ShaderFamily.SKY;
		}
		if (vertexShader.endsWith("core/stars") && fragmentShader.endsWith("core/stars")) {
			return MetalRasterPipelineSpec.ShaderFamily.STARS;
		}
		if (vertexShader.endsWith("core/position_color") && fragmentShader.endsWith("core/position_color")) {
			return MetalRasterPipelineSpec.ShaderFamily.POSITION_COLOR;
		}
		if (vertexShader.endsWith("core/position_tex") && fragmentShader.endsWith("core/position_tex")) {
			return MetalRasterPipelineSpec.ShaderFamily.POSITION_TEX;
		}
		if (vertexShader.endsWith("core/rendertype_lines") && fragmentShader.endsWith("core/rendertype_lines")) {
			return MetalRasterPipelineSpec.ShaderFamily.LINE;
		}
		if (layoutFamily == MetalRasterPipelineSpec.VertexLayoutFamily.POSITION_TEX_COLOR) {
			return MetalRasterPipelineSpec.ShaderFamily.GUI_TEXTURED;
		}
		if (layoutFamily == MetalRasterPipelineSpec.VertexLayoutFamily.POSITION_COLOR) {
			return MetalRasterPipelineSpec.ShaderFamily.GUI_COLOR;
		}
		return MetalRasterPipelineSpec.ShaderFamily.UNKNOWN;
	}

	private static MetalRasterPipelineSpec.VertexLayoutFamily resolveVertexLayoutFamily(VertexFormat vertexFormat) {
		boolean hasColor = vertexFormat.contains(VertexFormatElement.COLOR);
		boolean hasUv0 = vertexFormat.contains(VertexFormatElement.UV0);
		boolean hasUv2 = vertexFormat.contains(VertexFormatElement.UV2);
		boolean hasNormal = vertexFormat.contains(VertexFormatElement.NORMAL);
		boolean hasLineWidth = vertexFormat.contains(VertexFormatElement.LINE_WIDTH);

		if (hasColor && hasUv0 && hasUv2) {
			return MetalRasterPipelineSpec.VertexLayoutFamily.BLOCK;
		}
		if (hasColor && hasNormal && hasLineWidth) {
			return MetalRasterPipelineSpec.VertexLayoutFamily.POSITION_COLOR_NORMAL_LINE_WIDTH;
		}
		if (hasColor && hasUv0) {
			return MetalRasterPipelineSpec.VertexLayoutFamily.POSITION_TEX_COLOR;
		}
		if (hasUv0) {
			return MetalRasterPipelineSpec.VertexLayoutFamily.POSITION_TEX;
		}
		if (hasColor) {
			return MetalRasterPipelineSpec.VertexLayoutFamily.POSITION_COLOR;
		}
		if (vertexFormat.contains(VertexFormatElement.POSITION)) {
			return MetalRasterPipelineSpec.VertexLayoutFamily.POSITION;
		}
		return MetalRasterPipelineSpec.VertexLayoutFamily.UNKNOWN;
	}

	private static MetalRasterPipelineSpec.PrimitiveTopology resolvePrimitiveTopology(VertexFormat.Mode mode) {
		if (mode == VertexFormat.Mode.TRIANGLE_STRIP) {
			return MetalRasterPipelineSpec.PrimitiveTopology.TRIANGLE_STRIP;
		}
		if (mode == VertexFormat.Mode.LINES || mode == VertexFormat.Mode.DEBUG_LINES) {
			return MetalRasterPipelineSpec.PrimitiveTopology.LINE;
		}
		if (mode == VertexFormat.Mode.DEBUG_LINE_STRIP) {
			return MetalRasterPipelineSpec.PrimitiveTopology.LINE_STRIP;
		}
		if (mode == VertexFormat.Mode.POINTS) {
			return MetalRasterPipelineSpec.PrimitiveTopology.POINT;
		}
		return MetalRasterPipelineSpec.PrimitiveTopology.TRIANGLE;
	}

	private static boolean resolveCull(RenderPipeline pipeline) {
		String location = pipeline.getLocation().toString();
		if ("minecraft:pipeline/sky".equals(location)
			|| "minecraft:pipeline/stars".equals(location)
			|| "minecraft:pipeline/sunrise_sunset".equals(location)
			|| "minecraft:pipeline/celestial".equals(location)) {
			return false;
		}
		return pipeline.isCull();
	}

	private static boolean resolveWantsDepthTexture(
		RenderPipeline pipeline,
		MetalRasterPipelineSpecialCase specialCase,
		MetalRasterPipelineSpec.ShaderFamily shaderFamily
	) {
		if (specialCase != MetalRasterPipelineSpecialCase.NONE) {
			return pipeline.wantsDepthTexture();
		}

		return switch (shaderFamily) {
			case GUI_COLOR, GUI_TEXTURED, PANORAMA, SKY, STARS, POSITION_COLOR, POSITION_TEX -> false;
			case LINE, UNKNOWN -> pipeline.wantsDepthTexture();
		};
	}

	private static boolean resolveDepthWrite(
		RenderPipeline pipeline,
		MetalRasterPipelineSpecialCase specialCase,
		MetalRasterPipelineSpec.ShaderFamily shaderFamily,
		DepthStencilState depthStencilState
	) {
		if (specialCase != MetalRasterPipelineSpecialCase.NONE) {
			return depthStencilState.writeDepth();
		}

		return switch (shaderFamily) {
			case GUI_COLOR, GUI_TEXTURED, PANORAMA, SKY, STARS, POSITION_COLOR, POSITION_TEX -> false;
			case LINE, UNKNOWN -> depthStencilState.writeDepth();
		};
	}

	private static MetalRasterPipelineSpec.CompareFunction mapCompare(CompareOp compareOp) {
		return switch (compareOp) {
			case ALWAYS_PASS -> MetalRasterPipelineSpec.CompareFunction.ALWAYS_PASS;
			case LESS_THAN -> MetalRasterPipelineSpec.CompareFunction.LESS_THAN;
			case LESS_THAN_OR_EQUAL -> MetalRasterPipelineSpec.CompareFunction.LESS_THAN_OR_EQUAL;
			case EQUAL -> MetalRasterPipelineSpec.CompareFunction.EQUAL;
			case NOT_EQUAL -> MetalRasterPipelineSpec.CompareFunction.NOT_EQUAL;
			case GREATER_THAN_OR_EQUAL -> MetalRasterPipelineSpec.CompareFunction.GREATER_THAN_OR_EQUAL;
			case GREATER_THAN -> MetalRasterPipelineSpec.CompareFunction.GREATER_THAN;
			case NEVER_PASS -> MetalRasterPipelineSpec.CompareFunction.NEVER_PASS;
		};
	}

	private static MetalRasterPipelineSpec.BlendState mapBlend(Optional<BlendFunction> blendFunction) {
		if (blendFunction.isEmpty()) {
			return MetalRasterPipelineSpec.BlendState.disabled();
		}

		BlendEquation color = blendFunction.get().color();
		BlendEquation alpha = blendFunction.get().alpha();
		return new MetalRasterPipelineSpec.BlendState(
			true,
			mapBlendOperation(color.op()),
			mapBlendOperation(alpha.op()),
			mapBlendFactor(color.sourceFactor()),
			mapBlendFactor(color.destFactor()),
			mapBlendFactor(alpha.sourceFactor()),
			mapBlendFactor(alpha.destFactor())
		);
	}

	private static MetalRasterPipelineSpec.BlendFactor mapBlendFactor(BlendFactor blendFactor) {
		return switch (blendFactor) {
			case CONSTANT_ALPHA -> MetalRasterPipelineSpec.BlendFactor.CONSTANT_ALPHA;
			case CONSTANT_COLOR -> MetalRasterPipelineSpec.BlendFactor.CONSTANT_COLOR;
			case DST_ALPHA -> MetalRasterPipelineSpec.BlendFactor.DST_ALPHA;
			case DST_COLOR -> MetalRasterPipelineSpec.BlendFactor.DST_COLOR;
			case ONE -> MetalRasterPipelineSpec.BlendFactor.ONE;
			case ONE_MINUS_CONSTANT_ALPHA -> MetalRasterPipelineSpec.BlendFactor.ONE_MINUS_CONSTANT_ALPHA;
			case ONE_MINUS_CONSTANT_COLOR -> MetalRasterPipelineSpec.BlendFactor.ONE_MINUS_CONSTANT_COLOR;
			case ONE_MINUS_DST_ALPHA -> MetalRasterPipelineSpec.BlendFactor.ONE_MINUS_DST_ALPHA;
			case ONE_MINUS_DST_COLOR -> MetalRasterPipelineSpec.BlendFactor.ONE_MINUS_DST_COLOR;
			case ONE_MINUS_SRC_ALPHA -> MetalRasterPipelineSpec.BlendFactor.ONE_MINUS_SRC_ALPHA;
			case ONE_MINUS_SRC_COLOR -> MetalRasterPipelineSpec.BlendFactor.ONE_MINUS_SRC_COLOR;
			case SRC_ALPHA -> MetalRasterPipelineSpec.BlendFactor.SRC_ALPHA;
			case SRC_ALPHA_SATURATE -> MetalRasterPipelineSpec.BlendFactor.SRC_ALPHA_SATURATE;
			case SRC_COLOR -> MetalRasterPipelineSpec.BlendFactor.SRC_COLOR;
			case ZERO -> MetalRasterPipelineSpec.BlendFactor.ZERO;
		};
	}

	private static MetalRasterPipelineSpec.BlendOperation mapBlendOperation(BlendOp blendOp) {
		return switch (blendOp) {
			case ADD -> MetalRasterPipelineSpec.BlendOperation.ADD;
			case SUBTRACT -> MetalRasterPipelineSpec.BlendOperation.SUBTRACT;
			case REVERSE_SUBTRACT -> MetalRasterPipelineSpec.BlendOperation.REVERSE_SUBTRACT;
			case MIN -> MetalRasterPipelineSpec.BlendOperation.MIN;
			case MAX -> MetalRasterPipelineSpec.BlendOperation.MAX;
		};
	}

	private static String identifierString(Object identifier) {
		return identifier == null ? "" : identifier.toString();
	}
}
