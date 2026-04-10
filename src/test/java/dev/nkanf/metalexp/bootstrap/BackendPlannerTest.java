package dev.nkanf.metalexp.bootstrap;

import dev.nkanf.metalexp.config.BackendKind;
import dev.nkanf.metalexp.config.FailureMode;
import dev.nkanf.metalexp.config.MetalExpConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BackendPlannerTest {

	@Test
	void metalFallbackIncludesMetalThenVulkanThenOpenGl() {
		MetalExpConfig config = new MetalExpConfig(BackendKind.METAL, FailureMode.FALLBACK, true);

		BackendPlan plan = BackendPlanner.plan(config, true);

		assertNotNull(plan, "Planner.plan should produce a backend plan for the provided configuration");
		assertEquals(List.of(BackendKind.METAL, BackendKind.VULKAN, BackendKind.OPENGL), plan.backendsToTry(),
				"Backends should be tried in the planned order");
		assertEquals(FailureMode.FALLBACK, plan.failureMode(), "The failure mode should follow the configuration");
	}

	@Test
	void metalStrictPreservesStrictModeOnMacOs() {
		MetalExpConfig config = new MetalExpConfig(BackendKind.METAL, FailureMode.STRICT, true);

		BackendPlan plan = BackendPlanner.plan(config, true);

		assertEquals(List.of(BackendKind.METAL, BackendKind.VULKAN, BackendKind.OPENGL), plan.backendsToTry());
		assertEquals(FailureMode.STRICT, plan.failureMode());
	}

	@Test
	void metalOnNonMacOsFallsBackToOriginalBackends() {
		MetalExpConfig config = MetalExpConfig.defaults();

		BackendPlan plan = BackendPlanner.plan(config, false);

		assertEquals(List.of(BackendKind.VULKAN, BackendKind.OPENGL), plan.backendsToTry());
		assertEquals(FailureMode.FALLBACK, plan.failureMode());
	}

	@Test
	void defaultsUseStrictMetalOnMacOs() {
		BackendPlan plan = BackendPlanner.plan(MetalExpConfig.defaults(), true);

		assertEquals(List.of(BackendKind.METAL, BackendKind.VULKAN, BackendKind.OPENGL), plan.backendsToTry());
		assertEquals(FailureMode.STRICT, plan.failureMode());
	}
}
