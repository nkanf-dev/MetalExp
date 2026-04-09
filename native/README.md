# Native Bridge

This directory is reserved for the macOS native bridge used by `MetalExp`.

Planned responsibilities:

- unwrap the GLFW window into Cocoa objects
- attach and manage `CAMetalLayer`
- create and own core Metal objects
- expose a small JNI bridge to the Fabric-side backend
