cmake_minimum_required(VERSION 3.22.1)
project(nemotron_diar LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(NEMO_ROOT ${CMAKE_CURRENT_LIST_DIR}/../nemo_speech)
add_subdirectory(${NEMO_ROOT}/ggml ${CMAKE_CURRENT_BINARY_DIR}/ggml)

file(GLOB NEMO_RUNTIME ${NEMO_ROOT}/src/runtime/ggml/*.cpp)
file(GLOB NEMO_DIAR ${NEMO_ROOT}/src/asr/diar/*.cpp)
add_library(nemotron_diar SHARED
    nemotron_jni.cpp
    ${NEMO_RUNTIME}
    ${NEMO_DIAR}
    ${NEMO_ROOT}/src/asr/features/fe.cpp
    ${NEMO_ROOT}/src/asr/encoder/fastconformer.cpp
    ${NEMO_ROOT}/src/asr/encoder/rel_pos_attention.cpp
    ${NEMO_ROOT}/src/asr/encoder/rope_transformer.cpp
    ${NEMO_ROOT}/src/asr/encoder/transformer_encoder.cpp
    ${NEMO_ROOT}/src/common/audio_resampler.cpp
    ${NEMO_ROOT}/src/common/parameter_parser.cpp)
target_include_directories(nemotron_diar PRIVATE
    ${NEMO_ROOT}/src/runtime/ggml
    ${NEMO_ROOT}/src/asr
    ${NEMO_ROOT}/src/asr/diar
    ${NEMO_ROOT}/src/asr/encoder
    ${NEMO_ROOT}/src/asr/features
    ${NEMO_ROOT}/src/common
    ${NEMO_ROOT}/ggml/include
    ${NEMO_ROOT}/ggml/src)
target_compile_definitions(nemotron_diar PRIVATE GGML_USE_CPU)
target_link_libraries(nemotron_diar PRIVATE ggml ggml-base ggml-cpu log android)
target_link_options(nemotron_diar PRIVATE
    "-Wl,--exclude-libs,ALL"
    "-Wl,--version-script=${CMAKE_CURRENT_LIST_DIR}/exports.map")
