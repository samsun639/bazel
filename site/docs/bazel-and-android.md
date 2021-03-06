---
layout: documentation
title: Android and Bazel
---

# Android and Bazel

This page contains resources that help you use Bazel with Android projects. It
links to a tutorial, build rules, and other information specific to building
Android projects with Bazel.

## Working with Bazel

The following resources will help you work with Bazel on Android projects:

*  [Tutorial: Building an Android app](tutorial/android-app.html)
*  [Android rules](https://docs.bazel.build/versions/master/be/android.html)
*  [mobile-install for Android](mobile-install.html)
*  [Integration with Android Studio](ide.html)
*  [How Android Builds work in Bazel](https://blog.bazel.build/2018/02/14/how-android-builds-work-in-bazel.html)

## Android and new rules

**Note**: Creating new rules is for advanced build and test scenarios.
You do not need it when getting started with Bazel.

The following modules, configuration fragments, and providers will help you
[extend Bazel's capabilities](https://docs.bazel.build/versions/master/skylark/concepts.html)
when building your Android projects:

*  Modules:

   *  [`android_common`](skylark/lib/AndroidSkylarkApiProvider.html)
   *  [`AndroidSkylarkIdlInfo`](skylark/lib/AndroidSkylarkIdlInfo.html)

*  Configuration fragments:

   *  [`android`](skylark/lib/android.html)

*  Providers:

   *  [`android`](skylark/lib/AndroidSkylarkApiProvider.html)
