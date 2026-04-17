#include "metalexp_common.h"
#include <dispatch/dispatch.h>

static id<MTLDevice> metalexp_shared_buffer_device(void) {
	static id<MTLDevice> cached_device = nil;
	static dispatch_once_t once_token;
	dispatch_once(&once_token, ^{
		cached_device = MTLCreateSystemDefaultDevice();
	});
	return cached_device;
}

jlong metalexp_create_buffer(jlong length) {
	if (length <= 0) {
		@throw [NSException exceptionWithName:@"MetalExpBufferException"
			reason:@"Metal buffer creation requires a positive size."
			userInfo:nil];
	}

	id<MTLDevice> device = metalexp_shared_buffer_device();
	if (device == nil) {
		@throw [NSException exceptionWithName:@"MetalExpBufferException"
			reason:@"Metal buffer creation could not acquire an MTLDevice."
			userInfo:nil];
	}

	id<MTLBuffer> buffer = [device newBufferWithLength:(NSUInteger)length options:MTLResourceStorageModeShared];
	if (buffer == nil) {
		@throw [NSException exceptionWithName:@"MetalExpBufferException"
			reason:@"Metal buffer creation could not allocate an MTLBuffer."
			userInfo:nil];
	}

	metalexp_native_buffer *native_buffer = calloc(1, sizeof(metalexp_native_buffer));
	if (native_buffer == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpBufferException"
			reason:@"Metal buffer creation could not allocate native buffer bookkeeping."
			userInfo:nil];
	}

	native_buffer->buffer = (void *)CFBridgingRetain(buffer);
	native_buffer->length = (uint32_t)MIN((jlong)UINT32_MAX, length);
	return (jlong)(uintptr_t)native_buffer;
}

void metalexp_upload_buffer(jlong native_buffer_handle, jlong offset, const void *bytes, jlong capacity) {
	if (bytes == NULL || capacity <= 0) {
		@throw [NSException exceptionWithName:@"MetalExpBufferException"
			reason:@"Metal buffer upload requires non-empty direct bytes."
			userInfo:nil];
	}

	metalexp_native_buffer *native_buffer = metalexp_require_buffer(native_buffer_handle, "upload");
	if (offset < 0 || offset + capacity > (jlong)native_buffer->length) {
		@throw [NSException exceptionWithName:@"MetalExpBufferException"
			reason:@"Metal buffer upload range exceeded the native buffer length."
			userInfo:nil];
	}

	id<MTLBuffer> buffer = metalexp_buffer_object(native_buffer);
	memcpy((uint8_t *)buffer.contents + (NSUInteger)offset, bytes, (NSUInteger)capacity);
}

void metalexp_release_buffer(jlong native_buffer_handle) {
	if (native_buffer_handle == 0) {
		return;
	}

	metalexp_native_buffer *native_buffer = (metalexp_native_buffer *)(uintptr_t)native_buffer_handle;
	if (native_buffer->buffer != NULL) {
		(void)CFBridgingRelease(native_buffer->buffer);
	}
	free(native_buffer);
}
