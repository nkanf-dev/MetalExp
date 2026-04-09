package dev.nkanf.metalexp.bootstrap;

import dev.nkanf.metalexp.config.BackendKind;
import dev.nkanf.metalexp.config.FailureMode;

import java.util.List;

public record BackendPlan(
	List<BackendKind> backendsToTry,
	FailureMode failureMode
) {
}

