# Description:
#   C++ utility tests for Bazel
package(default_visibility = ["//visibility:public"])

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
    visibility = ["//src/test/cpp:__pkg__"],
)

cc_test(
    name = "md5_test",
    srcs = ["md5_test.cc"],
    deps = [
        "//src/main/cpp/util",
        "//src/main/cpp/util:md5",
        "@com_google_googletest//:gtest_main",
    ],
)

cc_test(
    name = "file_test",
    size = "small",
    srcs = ["file_test.cc"] + select({
        "//src/conditions:windows": [
            "file_windows_test.cc",
        ],
        "//conditions:default": [
            "file_posix_test.cc",
        ],
    }),
    deps = [
        ":test_util",
        "//src/main/cpp/util:file",
        "@com_google_googletest//:gtest_main",
    ] + select({
        "//src/conditions:windows": [
            ":windows_test_util",
            "//src/main/native/windows:lib-file",
        ],
        "//conditions:default": [],
    }),
)

cc_test(
    name = "logging_test",
    srcs = ["logging_test.cc"],
    deps = [
        "//src/main/cpp:blaze_util",
        "//src/main/cpp/util:bazel_log_handler",
        "//src/main/cpp/util:logging",
        "@com_google_googletest//:gtest_main",
    ],
)

cc_test(
    name = "numbers_test",
    srcs = ["numbers_test.cc"],
    deps = [
        "//src/main/cpp/util",
        "@com_google_googletest//:gtest_main",
    ],
)

cc_test(
    name = "strings_test",
    srcs = ["strings_test.cc"],
    shard_count = 2,
    deps = [
        "//src/main/cpp/util:strings",
        "@com_google_googletest//:gtest_main",
    ],
)

cc_library(
    name = "windows_test_util",
    testonly = 1,
    srcs = select({
        "//src/conditions:windows": ["windows_test_util.cc"],
        "//conditions:default": [],
    }),
    hdrs = select({
        "//src/conditions:windows": ["windows_test_util.h"],
        "//conditions:default": [],
    }),
    visibility = [
        "//src/test/cpp:__subpackages__",
        "//src/test/native:__subpackages__",
    ],
)

cc_test(
    name = "windows_test_util_test",
    size = "small",
    srcs = select({
        "//src/conditions:windows": ["windows_test_util_test.cc"],
        "//conditions:default": ["dummy_test.cc"],
    }),
    deps = select({
        "//src/conditions:windows": [
            ":windows_test_util",
            "//third_party:gtest",
        ],
        "//conditions:default": [],
    }),
)

cc_library(
    name = "test_util",
    testonly = 1,
    hdrs = ["test_util.h"],
    visibility = ["//visibility:private"],
)

test_suite(name = "all_tests")

test_suite(
    name = "windows_tests",
    tags = [
        "-no_windows",
        "-slow",
    ],
    visibility = ["//visibility:private"],
)

test_suite(
    name = "all_windows_tests",
    tests = [
        ":windows_tests",
    ],
    visibility = ["//src/test/cpp:__pkg__"],
)
