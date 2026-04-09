package dev.nkanf.metalexp.bootstrap;

import dev.nkanf.metalexp.config.BackendKind;
import dev.nkanf.metalexp.config.FailureMode;

import java.util.ArrayList;
import java.util.List;

public final class BackendNegotiationResolver {
	private static final String STRICT_UNAVAILABLE_MESSAGE = "MetalExp strict mode selected Metal, but native Metal backend is not implemented yet.";

	private BackendNegotiationResolver() {
	}

	public static List<BackendKind> resolveRunnableBackends(BackendPlan plan) {
		if (plan == null || plan.backendsToTry() == null || plan.backendsToTry().isEmpty()) {
			return List.of(BackendKind.VULKAN, BackendKind.OPENGL);
		}

		if (plan.failureMode() == FailureMode.STRICT && plan.backendsToTry().contains(BackendKind.METAL)) {
			throw new IllegalStateException(STRICT_UNAVAILABLE_MESSAGE);
		}

		ArrayList<BackendKind> runnable = new ArrayList<>();
		for (BackendKind candidate : plan.backendsToTry()) {
			if (candidate == BackendKind.METAL) {
				continue;
			}

			if (!runnable.contains(candidate)) {
				runnable.add(candidate);
			}
		}

		if (runnable.isEmpty()) {
			return List.of(BackendKind.VULKAN, BackendKind.OPENGL);
		}

		return List.copyOf(runnable);
	}
}
