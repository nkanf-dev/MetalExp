package dev.nkanf.metalexp.config;

public enum BackendKind {
	METAL("metal"),
	VULKAN("vulkan"),
	OPENGL("opengl");

	private final String id;

	BackendKind(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}

	public static BackendKind fromId(String value) {
		if (value == null) {
			return METAL;
		}

		for (BackendKind kind : values()) {
			if (kind.id.equalsIgnoreCase(value)) {
				return kind;
			}
		}

		return METAL;
	}
}

