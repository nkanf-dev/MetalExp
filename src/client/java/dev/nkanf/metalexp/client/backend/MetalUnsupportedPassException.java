package dev.nkanf.metalexp.client.backend;

final class MetalUnsupportedPassException extends UnsupportedOperationException {
	MetalUnsupportedPassException(String reason, MetalPassContext context) {
		super(reason + " [" + context.describe() + "]");
	}
}
