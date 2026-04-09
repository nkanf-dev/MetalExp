package dev.nkanf.metalexp.bootstrap;

import dev.nkanf.metalexp.config.BackendKind;
import dev.nkanf.metalexp.config.FailureMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackendNegotiationResolverTest {

	@Test
	void strictModeWithMetalFailsFastUntilMetalBackendExists() {
		BackendPlan plan = new BackendPlan(
			List.of(BackendKind.METAL, BackendKind.VULKAN, BackendKind.OPENGL),
			FailureMode.STRICT
		);

		assertThrows(IllegalStateException.class, () -> BackendNegotiationResolver.resolveRunnableBackends(plan));
	}

	@Test
	void fallbackModeSkipsMetalAndKeepsRemainingOrder() {
		BackendPlan plan = new BackendPlan(
			List.of(BackendKind.METAL, BackendKind.VULKAN, BackendKind.OPENGL),
			FailureMode.FALLBACK
		);

		List<BackendKind> resolved = BackendNegotiationResolver.resolveRunnableBackends(plan);

		assertEquals(List.of(BackendKind.VULKAN, BackendKind.OPENGL), resolved);
	}

	@Test
	void nullPlanFallsBackToDefaultOrder() {
		assertEquals(
			List.of(BackendKind.VULKAN, BackendKind.OPENGL),
			BackendNegotiationResolver.resolveRunnableBackends(null)
		);
	}
}
