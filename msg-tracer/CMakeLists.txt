cmake_minimum_required(VERSION 3.22.1)

project("anrwatcher")

add_library(
        ${CMAKE_PROJECT_NAME}
        SHARED
        src/main/cpp/AnrWatcher.cc
        src/main/cpp/SignalHandler.cc
        src/main/cpp/AnrDumper.cc
        src/main/cpp/managed_jnienv.cc
        src/main/cpp/Support.cc
)

target_link_libraries(
        ${CMAKE_PROJECT_NAME}
        android
        log
)