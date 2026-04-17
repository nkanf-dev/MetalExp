package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.vertex.VertexFormat;

record MetalPassContext(
	String passLabel,
	String pipelineLocation,
	MetalPassAttachment colorAttachment,
	MetalPassAttachment depthAttachment,
	String drawType,
	VertexFormat vertexFormat,
	VertexFormat.IndexType indexType,
	boolean nativePath
) {
	String describe() {
		return "passLabel=" + this.passLabel
			+ ", pipeline=" + this.pipelineLocation
			+ ", colorAttachment={" + this.colorAttachment.describe() + "}"
			+ ", depthAttachment={" + (this.depthAttachment == null ? "NONE" : this.depthAttachment.describe()) + "}"
			+ ", drawType=" + this.drawType
			+ ", vertexFormat=" + this.vertexFormat
			+ ", indexType=" + this.indexType
			+ ", nativePath=" + this.nativePath;
	}
}
