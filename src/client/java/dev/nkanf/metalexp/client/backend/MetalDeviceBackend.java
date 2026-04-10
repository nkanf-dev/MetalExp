package dev.nkanf.metalexp.client.backend;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.DeviceLimits;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.GpuSurfaceBackend;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.nkanf.metalexp.bridge.MetalBridge;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Supplier;

final class MetalDeviceBackend implements GpuDeviceBackend {
	private static final DeviceLimits DEFAULT_LIMITS = new DeviceLimits(16, 256, 16384);

	private final MetalSurfaceLease surfaceLease;
	private final DeviceInfo deviceInfo;
	private boolean closed;

	MetalDeviceBackend(MetalBridge metalBridge, MetalSurfaceDescriptor surfaceDescriptor) {
		this(new MetalSurfaceLease(metalBridge, surfaceDescriptor));
	}

	MetalDeviceBackend(MetalSurfaceLease surfaceLease) {
		this.surfaceLease = Objects.requireNonNull(surfaceLease, "surfaceLease");
		this.deviceInfo = new DeviceInfo(
			"Metal Bootstrap Device",
			"Apple",
			"metalexp-bootstrap",
			true,
			"Metal",
			1.0F,
			DEFAULT_LIMITS,
			Set.of()
		);
	}

	MetalSurfaceDescriptor surfaceDescriptor() {
		return this.surfaceLease.descriptor();
	}

	MetalSurfaceLease surfaceLease() {
		return this.surfaceLease;
	}

	@Override
	public GpuSurfaceBackend createSurface(long window) {
		if (this.closed) {
			throw new IllegalStateException("Metal device backend is closed.");
		}

		return new MetalSurfaceBackend(this.surfaceLease);
	}

	@Override
	public CommandEncoderBackend createCommandEncoder() {
		return new MetalCommandEncoderBackend();
	}

	@Override
	public GpuSampler createSampler(AddressMode addressMode, AddressMode addressMode1, FilterMode filterMode, FilterMode filterMode1, int i, OptionalDouble optionalDouble) {
		return new MetalSampler(addressMode, addressMode1, filterMode, filterMode1, i, optionalDouble);
	}

	@Override
	public GpuTexture createTexture(Supplier<String> supplier, int i, GpuFormat gpuFormat, int i1, int i2, int i3, int i4) {
		return new MetalTexture(this.surfaceLease.metalBridge(), i, supplier.get(), gpuFormat, i1, i2, i3, i4);
	}

	@Override
	public GpuTexture createTexture(String s, int i, GpuFormat gpuFormat, int i1, int i2, int i3, int i4) {
		return new MetalTexture(this.surfaceLease.metalBridge(), i, s, gpuFormat, i1, i2, i3, i4);
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture gpuTexture) {
		return new MetalTextureView(gpuTexture, 0, gpuTexture.getMipLevels());
	}

	@Override
	public GpuTextureView createTextureView(GpuTexture gpuTexture, int i, int i1) {
		return new MetalTextureView(gpuTexture, i, i1);
	}

	@Override
	public GpuBuffer createBuffer(Supplier<String> supplier, int i, long l) {
		return new MetalBuffer(i, l);
	}

	@Override
	public GpuBuffer createBuffer(Supplier<String> supplier, int i, ByteBuffer byteBuffer) {
		MetalBuffer buffer = new MetalBuffer(i, byteBuffer.remaining());
		buffer.write(0L, byteBuffer.duplicate());
		return buffer;
	}

	@Override
	public List<String> getLastDebugMessages() {
		return List.of("MetalDeviceBackend bootstrap device path active.");
	}

	@Override
	public boolean isDebuggingEnabled() {
		return false;
	}

	@Override
	public CompiledRenderPipeline precompilePipeline(RenderPipeline renderPipeline, ShaderSource shaderSource) {
		return () -> true;
	}

	@Override
	public void clearPipelineCache() {
	}

	@Override
	public void close() {
		this.surfaceLease.close();
		this.closed = true;
	}

	@Override
	public GpuQueryPool createTimestampQueryPool(int i) {
		return new MetalQueryPool(i);
	}

	@Override
	public long getTimestampNow() {
		return 0L;
	}

	@Override
	public DeviceInfo getDeviceInfo() {
		return this.deviceInfo;
	}

	boolean isClosed() {
		return this.closed;
	}

	private static UnsupportedOperationException unsupported(String feature) {
		return new UnsupportedOperationException("Metal bootstrap backend does not implement " + feature + " yet.");
	}
}
