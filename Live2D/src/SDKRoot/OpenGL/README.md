# Cubism Native Samples for OpenGL

OpenGL で実装したアプリケーションのサンプル実装です。


## 開発環境

| サードパーティ | バージョン |
| --- | --- |
| [GLEW] | 2.2.0 |
| [GLFW] | 3.3.2 |
| [ios-cmake] | 3.1.2 |
| [stb_image.h] | 2.23 |

その他の開発環境・動作確認環境はトップディレクトリにある [README.md](/README.md) を参照してください。


## ディレクトリ構造

```
.
├─ Demo
│  ├─ proj.android.cmake    # Android Studio project
│  ├─ proj.ios.cmake        # CMake project for iOS
│  ├─ proj.linux.cmake      # CMake project for Linux
│  ├─ proj.mac.cmake        # CMake project for macOS
│  └─ proj.win.cmake        # CMake project for Windows
└─ thirdParty               # Third party libraries and scripts
```


## Demo

[Cubism Native Framework] の各機能を一通り使用したサンプルです。
モーションの再生、表情の設定、ポーズの切り替え、物理演算の設定などを行います。
メニューボタンからモデルを切り替えることができます。

[Cubism Native Framework]: https://github.com/Live2D/CubismNativeFramework

このディレクトリ内に含まれるものは以下の通りです。

### proj.android.cmake

Android 用の Android Studio プロジェクトが含まれます。

NOTE: 事前に下記の SDK のダウンロードが必要です

* Android SDK Build-Tools
* NDK
* CMake

### proj.ios.cmake

iOS 用の CMake プロジェクトです。

`script` ディレクトリのスクリプトを実行すると `build` ディレクトリに CMake 成果物が生成されます

| スクリプト名 | 生成物 |
| --- | --- |
| `proj_xcode` | Xcode プロジェクト |

CMake のツールチェーンとして [ios-cmake] を使用しています。
[thirdParty](README.md#thirdParty) の項目を参照して事前にダウンロードを行なってください。

[ios-cmake]: https://github.com/leetal/ios-cmake

### proj.linux.cmake

Linux 用の CMake プロジェクトです。

`script` ディレクトリのスクリプトを実行すると `build` ディレクトリに CMake 成果物が生成されます

| スクリプト名 | 生成物 |
| --- | --- |
| `make_gcc` | 実行可能なアプリケーション |

追加ライブラリとして [GLEW] と [GLFW] を使用しています。
[thirdParty](README.md#thirdParty) の項目を参照して事前にダウンロードを行なってください。

### proj.mac.cmake

macOS 用の CMake プロジェクトです。

`script` ディレクトリのスクリプトを実行すると `build` ディレクトリに CMake 成果物が生成されます

| スクリプト名 | 生成物 |
| --- | --- |
| `make_xcode` | 実行可能なアプリケーション |
| `proj_xcode` | Xcode プロジェクト |

追加ライブラリとして [GLEW] と [GLFW] を使用しています。
[thirdParty](README.md#thirdParty) の項目を参照して事前にダウンロードを行なってください。

### proj.win.cmake

Windows 用の CMake プロジェクトです。

`script` ディレクトリのスクリプトを実行すると `build` ディレクトリに CMake 成果物が生成されます

| スクリプト名 | 生成物 |
| --- | --- |
| `nmake_msvcXXXX.bat` | 実行可能なアプリケーション |
| `proj_msvcXXXX.bat` | Visual Studio プロジェクト |

追加ライブラリとして [GLEW] と [GLFW] を使用しています。
[thirdParty](README.md#thirdParty) の項目を参照して事前にダウンロードを行なってください。

## thirdParty

サンプルプロジェクトで使用するサードパーティライブラリと自動展開スクリプトが含まれます。

### GLEW / GLFW のセットアップ

`script` ディレクトリ内のスクリプトを実行することで GLEW と GLFW のダウンロードを行います。

| プラットフォーム | スクリプト名 |
| --- | --- |
| Linux / macOS | `setup_glew_glfw` |
| Windows | `setup_glew_glfw.bat` |

スクリプト内の `GLEW_VERSION` 及び `GLFW_VERSION` を変更することで、ダウンロードするバージョンを変更できます。

NOTE: `Visual Studio 2013` をご利用の際、追加対応が必要となる場合がございます。
詳しくは [NOTICE](NOTICE.md) をご確認ください。

## ios-cmake のセットアップ

`script` ディレクトリ内の `setup_ios_cmake` を実行することで ios-cmake のダウンロードを行います。

スクリプト内の `IOS_CMAKE_VERSION` を変更することで、ダウンロードするバージョンを変更できます。

[GLEW]: https://github.com/nigels-com/glew
[GLFW]: https://github.com/glfw/glfw
[ios-cmake]: https://github.com/leetal/ios-cmake
[stb_image.h]: https://github.com/nothings/stb/blob/master/stb_image.h
