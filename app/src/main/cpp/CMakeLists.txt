cmake_minimum_required(VERSION 3.22.1)

project("mahavtaar_native" LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Optimization flags for ARM64 and x86_64
add_compile_options(
    -O3
    -fPIC
    -Wall
    -Wextra
    -Wno-unused-parameter
    -Wno-unused-function
    -Wno-sign-compare
    -fexceptions
    -frtti
)

if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_compile_options(-march=armv8-a+fp+simd)
endif()

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper
    ${CMAKE_CURRENT_SOURCE_DIR}/llama
    ${CMAKE_CURRENT_SOURCE_DIR}/sherpa
)

# Source files: Georgi Gerganov GGML core, whisper.cpp, llama.cpp, and runtime bridges
set(SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml/ggml.c
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml/ggml-alloc.c
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml/ggml-backend.c
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml/ggml-quants.c
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper/whisper.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/llama.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/stt_neural_runtime.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llm_gguf_runtime.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/tts_neural_runtime.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/mahavtaar_native.cpp
)

# Imported sherpa-onnx and onnxruntime libraries
add_library(sherpa-onnx-c-api SHARED IMPORTED)
set_target_properties(sherpa-onnx-c-api PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libsherpa-onnx-c-api.so
)

add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libonnxruntime.so
)

# Build shared library
add_library(mahavtaar_native SHARED ${SOURCES})

# Link Android system libraries and neural runtimes
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    mahavtaar_native
    sherpa-onnx-c-api
    onnxruntime
    ${log-lib}
    ${android-lib}
)
