package dev.nkanf.metalexp.bootstrap;

import dev.nkanf.metalexp.config.MetalExpConfig;

public record BootstrapState(
	MetalExpConfig config,
	BackendPlan backendPlan
) {
}

