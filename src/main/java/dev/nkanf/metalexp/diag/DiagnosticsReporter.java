package dev.nkanf.metalexp.diag;

import dev.nkanf.metalexp.MetalExpMod;

public final class DiagnosticsReporter {
	private DiagnosticsReporter() {
	}

	public static void logStartup(DiagnosticsSnapshot snapshot) {
		if (snapshot == null || snapshot.config() == null || snapshot.backendPlan() == null) {
			return;
		}

		MetalExpMod.LOGGER.info(
			"MetalExp startup: backend={}, failureMode={}, diagnosticsEnabled={}, plan={}, os={} {}",
			snapshot.config().backend(),
			snapshot.config().failureMode(),
			snapshot.config().diagnosticsEnabled(),
			snapshot.backendPlan().backendsToTry(),
			snapshot.osName(),
			snapshot.osArch()
		);
	}
}
