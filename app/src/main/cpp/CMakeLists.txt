cmake_minimum_required(VERSION 3.22.1)
project(agave_native LANGUAGES C CXX)

set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ENGINE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/engine)
file(GLOB ENGINE_SOURCES CONFIGURE_DEPENDS ${ENGINE_DIR}/src/*.c)

add_library(needle_engine STATIC ${ENGINE_SOURCES})
target_include_directories(needle_engine PUBLIC ${ENGINE_DIR}/include)
target_compile_options(needle_engine PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -O3
)
set_target_properties(needle_engine PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(agave SHARED agave_jni.cpp)
target_include_directories(agave PRIVATE ${ENGINE_DIR}/include)
target_compile_options(agave PRIVATE -Wall -Wextra -O3)

target_link_libraries(agave PRIVATE needle_engine android log m)
