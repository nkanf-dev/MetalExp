# Native Bridge

This directory is reserved for the macOS native bridge used by `MetalExp`.

Planned responsibilities:

- unwrap the GLFW window into Cocoa objects
- attach and manage `CAMetalLayer`
- create and own core Metal objects
- expose a small JNI bridge to the Fabric-side backend

Current scope:

- a minimal JNI-loadable stub library target named `metalexp_native`
- a native probe entrypoint that returns a structured "stub not implemented yet" result
- no Cocoa extraction, `CAMetalLayer`, `MTLDevice`, or drawable lifecycle work yet
