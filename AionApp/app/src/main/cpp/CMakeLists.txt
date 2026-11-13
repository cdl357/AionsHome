# 本地模型 JNI 层：只编译薄包装，llama/ggml 用预编译 .so（jniLibs）
cmake_minimum_required(VERSION 3.22)
project(local-llama C CXX)

set(LLAMA_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/llama-include")

add_library(local-llama SHARED local-llama.c)

set(JNILIBS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION "${JNILIBS_DIR}/libllama.so")
add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION "${JNILIBS_DIR}/libggml.so")

target_include_directories(local-llama PRIVATE ${LLAMA_INCLUDE_DIR})

find_library(log-lib log)
target_link_libraries(local-llama ${log-lib} llama ggml)

# ── 沙盒 pty（照橘瓣 workspace 模块：交互终端的 termux-pty 原生层） ──
add_library(termux_pty SHARED termux_pty.cpp)
target_link_libraries(termux_pty ${log-lib})
