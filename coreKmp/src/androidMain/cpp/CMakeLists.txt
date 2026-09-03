# CMake configuration for Monerujo JNI integration
cmake_minimum_required(VERSION 3.18.1)

project("monerujo_jni_bridge")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find required packages
find_library(log-lib log)
find_library(android-lib android)

# Define source files
set(JNI_BRIDGE_SOURCES
    jni_bridge/monerujo_jni_bridge.cpp
    jni_bridge/monerujo_helpers.cpp
)

# Define dynamic JNI bridge sources (支援測試環境)
set(DYNAMIC_JNI_BRIDGE_SOURCES
    jni_bridge/monerujo_dynamic_bridge.cpp
)

# Define monero-java compatibility bridge sources
set(MONERO_JAVA_BRIDGE_SOURCES
    monero_java_bridge.cpp
)

# Define real Monero bridge sources
set(MONERO_REAL_BRIDGE_SOURCES
    jni_bridge/monero_real_bridge.cpp
)

# Define monero_c bridge sources
set(MONERO_C_BRIDGE_SOURCES
    # jni_bridge/monero_c_jni_bridge.cpp  # Temporarily disabled due to duplicate symbols
    jni_bridge/context_aware_bridge.cpp  # Add context-aware initialization support
    # jni_bridge/monero_c_jni_bridge_network_type.cpp  # Replaced by real implementation
    jni_bridge/monero_wallet2_real.cpp  # REAL wallet2 implementation - NO STUBS!
)

# Create shared library for JNI bridge
add_library(monerujo_jni_bridge SHARED ${JNI_BRIDGE_SOURCES})

# Dynamic JNI bridge is omitted from the sanitized public export (blacklist:
# **/monerujo_dynamic_bridge.cpp — contains a hard-coded test seed). Private
# trees still build it when the source file is present.
set(MONERUJO_DYNAMIC_SRC "${CMAKE_CURRENT_SOURCE_DIR}/jni_bridge/monerujo_dynamic_bridge.cpp")
if(EXISTS "${MONERUJO_DYNAMIC_SRC}")
    add_library(monerujo_dynamic SHARED ${DYNAMIC_JNI_BRIDGE_SOURCES})
else()
    message(STATUS "Skipping monerujo_dynamic (source not present in this tree)")
endif()

# Create shared library for monero-java compatibility
# Disabled until we have proper Monero libraries for Android
# add_library(monero_java_bridge SHARED ${MONERO_JAVA_BRIDGE_SOURCES})

# Create shared library for real Monero bridge
# Disabled until we have proper Monero libraries for Android
# add_library(monero_real_bridge SHARED ${MONERO_REAL_BRIDGE_SOURCES})

# Create shared library for monero_c bridge
add_library(monero_c_bridge SHARED ${MONERO_C_BRIDGE_SOURCES})

# Include directories
target_include_directories(monerujo_jni_bridge PRIVATE
    jni_bridge
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Link libraries
target_link_libraries(monerujo_jni_bridge
    ${log-lib}
    ${android-lib}
)

# Link libraries for monero-java bridge
# Disabled until we have proper Monero libraries for Android
# target_link_libraries(monero_java_bridge
#     ${log-lib}
#     ${android-lib}
#     dl  # for dlopen/dlsym
#     monero-cpp
#     monero-java
# )

# Note: monero_c_bridge uses dlopen to dynamically load libmonero_libwallet2_api_c.so
# The .so file is already included in the project and doesn't need to be imported

# Include directories for monero_c headers
# Note: monero_c uses its own headers in /tmp/monero_c/monero_libwallet2_api_c/src/main/cpp
# These are included in the monero_c_bridge target configuration

# Set output directory
set_target_properties(monerujo_jni_bridge PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
)

# Add compiler flags
target_compile_options(monerujo_jni_bridge PRIVATE
    -Wall
    -Wextra
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)

# Add linker flags
target_link_options(monerujo_jni_bridge PRIVATE
    -Wl,--gc-sections
    -Wl,--strip-all
)

# Configure dynamic bridge library (only when the target was created above)
if(TARGET monerujo_dynamic)
    target_include_directories(monerujo_dynamic PRIVATE
        jni_bridge
        ${CMAKE_CURRENT_SOURCE_DIR}
    )

    # monerujo_dynamic doesn't need monero-cpp or monero-java
    # It only needs system libraries
    target_link_libraries(monerujo_dynamic
        ${log-lib}
        ${android-lib}
    )

    # Use hidden visibility and only export JNI_OnLoad
    target_compile_options(monerujo_dynamic PRIVATE
        -Wall
        -Wextra
        -fvisibility=hidden
        -ffunction-sections
        -fdata-sections
    )

    target_link_options(monerujo_dynamic PRIVATE
        -Wl,--gc-sections
        -Wl,--strip-all
        -Wl,--exclude-libs,ALL
    )

    set_target_properties(monerujo_dynamic PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
    )
endif()

# Configure real Monero bridge
# Disabled until we have proper Monero libraries for Android
# if(MONERO_LIBS_AVAILABLE)
#     target_include_directories(monero_real_bridge PRIVATE
#         jni_bridge
#         ${CMAKE_CURRENT_SOURCE_DIR}
#         ${CMAKE_SOURCE_DIR}/../../../../monero-java-build/monero-java/external/monero-cpp/external/monero-project/src/wallet/api
#     )
#     
#     target_link_libraries(monero_real_bridge
#         ${log-lib}
#         ${android-lib}
#         monero-cpp
#         monero-java
#     )
#     
#     target_compile_options(monero_real_bridge PRIVATE
#         -Wall
#         -Wextra
#         -fvisibility=hidden
#     )
#     
#     set_target_properties(monero_real_bridge PROPERTIES
#         LIBRARY_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
#     )
# endif()

# Configure monero_c bridge
target_include_directories(monero_c_bridge PRIVATE
    jni_bridge
    ${CMAKE_CURRENT_SOURCE_DIR}
    /tmp/monero_c/monero_libwallet2_api_c/src/main/cpp
)

target_link_libraries(monero_c_bridge
    ${log-lib}
    ${android-lib}
    dl  # for dlopen/dlsym
    c++_shared  # C++ standard library for stringstream and other C++ features
)

target_compile_options(monero_c_bridge PRIVATE
    -Wall
    -Wextra
    -fvisibility=hidden
)

set_target_properties(monero_c_bridge PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}
)