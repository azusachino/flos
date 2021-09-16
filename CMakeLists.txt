project(flos)

cmake_minimum_required(VERSION 3.20)

include_directories(include)
aux_source_directory(src src)

add_executable(flos main.cpp)