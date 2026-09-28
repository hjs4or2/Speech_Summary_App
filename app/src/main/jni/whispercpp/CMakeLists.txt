cmake_minimum_required(VERSION 3.22.1)
project(whispercpp)

set(CMAKE_CXX_STANDARD 17)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(WHISPER_ROOT ${CMAKE_CURRENT_LIST_DIR})
set(SOURCE_FILES ${WHISPER_ROOT}/src/whisper.cpp ${WHISPER_ROOT}/jni.c)

add_library(whisper SHARED ${SOURCE_FILES})
target_compile_definitions(whisper PUBLIC GGML_USE_CPU)
target_compile_definitions(whisper PRIVATE WHISPER_VERSION="1.9.4")

add_subdirectory(${WHISPER_ROOT}/ggml ${CMAKE_CURRENT_BINARY_DIR}/ggml)
find_library(LOG_LIB log)
target_include_directories(whisper PRIVATE
    ${WHISPER_ROOT}
    ${WHISPER_ROOT}/src
    ${WHISPER_ROOT}/include
    ${WHISPER_ROOT}/ggml/include
    ${WHISPER_ROOT}/ggml/src
    ${WHISPER_ROOT}/ggml/src/ggml-cpu
)
target_link_libraries(whisper ${LOG_LIB} android ggml ggml-cpu ggml-base)
