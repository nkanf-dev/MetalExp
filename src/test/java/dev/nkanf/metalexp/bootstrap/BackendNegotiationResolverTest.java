package dev.nkanf.metalexp.bootstrap;

import dev.nkanf.metalexp.config.BackendKind;
import dev.nkanf.metalexp.config.FailureMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackendNegotiationResolverTest {

	@Test
	void strictModeWithMetalOnlyAttemptsMetal() {
		BackendPlan plan = new BackendPlan(
			List.of(BackendKind.METAL, BackendKind.VULKAN, BackendKind.OPENGL),
			FailureMode.STRICT
		);

		assertEquals(List.of(BackendKind.METAL), BackendNegotiationResolver.resolveBackendTryOrder(plan));
	}

	@Test
	void fallbackModeKeepsMetalFirstThenRemainingOrder() {
		BackendPlan plan = new BackendPlan(
			List.of(BackendKind.METAL, BackendKind.VULKAN, BackendKind.OPENGL),
			FailureMode.FALLBACK
		);

		List<BackendKind> resolved = BackendNegotiationResolver.resolveBackendTryOrder(plan);

		assertEquals(List.of(BackendKind.METAL, BackendKind.VULKAN, BackendKind.OPENGL), resolved);
	}

	@Test
	void nullPlanFallsBackToDefaultOrder() {
		assertEquals(
			List.of(BackendKind.VULKAN, BackendKind.OPENGL),
			BackendNegotiationResolver.resolveBackendTryOrder(null)
		);
	}

	@Test
	void duplicateCandidatesCollapseToSingleEntries() {
		BackendPlan plan = new BackendPlan(
			List.of(BackendKind.METAL, BackendKind.METAL, BackendKind.VULKAN, BackendKind.VULKAN, BackendKind.OPENGL),
			FailureMode.FALLBACK
		);

		assertEquals(
			List.of(BackendKind.METAL, BackendKind.VULKAN, BackendKind.OPENGL),
			BackendNegotiationResolver.resolveBackendTryOrder(plan)
		);
	}
}
