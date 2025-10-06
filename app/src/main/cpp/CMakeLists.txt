cmake_minimum_required(VERSION 3.22.1)

project("edgeviewer")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# OpenCV path (adjust based on your OpenCV Android SDK location)
set(OpenCV_DIR "${CMAKE_SOURCE_DIR}/../../../../../opencv-android-sdk/sdk/native/jni")

# Find OpenCV
find_package(OpenCV REQUIRED)

# Include directories
include_directories(${OpenCV_INCLUDE_DIRS})

# Add native library
add_library(
        edgeviewer
        SHARED
        processor.cpp
)

# Link libraries
target_link_libraries(
        edgeviewer
        ${OpenCV_LIBS}
        android
        log
)
