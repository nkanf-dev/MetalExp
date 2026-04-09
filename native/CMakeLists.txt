cmake_minimum_required(VERSION 3.25)

project(MetalExpNative LANGUAGES C CXX OBJC OBJCXX)

find_package(JNI REQUIRED)

add_library(metalexp_native SHARED
	src/metalexp_bridge_probe.m
)

target_include_directories(metalexp_native PRIVATE
	${JNI_INCLUDE_DIRS}
)

target_compile_features(metalexp_native PRIVATE c_std_11)

if(APPLE)
	target_link_libraries(metalexp_native PRIVATE
		"-framework AppKit"
		"-framework Foundation"
		"-framework Metal"
		"-framework QuartzCore"
	)
endif()
