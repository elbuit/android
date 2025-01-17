# Introduction

This is the eduVPN / Let's Connect! for Android application.

You can clone this repository by executing the following command (provided you have git installed):
    
    git clone --recurse-submodules https://github.com/eduvpn/android.git

Or if your git version is below 2.13:

    git clone --recursive https://github.com/eduvpn/android.git

# Download the app

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/nl.eduvpn.app/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=nl.eduvpn.app)

# Running with Android Studio

First install the `swig` and `go` packages with your operating system package manager.

Make sure you have the latest stable version of Android Studio installed, you can download it from [here](https://developer.android.com/studio).
Open the project by opening the build.gradle in the root of this repository with Android Studio.
Make sure that you have the following packages installed in the SDK Manager (Tools -> SDK Manager):
* SDK Platforms - Android 10
* SDK Tools - Android SDK Build Tools
* SDK Tools - LLDB
* SDK Tools - CMake
* SDK Tools - Android SDK Platform-Tools
* SDK Tools - Android SDK Tools
* SDK Tools - NDK - 26.1.10909125 version (a later version might work, but is untested)

To find a specific version of a package (for the NDK), check the option 'Show Package Details'
in the bottom-lower corner of the SDK Manager.

The app should now build when selecting Run -> Run 'app', or clicking the green play button next
to the device selector.

# Building

**NOTE**: building Android applications takes a lot of resources. The VM used
to build the Let's Connect! application has 6GB of memory and 50G disk. 
Anything lower was not tested, but 1GB of memory definitely does not work.

The builder will spew a lot of backtraces/warnings, but does seem to complete 
eventually.

We last tested this on 2023-10-20 with Fedora 38.

We removed the Debian instructions as building on Debian no longer works on 
Debian 11 (as of 2023-03-06) and I have no desire to investigate fixing this.

## Dependencies

### Fedora

```bash
$ sudo dnf -y install \
    unzip \
    tar \
    git \
    swig \
    go \
    java-17-openjdk \
    java-17-openjdk-devel \
    ncurses-compat-libs \
    ninja-build \
    cmake \
    pv
```

## Key Store

Generate a key store for signing the Android application:

    $ keytool \
        -genkey \
        -keystore ${HOME}/android.jks \
        -keyalg RSA \
        -keysize 4096 \
        -sigalg SHA256withRSA \
        -dname "CN=eduVPN for Android" \
        -validity 10000 \
        -alias eduVPN

Additional documentation 
[here](https://developer.android.com/studio/publish/app-signing#signing-manually).

## Setup

```bash
$ ./builder_setup.sh
```

## Build

### Git

To build the app from git, i.e. tag, branch or commit, use the following after 
modifying the file if necessary. Use this if you want to build a test or 
development release.

```bash
$ ./build_app_git.sh
```

You'll find the signed output APK in `${HOME}/Projects`.

### Tar

To build the app from tar, i.e. the archive that contains the full source code
including all submodules, use the following after modifying the file if 
necessary. Use this if you want to use an official release.

```bash
$ ./build_app_tar.sh
```

You'll find the signed output APK in `${HOME}/Projects`.

# Testing

To run all tests, execute the following command:

```bash
$ ./gradlew :app:connectedBasicDebugAndroidTest
```
