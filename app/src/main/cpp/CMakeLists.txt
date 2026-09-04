cmake_minimum_required(VERSION 3.22.1)

project("fiberhome")

add_library(
        fiberhome
        SHARED
        native-lib.cpp
        lib.c)

find_library(
        log-lib
        log)

target_link_libraries(
        fiberhome
        ${log-lib})
