# MetalExp Bootstrap and Selection Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first executable MetalExp milestone by adding a project-owned backend selection model, persisted configuration, bootstrap diagnostics, and a client startup skeleton that can later drive settings replacement and backend negotiation.

**Architecture:** This plan intentionally covers only the first subproject from the full MetalExp spec. It creates stable core types and bootstrap wiring in the existing Fabric mod scaffold while deferring the renderer backend, native bridge, and full settings-screen replacement to later plans. The implementation keeps Minecraft-specific hooks thin and pushes policy into plain Java types that can be tested without launching the game.

**Tech Stack:** Fabric Loader, Fabric API, Fabric Loom, Java 25, JUnit Jupiter

---

## Scope Check

The full spec spans multiple independent subsystems:

- bootstrap and configuration
- settings UI replacement
- backend negotiation mixins
- native Metal bridge
- shader toolchain
- Java backend implementation

This plan only covers the first subsystem: **bootstrap, config, backend selection, and diagnostics scaffolding**. Follow-up plans are required for:

- replacing the in-game graphics API option
- installing the real backend negotiation hook
- implementing the native bridge and Metal backend

## File Structure

### Existing Files to Modify

- Modify: `/Users/nkanf/projs/MetalExp/build.gradle`
- Modify: `/Users/nkanf/projs/MetalExp/README.md`
- Modify: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/MetalExpMod.java`
- Modify: `/Users/nkanf/projs/MetalExp/src/client/java/dev/nkanf/metalexp/client/MetalExpClient.java`
- Modify: `/Users/nkanf/projs/MetalExp/src/client/java/dev/nkanf/metalexp/client/mixin/ClientBootstrapMixin.java`

### New Main Sources

- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/config/BackendKind.java`
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/config/FailureMode.java`
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/config/MetalExpConfig.java`
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/config/MetalExpConfigStore.java`
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/bootstrap/BackendPlan.java`
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/bootstrap/BackendPlanner.java`
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/bootstrap/BootstrapState.java`
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/diag/DiagnosticsSnapshot.java`
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/diag/DiagnosticsReporter.java`

### New Test Sources

- Create: `/Users/nkanf/projs/MetalExp/src/test/java/dev/nkanf/metalexp/config/MetalExpConfigStoreTest.java`
- Create: `/Users/nkanf/projs/MetalExp/src/test/java/dev/nkanf/metalexp/bootstrap/BackendPlannerTest.java`

## Conventions

- Keep all bootstrap policy classes in `src/main/java` so they stay part of the normal Fabric mod module.
- Do not introduce the native bridge, renderer backend, or Minecraft option-screen hook in this plan.
- Do not hardcode a real Metal backend instance yet. `METAL` should exist as a selection value and planning result, but startup only logs the selected backend plan.

### Task 1: Add Test Support and Bootstrap Package Structure

**Files:**
- Modify: `/Users/nkanf/projs/MetalExp/build.gradle`
- Test: `/Users/nkanf/projs/MetalExp/src/test/java/dev/nkanf/metalexp/bootstrap/BackendPlannerTest.java`

- [ ] **Step 1: Add JUnit Jupiter dependencies and test task configuration**

```groovy
dependencies {
	minecraft "com.mojang:minecraft:${project.minecraft_version}"
	implementation "net.fabricmc:fabric-loader:${project.loader_version}"
	implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"

	testImplementation platform("org.junit:junit-bom:5.12.2")
	testImplementation "org.junit.jupiter:junit-jupiter"
}

test {
	useJUnitPlatform()
}
```

- [ ] **Step 2: Create the first failing planner test**

```java
package dev.nkanf.metalexp.bootstrap;

import dev.nkanf.metalexp.config.BackendKind;
import dev.nkanf.metalexp.config.FailureMode;
import dev.nkanf.metalexp.config.MetalExpConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BackendPlannerTest {
	@Test
	void metalFallbackIncludesMetalThenVulkanThenOpenGl() {
		MetalExpConfig config = new MetalExpConfig(BackendKind.METAL, FailureMode.FALLBACK, true);

		BackendPlan plan = BackendPlanner.plan(config, true);

		assertEquals(List.of(BackendKind.METAL, BackendKind.VULKAN, BackendKind.OPENGL), plan.backendsToTry());
		assertEquals(FailureMode.FALLBACK, plan.failureMode());
	}
}
```

- [ ] **Step 3: Run the test to confirm the planner classes do not exist yet**

Run: `./gradlew test --tests dev.nkanf.metalexp.bootstrap.BackendPlannerTest --console=plain`

Expected: FAIL with compilation errors for missing `BackendKind`, `FailureMode`, `MetalExpConfig`, `BackendPlan`, or `BackendPlanner`.

- [ ] **Step 4: Commit the build-system and test harness checkpoint**

```bash
git add build.gradle src/test/java/dev/nkanf/metalexp/bootstrap/BackendPlannerTest.java
git commit -m "test: add bootstrap planner test harness"
```

### Task 2: Implement the Project-Owned Backend Selection Core

**Files:**
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/config/BackendKind.java`
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/config/FailureMode.java`
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/config/MetalExpConfig.java`
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/bootstrap/BackendPlan.java`
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/bootstrap/BackendPlanner.java`
- Test: `/Users/nkanf/projs/MetalExp/src/test/java/dev/nkanf/metalexp/bootstrap/BackendPlannerTest.java`

- [ ] **Step 1: Implement backend selection enums and config record**

```java
package dev.nkanf.metalexp.config;

public enum BackendKind {
	METAL("metal"),
	VULKAN("vulkan"),
	OPENGL("opengl");

	private final String id;

	BackendKind(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}

	public static BackendKind fromId(String value) {
		for (BackendKind kind : values()) {
			if (kind.id.equalsIgnoreCase(value)) {
				return kind;
			}
		}
		return METAL;
	}
}
```

```java
package dev.nkanf.metalexp.config;

public enum FailureMode {
	FALLBACK("fallback"),
	STRICT("strict");

	private final String id;

	FailureMode(String id) {
		this.id = id;
	}

	public String id() {
		return this.id;
	}

	public static FailureMode fromId(String value) {
		for (FailureMode mode : values()) {
			if (mode.id.equalsIgnoreCase(value)) {
				return mode;
			}
		}
		return FALLBACK;
	}
}
```

```java
package dev.nkanf.metalexp.config;

public record MetalExpConfig(
	BackendKind backend,
	FailureMode failureMode,
	boolean diagnosticsEnabled
) {
	public static MetalExpConfig defaults() {
		return new MetalExpConfig(BackendKind.METAL, FailureMode.FALLBACK, true);
	}
}
```

- [ ] **Step 2: Implement the planner types**

```java
package dev.nkanf.metalexp.bootstrap;

import dev.nkanf.metalexp.config.BackendKind;
import dev.nkanf.metalexp.config.FailureMode;

import java.util.List;

public record BackendPlan(
	List<BackendKind> backendsToTry,
	FailureMode failureMode
) {
}
```

```java
package dev.nkanf.metalexp.bootstrap;

import dev.nkanf.metalexp.config.BackendKind;
import dev.nkanf.metalexp.config.FailureMode;
import dev.nkanf.metalexp.config.MetalExpConfig;

import java.util.List;

public final class BackendPlanner {
	private BackendPlanner() {
	}

	public static BackendPlan plan(MetalExpConfig config, boolean isMacOs) {
		List<BackendKind> order = switch (config.backend()) {
			case METAL -> isMacOs
				? List.of(BackendKind.METAL, BackendKind.VULKAN, BackendKind.OPENGL)
				: List.of(BackendKind.VULKAN, BackendKind.OPENGL);
			case VULKAN -> List.of(BackendKind.VULKAN, BackendKind.OPENGL);
			case OPENGL -> List.of(BackendKind.OPENGL, BackendKind.VULKAN);
		};

		FailureMode failureMode = config.backend() == BackendKind.METAL && isMacOs
			? config.failureMode()
			: FailureMode.FALLBACK;

		return new BackendPlan(order, failureMode);
	}
}
```

- [ ] **Step 3: Expand the planner test to cover strict mode and non-macOS behavior**

```java
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
```

- [ ] **Step 4: Run tests and confirm they pass**

Run: `./gradlew test --tests dev.nkanf.metalexp.bootstrap.BackendPlannerTest --console=plain`

Expected: PASS

- [ ] **Step 5: Commit the backend selection core**

```bash
git add src/main/java/dev/nkanf/metalexp/config src/main/java/dev/nkanf/metalexp/bootstrap src/test/java/dev/nkanf/metalexp/bootstrap/BackendPlannerTest.java
git commit -m "feat: add backend planning core"
```

### Task 3: Implement Config Persistence and Tests

**Files:**
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/config/MetalExpConfigStore.java`
- Create: `/Users/nkanf/projs/MetalExp/src/test/java/dev/nkanf/metalexp/config/MetalExpConfigStoreTest.java`
- Test: `/Users/nkanf/projs/MetalExp/src/test/java/dev/nkanf/metalexp/config/MetalExpConfigStoreTest.java`

- [ ] **Step 1: Add a failing config store test**

```java
package dev.nkanf.metalexp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MetalExpConfigStoreTest {
	@TempDir
	Path tempDir;

	@Test
	void loadCreatesDefaultsWhenFileDoesNotExist() {
		MetalExpConfigStore store = new MetalExpConfigStore(tempDir.resolve("metalexp.properties"));

		MetalExpConfig config = store.load();

		assertEquals(MetalExpConfig.defaults(), config);
	}
}
```

- [ ] **Step 2: Run the test to verify the config store is still missing**

Run: `./gradlew test --tests dev.nkanf.metalexp.config.MetalExpConfigStoreTest --console=plain`

Expected: FAIL with missing `MetalExpConfigStore`.

- [ ] **Step 3: Implement a simple properties-backed config store**

```java
package dev.nkanf.metalexp.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class MetalExpConfigStore {
	private final Path path;

	public MetalExpConfigStore(Path path) {
		this.path = path;
	}

	public MetalExpConfig load() {
		if (Files.notExists(this.path)) {
			return MetalExpConfig.defaults();
		}

		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(this.path)) {
			properties.load(input);
		} catch (IOException exception) {
			return MetalExpConfig.defaults();
		}

		return new MetalExpConfig(
			BackendKind.fromId(properties.getProperty("backend", "metal")),
			FailureMode.fromId(properties.getProperty("failureMode", "fallback")),
			Boolean.parseBoolean(properties.getProperty("diagnosticsEnabled", "true"))
		);
	}

	public void save(MetalExpConfig config) throws IOException {
		Files.createDirectories(this.path.getParent());

		Properties properties = new Properties();
		properties.setProperty("backend", config.backend().id());
		properties.setProperty("failureMode", config.failureMode().id());
		properties.setProperty("diagnosticsEnabled", Boolean.toString(config.diagnosticsEnabled()));

		try (OutputStream output = Files.newOutputStream(this.path)) {
			properties.store(output, "MetalExp configuration");
		}
	}
}
```

- [ ] **Step 4: Add a round-trip persistence test**

```java
@Test
void saveAndLoadRoundTripsUserSelection() throws Exception {
	Path path = tempDir.resolve("metalexp.properties");
	MetalExpConfigStore store = new MetalExpConfigStore(path);
	MetalExpConfig expected = new MetalExpConfig(BackendKind.OPENGL, FailureMode.STRICT, false);

	store.save(expected);

	assertEquals(expected, store.load());
}
```

- [ ] **Step 5: Run both test classes**

Run: `./gradlew test --tests dev.nkanf.metalexp.bootstrap.BackendPlannerTest --tests dev.nkanf.metalexp.config.MetalExpConfigStoreTest --console=plain`

Expected: PASS

- [ ] **Step 6: Commit the config store**

```bash
git add src/main/java/dev/nkanf/metalexp/config src/test/java/dev/nkanf/metalexp/config/MetalExpConfigStoreTest.java
git commit -m "feat: add MetalExp config persistence"
```

### Task 4: Wire Bootstrap Diagnostics and Runtime State

**Files:**
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/bootstrap/BootstrapState.java`
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/diag/DiagnosticsSnapshot.java`
- Create: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/diag/DiagnosticsReporter.java`
- Modify: `/Users/nkanf/projs/MetalExp/src/main/java/dev/nkanf/metalexp/MetalExpMod.java`
- Modify: `/Users/nkanf/projs/MetalExp/src/client/java/dev/nkanf/metalexp/client/MetalExpClient.java`

- [ ] **Step 1: Implement a snapshot of startup state**

```java
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
```

```java
package dev.nkanf.metalexp.diag;

import dev.nkanf.metalexp.MetalExpMod;

public final class DiagnosticsReporter {
	private DiagnosticsReporter() {
	}

	public static void logStartup(DiagnosticsSnapshot snapshot) {
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
```

- [ ] **Step 2: Implement bootstrap state storage**

```java
package dev.nkanf.metalexp.bootstrap;

import dev.nkanf.metalexp.config.MetalExpConfig;

public record BootstrapState(
	MetalExpConfig config,
	BackendPlan backendPlan
) {
}
```

- [ ] **Step 3: Load config and plan at mod initialization**

```java
package dev.nkanf.metalexp;

import dev.nkanf.metalexp.bootstrap.BackendPlan;
import dev.nkanf.metalexp.bootstrap.BackendPlanner;
import dev.nkanf.metalexp.bootstrap.BootstrapState;
import dev.nkanf.metalexp.config.MetalExpConfig;
import dev.nkanf.metalexp.config.MetalExpConfigStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MetalExpMod implements ModInitializer {
	public static final String MOD_ID = "metalexp";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static BootstrapState bootstrapState;

	@Override
	public void onInitialize() {
		MetalExpConfigStore store = new MetalExpConfigStore(
			FabricLoader.getInstance().getConfigDir().resolve("metalexp.properties")
		);
		MetalExpConfig config = store.load();
		BackendPlan plan = BackendPlanner.plan(config, isMacOs());

		bootstrapState = new BootstrapState(config, plan);
		LOGGER.info("Initializing {} bootstrap", MOD_ID);
	}

	public static BootstrapState bootstrapState() {
		return bootstrapState;
	}

	private static boolean isMacOs() {
		return System.getProperty("os.name", "").toLowerCase().contains("mac");
	}
}
```

- [ ] **Step 4: Emit diagnostics in the client initializer**

```java
package dev.nkanf.metalexp.client;

import dev.nkanf.metalexp.MetalExpMod;
import dev.nkanf.metalexp.diag.DiagnosticsReporter;
import dev.nkanf.metalexp.diag.DiagnosticsSnapshot;
import net.fabricmc.api.ClientModInitializer;

public final class MetalExpClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		DiagnosticsReporter.logStartup(new DiagnosticsSnapshot(
			MetalExpMod.bootstrapState().config(),
			MetalExpMod.bootstrapState().backendPlan(),
			System.getProperty("os.name", "unknown"),
			System.getProperty("os.arch", "unknown")
		));
	}
}
```

- [ ] **Step 5: Run a build to verify bootstrap wiring compiles**

Run: `./gradlew build --console=plain`

Expected: PASS

- [ ] **Step 6: Commit the diagnostics bootstrap**

```bash
git add src/main/java/dev/nkanf/metalexp src/client/java/dev/nkanf/metalexp/client
git commit -m "feat: add bootstrap diagnostics state"
```

### Task 5: Add a Client Startup Hook for Future Negotiation Work

**Files:**
- Modify: `/Users/nkanf/projs/MetalExp/src/client/java/dev/nkanf/metalexp/client/mixin/ClientBootstrapMixin.java`
- Modify: `/Users/nkanf/projs/MetalExp/README.md`

- [ ] **Step 1: Replace the placeholder mixin body with a negotiation log hook**

```java
package dev.nkanf.metalexp.client.mixin;

import dev.nkanf.metalexp.MetalExpMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientBootstrapMixin {
	@Inject(method = "run", at = @At("HEAD"))
	private void metalExp$onClientRun(CallbackInfo ci) {
		MetalExpMod.LOGGER.info(
			"Client startup hook active; planned backends={}",
			MetalExpMod.bootstrapState().backendPlan().backendsToTry()
		);
	}
}
```

- [ ] **Step 2: Update the README to explain the current milestone boundary**

```md
## Current Milestone

The current codebase only implements the bootstrap and backend-selection core:

- project-owned backend selection config
- fallback and strict-mode planning
- startup diagnostics
- a client startup hook for future negotiation work

It does not yet replace the in-game option screen or create a real Metal backend.
```

- [ ] **Step 3: Run the full test and build suite**

Run: `./gradlew test build --console=plain`

Expected: PASS

- [ ] **Step 4: Commit the milestone boundary**

```bash
git add README.md src/client/java/dev/nkanf/metalexp/client/mixin/ClientBootstrapMixin.java
git commit -m "docs: mark bootstrap milestone boundary"
```

## Follow-Up Plans Required

This plan deliberately stops before the next three subprojects:

1. settings UI replacement and project-owned option widget wiring
2. real backend negotiation hook into Minecraft startup
3. native Metal bridge and Java backend bring-up

The implementation should not begin those areas until this bootstrap plan is complete and reviewed.
