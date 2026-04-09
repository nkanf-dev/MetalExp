package dev.nkanf.metalexp.bootstrap;

import dev.nkanf.metalexp.config.BackendKind;
import dev.nkanf.metalexp.config.FailureMode;
import dev.nkanf.metalexp.config.MetalExpConfig;

import java.util.List;

public final class BackendPlanner {
	private BackendPlanner() {
	}

	public static BackendPlan plan(MetalExpConfig config, boolean isMacOs) {
		List<BackendKind> order = switch (config.backend()) {
			case METAL -> isMacOs
				? List.of(BackendKind.METAL, BackendKind.VULKAN, BackendKind.OPENGL)
				: List.of(BackendKind.VULKAN, BackendKind.OPENGL);
			case VULKAN -> List.of(BackendKind.VULKAN, BackendKind.OPENGL);
			case OPENGL -> List.of(BackendKind.OPENGL, BackendKind.VULKAN);
		};

		FailureMode failureMode = config.backend() == BackendKind.METAL && isMacOs
			? config.failureMode()
			: FailureMode.FALLBACK;

		return new BackendPlan(order, failureMode);
	}
}

