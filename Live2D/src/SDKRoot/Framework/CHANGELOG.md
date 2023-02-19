# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).


## [4-r.2] - 2021-02-17

### Added

* Implement anisotropic filtering for DirectX-based Renderer.
* Implement get pixel size and `PixelsPerUnit` of the model

### Changed

* Check pointer before use to avoid crash by [@Xrysnow](https://github.com/Xrysnow)

### Fixed

* Fix Physics input reflect flag on evaluate.
* Fix renderer for OpenGL.
  * Add delete mask buffer when renderer instance is destroyed.
* Fix delay in starting fade-out for expressions.
* Fix memory bug causing segmentation fault when reallocating memory by [@adrianiainlam](https://github.com/adrianiainlam)
* Fix reference size of model matrix.
* Fix memory leaking the color buffer on destroyed `CubismOffscreenFrame_OpenGLES2`.
* Fix argument name typo at `CubismEyeBlink::SetBlinkingInterval()`.


## [4-r.1] - 2020-01-30

### Added

* Add the callback function called on finished motion playback.

### Changed

* Include header files in CMake.
* `<GL/glew>` is not included on macOS if `CSM_TARGET_COCOS` is defined.

### Fixed

* Fix rendering not working properly on Android devices with Tegra.

### Deprecated

* Use `target_include_directories` instead of using `FRAMEWORK_XXX_INCLUDE_PATH` variable in application CMake.
* Use `target_compile_definitions` instead of using `FRAMEWORK_DEFINITIOINS` variable in application CMake.
* Specify `FRAMEWORK_SOURCE` variable also in OpenGL application CMake.


## [4-beta.2] - 2019-11-14

### Added

* Add the includes to `Framework` for Linux build.

### Changed

* Refactoring `CMakeLists.txt`

### Fixed

* Fix renderer for DirectX 9 / 11.
  * Add missing implementation: Check the dynamic flags.


## [4-beta.1] - 2019-09-04

### Added

* Support new Inverted Masking features.
* Add `.editorconfig` and `.gitattributes` to manage file formats.
* Add `.gitignore`.
* Add `CHANGELOG.md`.

### Changed

* Convert all file formats according to `.editorconfig`.

### Fixed

* Fix typo of `CubismCdiJson`.
* Fix invalid expressions of `CubismCdiJson`.


[4-r.2]: https://github.com/Live2D/CubismNativeFramework/compare/4-r.1...4-r.2
[4-r.1]: https://github.com/Live2D/CubismNativeFramework/compare/4-beta.2...4-r.1
[4-beta.2]: https://github.com/Live2D/CubismNativeFramework/compare/4-beta.1...4-beta.2
[4-beta.1]: https://github.com/Live2D/CubismNativeFramework/compare/0f5da4981cc636fe3892bb94d5c60137c9cf1eb1...4-beta.
