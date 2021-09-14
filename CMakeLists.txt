cmake_minimum_required(VERSION 3.20)

project(FLOS)

include_directories(include)

AUX_SOURCE_DIRECTORY(src src)

add_executable(flos main.cc)