#include "metalexp_common.h"
#include <dispatch/dispatch.h>

enum {
	METALEXP_STAGING_SLOT_VERTEX = 0,
	METALEXP_STAGING_SLOT_INDEX = 1,
	METALEXP_STAGING_SLOT_UNIFORM = 2
};

typedef struct metalexp_staging_block {
	void *buffer;
	NSUInteger capacity;
	NSUInteger used;
	struct metalexp_staging_block *next;
} metalexp_staging_block;

static id<MTLCommandQueue> metalexp_shared_command_queue(void) {
	static id<MTLDevice> cached_device = nil;
	static id<MTLCommandQueue> cached_queue = nil;
	static dispatch_once_t once_token;
	dispatch_once(&once_token, ^{
		id<MTLDevice> device = MTLCreateSystemDefaultDevice();
		if (device == nil) {
			return;
		}

		id<MTLCommandQueue> command_queue = [device newCommandQueue];
		if (command_queue == nil) {
			return;
		}

		cached_device = device;
		cached_queue = command_queue;
	});
	(void) cached_device;
	return cached_queue;
}

static metalexp_staging_block **metalexp_staging_block_head(metalexp_command_context *command_context, int slot) {
	switch (slot) {
		case METALEXP_STAGING_SLOT_VERTEX:
			return (metalexp_staging_block **)&command_context->vertex_staging_blocks;
		case METALEXP_STAGING_SLOT_INDEX:
			return (metalexp_staging_block **)&command_context->index_staging_blocks;
		case METALEXP_STAGING_SLOT_UNIFORM:
			return (metalexp_staging_block **)&command_context->uniform_staging_blocks;
		default:
			@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
				reason:@"Metal command context received an unknown staging slot."
				userInfo:nil];
	}
}

static NSUInteger metalexp_align_up(NSUInteger value, NSUInteger alignment) {
	if (alignment <= 1) {
		return value;
	}

	NSUInteger remainder = value % alignment;
	return remainder == 0 ? value : value + (alignment - remainder);
}

static NSUInteger metalexp_staging_alignment(int slot) {
	return slot == METALEXP_STAGING_SLOT_UNIFORM ? 256U : 4U;
}

static NSUInteger metalexp_staging_initial_capacity(int slot) {
	switch (slot) {
		case METALEXP_STAGING_SLOT_VERTEX:
			return 1U << 20;
		case METALEXP_STAGING_SLOT_INDEX:
			return 1U << 18;
		case METALEXP_STAGING_SLOT_UNIFORM:
			return 1U << 14;
		default:
			return 4096U;
	}
}

static void metalexp_reset_staging_blocks(metalexp_staging_block *block) {
	for (metalexp_staging_block *cursor = block; cursor != NULL; cursor = cursor->next) {
		cursor->used = 0;
	}
}

static void metalexp_release_staging_blocks(metalexp_staging_block *block) {
	metalexp_staging_block *cursor = block;
	while (cursor != NULL) {
		metalexp_staging_block *next = cursor->next;
		if (cursor->buffer != NULL) {
			(void)CFBridgingRelease(cursor->buffer);
		}
		free(cursor);
		cursor = next;
	}
}

static BOOL metalexp_command_buffer_is_terminal(MTLCommandBufferStatus status) {
	return status == MTLCommandBufferStatusCompleted
		|| status == MTLCommandBufferStatusError;
}

static BOOL metalexp_finish_command_buffer(metalexp_command_context *command_context, BOOL wait_for_completion) {
	if (command_context->command_buffer == NULL) {
		return YES;
	}

	id<MTLCommandBuffer> command_buffer = (__bridge id<MTLCommandBuffer>)command_context->command_buffer;
	MTLCommandBufferStatus status = command_buffer.status;
	if (status == MTLCommandBufferStatusNotEnqueued) {
		return NO;
	}

	if (!metalexp_command_buffer_is_terminal(status)) {
		if (!wait_for_completion) {
			return NO;
		}

		[command_buffer waitUntilCompleted];
		status = command_buffer.status;
	}

	NSString *message = nil;
	if (status == MTLCommandBufferStatusError) {
		message = command_buffer.error.localizedDescription;
		if (message == nil || message.length == 0) {
			message = @"Metal command context command buffer failed.";
		}
	}

	(void)CFBridgingRelease(command_context->command_buffer);
	command_context->command_buffer = NULL;
	metalexp_reset_staging_blocks((metalexp_staging_block *)command_context->vertex_staging_blocks);
	metalexp_reset_staging_blocks((metalexp_staging_block *)command_context->index_staging_blocks);
	metalexp_reset_staging_blocks((metalexp_staging_block *)command_context->uniform_staging_blocks);

	if (message != nil) {
		@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
			reason:message
			userInfo:nil];
	}

	return YES;
}

static metalexp_staging_buffer_slice metalexp_stage_bytes(
	jlong native_command_context_handle,
	id<MTLDevice> device,
	const void *bytes,
	NSUInteger length,
	int slot,
	const char *operation_name
) {
	if (bytes == NULL || length == 0) {
		@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
			reason:[NSString stringWithFormat:@"Metal command context %s requires non-empty staging input.", operation_name]
			userInfo:nil];
	}

	metalexp_command_context *command_context = metalexp_require_command_context(native_command_context_handle, operation_name);
	metalexp_staging_block **head = metalexp_staging_block_head(command_context, slot);
	metalexp_staging_block *block = *head;
	NSUInteger alignment = metalexp_staging_alignment(slot);

	while (block != NULL) {
		NSUInteger aligned_used = metalexp_align_up(block->used, alignment);
		if (block->capacity >= aligned_used && block->capacity - aligned_used >= length) {
			memcpy((uint8_t *)((__bridge id<MTLBuffer>)block->buffer).contents + aligned_used, bytes, length);
			block->used = aligned_used + length;
			return (metalexp_staging_buffer_slice){
				.buffer = (__bridge id<MTLBuffer>)block->buffer,
				.offset = aligned_used
			};
		}

		if (block->next == NULL) {
			break;
		}
		block = block->next;
	}

	NSUInteger new_capacity = MAX(metalexp_staging_initial_capacity(slot), length);
	if (block != NULL) {
		new_capacity = MAX(new_capacity, block->capacity << 1);
	}

	id<MTLBuffer> staging_buffer = [device newBufferWithLength:new_capacity options:MTLResourceStorageModeShared];
	if (staging_buffer == nil) {
		@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
			reason:[NSString stringWithFormat:@"Metal command context %s could not allocate a staging MTLBuffer.", operation_name]
			userInfo:nil];
	}

	metalexp_staging_block *new_block = calloc(1, sizeof(metalexp_staging_block));
	if (new_block == NULL) {
		@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
			reason:[NSString stringWithFormat:@"Metal command context %s could not allocate staging bookkeeping.", operation_name]
			userInfo:nil];
	}

	new_block->buffer = (void *)CFBridgingRetain(staging_buffer);
	new_block->capacity = new_capacity;
	new_block->used = length;
	if (block == NULL) {
		*head = new_block;
	} else {
		block->next = new_block;
	}

	memcpy(staging_buffer.contents, bytes, length);
	return (metalexp_staging_buffer_slice){
		.buffer = staging_buffer,
		.offset = 0
	};
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

	if (command_context->command_buffer != NULL) {
		id<MTLCommandBuffer> command_buffer = (__bridge id<MTLCommandBuffer>)command_context->command_buffer;
		MTLCommandBufferStatus status = command_buffer.status;
		if (status == MTLCommandBufferStatusNotEnqueued) {
			return command_buffer;
		}
		if (!metalexp_finish_command_buffer(command_context, NO)) {
			@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
				reason:@"Metal command context already has in-flight GPU work."
				userInfo:nil];
		}
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
	if (command_buffer.status != MTLCommandBufferStatusNotEnqueued) {
		@throw [NSException exceptionWithName:@"MetalExpCommandContextException"
			reason:@"Metal command context submit requires a fresh, unsubmitted MTLCommandBuffer."
			userInfo:nil];
	}
	[command_buffer commit];
}

BOOL metalexp_is_command_context_complete(jlong native_command_context_handle) {
	metalexp_command_context *command_context = metalexp_require_command_context(native_command_context_handle, "poll");
	return metalexp_finish_command_buffer(command_context, NO) == YES;
}

void metalexp_wait_for_command_context(jlong native_command_context_handle) {
	metalexp_command_context *command_context = metalexp_require_command_context(native_command_context_handle, "wait");
	(void)metalexp_finish_command_buffer(command_context, YES);
}

metalexp_staging_buffer_slice metalexp_stage_vertex_bytes(jlong native_command_context_handle, id<MTLDevice> device, const void *bytes, NSUInteger length, const char *operation_name) {
	return metalexp_stage_bytes(native_command_context_handle, device, bytes, length, METALEXP_STAGING_SLOT_VERTEX, operation_name);
}

metalexp_staging_buffer_slice metalexp_stage_index_bytes(jlong native_command_context_handle, id<MTLDevice> device, const void *bytes, NSUInteger length, const char *operation_name) {
	return metalexp_stage_bytes(native_command_context_handle, device, bytes, length, METALEXP_STAGING_SLOT_INDEX, operation_name);
}

metalexp_staging_buffer_slice metalexp_stage_uniform_bytes(jlong native_command_context_handle, id<MTLDevice> device, const void *bytes, NSUInteger length, const char *operation_name) {
	return metalexp_stage_bytes(native_command_context_handle, device, bytes, length, METALEXP_STAGING_SLOT_UNIFORM, operation_name);
}

void metalexp_release_command_context(jlong native_command_context_handle) {
	if (native_command_context_handle == 0) {
		return;
	}

	metalexp_command_context *command_context = (metalexp_command_context *)(uintptr_t)native_command_context_handle;
	if (command_context->command_buffer != NULL) {
		(void)metalexp_finish_command_buffer(command_context, YES);
	}
	if (command_context->command_queue != NULL) {
		(void)CFBridgingRelease(command_context->command_queue);
	}
	metalexp_release_staging_blocks((metalexp_staging_block *)command_context->vertex_staging_blocks);
	metalexp_release_staging_blocks((metalexp_staging_block *)command_context->index_staging_blocks);
	metalexp_release_staging_blocks((metalexp_staging_block *)command_context->uniform_staging_blocks);

	free(command_context);
}
