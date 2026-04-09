package dev.nkanf.metalexp.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class MetalExpConfigStore {
	private static final String KEY_BACKEND = "backend";
	private static final String KEY_FAILURE_MODE = "failureMode";
	private static final String KEY_DIAGNOSTICS_ENABLED = "diagnosticsEnabled";

	private final Path path;

	public MetalExpConfigStore(Path path) {
		this.path = path;
	}

	public MetalExpConfig load() {
		if (this.path == null || Files.notExists(this.path)) {
			return MetalExpConfig.defaults();
		}

		Properties properties = new Properties();
		try (InputStream inputStream = Files.newInputStream(this.path)) {
			properties.load(inputStream);
		} catch (IOException e) {
			return MetalExpConfig.defaults();
		}

		String backendRaw = properties.getProperty(KEY_BACKEND);
		String failureModeRaw = properties.getProperty(KEY_FAILURE_MODE);
		String diagnosticsRaw = properties.getProperty(KEY_DIAGNOSTICS_ENABLED);

		MetalExpConfig defaults = MetalExpConfig.defaults();
		BackendKind backend = backendRaw == null ? defaults.backend() : BackendKind.fromId(backendRaw);
		FailureMode failureMode = failureModeRaw == null ? defaults.failureMode() : FailureMode.fromId(failureModeRaw);
		boolean diagnosticsEnabled = parseDiagnosticsEnabled(diagnosticsRaw, defaults.diagnosticsEnabled());

		return new MetalExpConfig(backend, failureMode, diagnosticsEnabled);
	}

	public void save(MetalExpConfig config) throws IOException {
		if (this.path == null || config == null) {
			return;
		}

		Path parent = this.path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		Properties properties = new Properties();
		properties.setProperty(KEY_BACKEND, config.backend().id());
		properties.setProperty(KEY_FAILURE_MODE, config.failureMode().id());
		properties.setProperty(KEY_DIAGNOSTICS_ENABLED, Boolean.toString(config.diagnosticsEnabled()));

		try (OutputStream outputStream = Files.newOutputStream(this.path)) {
			properties.store(outputStream, "MetalExp configuration");
		}
	}

	private static boolean parseDiagnosticsEnabled(String value, boolean defaultValue) {
		if (value == null) {
			return defaultValue;
		}
		if ("true".equalsIgnoreCase(value)) {
			return true;
		}
		if ("false".equalsIgnoreCase(value)) {
			return false;
		}
		return defaultValue;
	}
}
