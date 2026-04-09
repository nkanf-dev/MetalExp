package dev.nkanf.metalexp.config;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum GraphicsApiPreference implements StringRepresentable {
	METAL("metal"),
	VULKAN("vulkan"),
	OPENGL("opengl");

	public static final Codec<GraphicsApiPreference> CODEC = StringRepresentable.fromEnum(GraphicsApiPreference::values);

	private final String id;

	GraphicsApiPreference(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}

	@Override
	public String getSerializedName() {
		return this.id;
	}

	public BackendKind toBackendKind() {
		return switch (this) {
			case METAL -> BackendKind.METAL;
			case VULKAN -> BackendKind.VULKAN;
			case OPENGL -> BackendKind.OPENGL;
		};
	}

	public static GraphicsApiPreference fromBackendKind(BackendKind backendKind) {
		if (backendKind == null) {
			return METAL;
		}

		return switch (backendKind) {
			case METAL -> METAL;
			case VULKAN -> VULKAN;
			case OPENGL -> OPENGL;
		};
	}

	public static GraphicsApiPreference fromId(String value) {
		if (value == null) {
			return METAL;
		}

		for (GraphicsApiPreference candidate : values()) {
			if (candidate.id.equalsIgnoreCase(value)) {
				return candidate;
			}
		}

		return METAL;
	}
}
