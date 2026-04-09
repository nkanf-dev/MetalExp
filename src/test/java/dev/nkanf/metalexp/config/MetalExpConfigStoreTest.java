package dev.nkanf.metalexp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetalExpConfigStoreTest {

	@Test
	void loadWhenFileMissingReturnsDefaults(@TempDir Path tempDir) {
		MetalExpConfigStore store = new MetalExpConfigStore(tempDir.resolve("metalexp.properties"));

		MetalExpConfig loaded = store.load();

		assertEquals(MetalExpConfig.defaults(), loaded);
	}

	@Test
	void loadWhenFileContainsInvalidValuesFallsBackToDefaults(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("metalexp.properties");
		Files.writeString(path, """
			backend=invalid
			failureMode=invalid
			diagnosticsEnabled=invalid
			""");
		MetalExpConfigStore store = new MetalExpConfigStore(path);

		assertEquals(MetalExpConfig.defaults(), store.load());
	}

	@Test
	void saveThenLoadRoundTrips(@TempDir Path tempDir) throws IOException {
		Path path = tempDir.resolve("metalexp.properties");
		MetalExpConfigStore store = new MetalExpConfigStore(path);
		MetalExpConfig config = new MetalExpConfig(BackendKind.OPENGL, FailureMode.STRICT, false);

		store.save(config);
		MetalExpConfig loaded = store.load();

		assertEquals(config, loaded);
	}

	@Test
	void savePropagatesIOException(@TempDir Path tempDir) {
		// A directory cannot be opened as a file OutputStream; this should throw.
		MetalExpConfigStore store = new MetalExpConfigStore(tempDir);
		MetalExpConfig config = MetalExpConfig.defaults();

		assertThrows(IOException.class, () -> store.save(config));
	}
}
