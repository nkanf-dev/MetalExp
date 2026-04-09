package dev.nkanf.metalexp.client.settings;

import dev.nkanf.metalexp.config.BackendKind;

public final class MetalExpGraphicsApiSelectionState {
	private static BackendKind openedWith;
	private static BackendKind currentSelection;

	private MetalExpGraphicsApiSelectionState() {
	}

	public static void onVideoSettingsOpened(BackendKind initialSelection) {
		openedWith = initialSelection;
		currentSelection = initialSelection;
	}

	public static void onSelectionApplied(BackendKind selected) {
		currentSelection = selected;
	}

	public static boolean hasPendingRestart() {
		return openedWith != null && currentSelection != null && openedWith != currentSelection;
	}
}
