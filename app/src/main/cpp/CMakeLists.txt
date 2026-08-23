cmake_minimum_required(VERSION 3.22.1)

project("phonelm")

# Explicitly set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enable Vulkan for GPU acceleration
set(GGML_USE_VULKAN ON CACHE BOOL "Use Vulkan backend" FORCE)

# Setup dependency for log
find_library(log-lib log)

# We assume the user has placed the llama.cpp repository in the 'llama.cpp' subdirectory
# If not present, this will error. Ideally, we would fetch it, but we can't network here easily for git clone.
# The user must ensure 'app/src/main/cpp/llama.cpp' exists.
add_subdirectory(llama.cpp)

# NativeBridge
add_library(phonelm SHARED NativeBridge.cpp)

# Link against llama (which comes from add_subdirectory) and android log
target_link_libraries(phonelm
    PRIVATE
    llama
    ${log-lib}
    android
)
