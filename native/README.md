# Native Bridge

This directory is reserved for the macOS native bridge used by `MetalExp`.

Planned responsibilities:

- unwrap the GLFW window into Cocoa objects
- attach and manage `CAMetalLayer`
- create and own core Metal objects
- expose a small JNI bridge to the Fabric-side backend

Current scope:

- a minimal JNI-loadable stub library target named `metalexp_native`
- a native probe entrypoint that checks `CAMetalLayer` availability, default `MTLDevice` access, and `MTLCommandQueue` creation
- a window-level probe entrypoint that validates GLFW-derived Cocoa `NSWindow` / `NSView` hosts by temporarily attaching and restoring a `CAMetalLayer`
- no real `CAMetalLayer` attachment, drawable lifecycle, or persistent surface ownership yet

On macOS, `./gradlew build` now also emits a JNI dylib at `build/native/libmetalexp_native.dylib`.
You can point the Java bridge at that artifact with the `metalexp.nativeLibraryPath` system property while iterating on the native probe.
