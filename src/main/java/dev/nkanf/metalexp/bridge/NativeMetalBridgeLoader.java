package dev.nkanf.metalexp.bridge;

final class NativeMetalBridgeLoader {
	static final String LIBRARY_NAME = "metalexp_native";
	private static final String LIBRARY_PATH_PROPERTY = "metalexp.nativeLibraryPath";
	private static volatile MetalBridgeLoadResult cachedResult;

	private NativeMetalBridgeLoader() {
	}

	static MetalBridgeLoadResult ensureLoaded() {
		MetalBridgeLoadResult cached = cachedResult;
		if (cached != null) {
			return cached;
		}

		synchronized (NativeMetalBridgeLoader.class) {
			if (cachedResult != null) {
				return cachedResult;
			}

			cachedResult = loadLibrary();
			return cachedResult;
		}
	}

	private static MetalBridgeLoadResult loadLibrary() {
		if (!isMacOs()) {
			return new MetalBridgeLoadResult(
				MetalBridgeLoadStatus.UNSUPPORTED_OS,
				"Metal bridge loading is only supported on macOS.",
				System.getProperty("os.name", "unknown")
			);
		}

		String explicitPath = System.getProperty(LIBRARY_PATH_PROPERTY, "").trim();
		if (!explicitPath.isEmpty()) {
			try {
				System.load(explicitPath);
				return new MetalBridgeLoadResult(MetalBridgeLoadStatus.LOADED, "Loaded Metal bridge from explicit path.", explicitPath);
			} catch (UnsatisfiedLinkError error) {
				return new MetalBridgeLoadResult(MetalBridgeLoadStatus.LOAD_FAILED, error.getMessage(), explicitPath);
			}
		}

		try {
			System.loadLibrary(LIBRARY_NAME);
			return new MetalBridgeLoadResult(MetalBridgeLoadStatus.LOADED, "Loaded Metal bridge from java.library.path.", LIBRARY_NAME);
		} catch (UnsatisfiedLinkError error) {
			String message = error.getMessage() == null ? "Metal bridge native library was not found." : error.getMessage();
			return new MetalBridgeLoadResult(MetalBridgeLoadStatus.NOT_FOUND, message, LIBRARY_NAME);
		}
	}

	private static boolean isMacOs() {
		return System.getProperty("os.name", "").toLowerCase().contains("mac");
	}

	static void resetForTests() {
		cachedResult = null;
	}
}
