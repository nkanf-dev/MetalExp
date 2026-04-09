package dev.nkanf.metalexp.diag;

import dev.nkanf.metalexp.bootstrap.BackendPlan;
import dev.nkanf.metalexp.config.MetalExpConfig;

public record DiagnosticsSnapshot(
	MetalExpConfig config,
	BackendPlan backendPlan,
	String osName,
	String osArch
) {
}

