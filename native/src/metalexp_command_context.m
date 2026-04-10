#include "metalexp_common.h"

static id<MTLCommandQueue> metalexp_shared_command_queue(void) {
	static id<MTLDevice> cached_device = nil;
	static id<MTLCommandQueue> cached_queue = nil;
	if (cached_queue != nil) {
		return cached_queue;
	}

	id<MTLDevice> device = MTLCreateSystemDefaultDevice();
	if (device == nil) {
		return nil;
	}

	id<MTLCommandQueue> command_queue = [device newCommandQueue];
	if (command_queue == nil) {
		return nil;
	}

	cached_device = device;
	cached_queue = command_queue;
	(void) cached_device;
	return cached_queue;
}

jlong metalexp_create_command_context(void) {
	id<MTLCommandQueue> command_queue = metalexp_shared_command_queue();
	if (command_queue == nil) {
		@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
			reason:@"Metal command context creation could not allocate an MTLCommandQueue."
			userInfo:nil];
	}

	metalexp_command_context *command_context = calloc(1, sizeof(metalexp_command_context));
	if (command_context == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
			reason:@"Metal command context creation could not allocate native command context storage."
			userInfo:nil];
	}

	command_context->command_queue = (void *)CFBridgingRetain(command_queue);
	command_context->command_buffer = NULL;
	return (jlong)(uintptr_t)command_context;
}

id<MTLCommandBuffer> metalexp_command_buffer_for_context(jlong native_command_context_handle, id<MTLDevice> device, const char *operation_name) {
	metalexp_command_context *command_context = metalexp_require_command_context(native_command_context_handle, operation_name);
	id<MTLCommandQueue> command_queue = (__bridge id<MTLCommandQueue>)command_context->command_queue;
	if (command_queue == nil) {
		@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
			reason:@"Metal command context did not retain an active MTLCommandQueue."
			userInfo:nil];
	}
	if (device == nil) {
		@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
			reason:@"Metal command context operation requires an active MTLDevice."
			userInfo:nil];
	}
	if (command_queue.device != device) {
		@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
			reason:@"Metal command context device did not match the target texture or surface device."
			userInfo:nil];
	}

	if (command_context->command_buffer == NULL) {
		id<MTLCommandBuffer> command_buffer = [command_queue commandBuffer];
		if (command_buffer == nil) {
			@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
				reason:@"Metal command context could not allocate an MTLCommandBuffer."
				userInfo:nil];
		}

		command_context->command_buffer = (void *)CFBridgingRetain(command_buffer);
	}

	return (__bridge id<MTLCommandBuffer>)command_context->command_buffer;
}

void metalexp_submit_command_context(jlong native_command_context_handle) {
	metalexp_command_context *command_context = metalexp_require_command_context(native_command_context_handle, "submit");
	if (command_context->command_buffer == NULL) {
		return;
	}

	id<MTLCommandBuffer> command_buffer = (__bridge id<MTLCommandBuffer>)command_context->command_buffer;
	[command_buffer commit];
	[command_buffer waitUntilCompleted];

	if (command_buffer.error != nil) {
		NSString *message = command_buffer.error.localizedDescription == nil
			? @"Metal command context command buffer failed."
			: command_buffer.error.localizedDescription;
		(void)CFBridgingRelease(command_context->command_buffer);
		command_context->command_buffer = NULL;
		@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
			reason:message
			userInfo:nil];
	}

	(void)CFBridgingRelease(command_context->command_buffer);
	command_context->command_buffer = NULL;
}

void metalexp_release_command_context(jlong native_command_context_handle) {
	if (native_command_context_handle == 0) {
		return;
	}

	metalexp_command_context *command_context = (metalexp_command_context *)(uintptr_t)native_command_context_handle;
	if (command_context->command_buffer != NULL) {
		(void)CFBridgingRelease(command_context->command_buffer);
	}
	if (command_context->command_queue != NULL) {
		(void)CFBridgingRelease(command_context->command_queue);
	}

	free(command_context);
}
