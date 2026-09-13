# ChatWaifu_Mobile

## Companion UI 开发交接

新会话先阅读 **[项目架构、当前进展与交接](docs/project-handoff.md)**。
Companion UI 的逐批施工记录、设备证据和剩余事项见
**[施工与跨机器交接文档](docs/companion-ui-construction-plan.md)** 第 10、12 节。
当前已完成 Phase 2.6，尚未进入 Phase 3。下方部分说明来自旧版项目，涉及当前架构、BV2、
角色身份和 Provider 的信息以交接文档为准。

## 年轻人的第一个移动版老婆聊天器（雾
## 简介
Android 手机版的 ChatGPT 二次元聊天器。\
目前内置如下模型，会自动播放 ChatGPT 的回复，由于内置为日语模型，其他语言的播放效果可能相当一言难尽, 可以替换增加本地模型。\
\
**模型1: 语音和模型均来自 Blue Archive 优香。**\
**模型2: 语音和模型均来自命运石之门牧濑红莉栖 - Amadeus 限定，β 世界线警告 :).**\
**模型3: 语音和模型均来自 ANIPLEX 的作品 ATRI -My Dear Moments- 的主角亚托莉**

- 语言大模型来自 GhatGPT
- 语音推理为客户端本地 VITS - ncnn
- 图形渲染基于 Native Live2D
- 语音输入识别为客户端本地 Sherpa - ncnn
- UI 框架为 Jetpack Compose
- 项目结构为 Retrofit + MVVM + LiveData/Flow + Room
- 多个 ncnn 库似乎是进程独享资源，使用 AIDL 做进程间通信
- 嘴形同步接入了 Native 版 meta - lipSync
- 接了 baidu 翻译功能，如需则自行设置 appid 和 key，不需要可在 setting 页面关闭翻译功能

## 关于 ChatGPT
OpenAI Key [地址](https://platform.openai.com/account/api-keys)，将其输入登录页面的 Chat Key 部分就行，本地会缓存。
## 关于移动端 VITS
详见[这里](https://github.com/weirdseed/Vits-Android-ncnn)
## 关于 Live2D
使用基于 Native C++ 的原生版本，详见[这里](https://docs.live2d.com/cubism-sdk-manual/top/)
## 关于语音识别输入
使用 Sherpa-ncnn ，详见[这里](https://github.com/k2-fsa/sherpa-ncnn) 
## 关于嘴形同步
接入了 Native 版 meta-lipSync，详见[这里](https://developer.oculus.com/documentation/native/audio-ovrlipsync-native/)\
*注：不过由于 LipSync 在实际使用中发现会有时长同步/映射等等问题，太过麻烦，目前只是播一个循环动画*

## 关于模型替换

把模型打成一个 **zip**，在左上角菜单进入 **ModelManager**，点右上角 ➕ 选中这个 zip 导入即可。
**zip 的文件名就是角色名**（`Yuuka.zip` → 角色叫 Yuuka）。

> 旧版本是把文件夹拖到手机存储的 `chatwaifu/live2d/`、`chatwaifu/vits/` 下。
> Android 11 之后应用已经拿不到任意路径的读取权限了（这类文件不是媒体文件，没有对应的运行时权限），
> 所以改成了导入制：导入后模型会解压到应用自己的目录，卸载时一并清理。

### zip 的结构

```
Yuuka.zip
├─ live2d/        ← Live2D 官方标准格式
│    ├─ Yuuka.model3.json
│    ├─ xxx.moc3
│    ├─ xxx.exp3.json
│    └─ .....
└─ vits/          ← 可选，不放就是「只有形象、不出声」的角色
     ├─ config.json
     ├─ xxxx.bin
     └─ .....
```

VITS 模型需要先参照[这里](https://github.com/weirdseed/vits-ncnn-convert-tool)转成 ncnn 版本，会生成一个 config.json 和一堆 bin 文件。

### 几个不用再操心的点

- **两个目录名不需要再保持一致**，`model3.json` 也**不需要**和角色名同名 —— 导入时会自动识别并记录
- 目录层级不必严格照上面来：多包一层文件夹（`Yuuka/live2d/...`）或者所有文件平铺在一层，都能正确识别
- 如果 VITS 模型是多人混合的，在 ModelManager 里展开该角色设置 Speaker ID，默认 0

### 导入失败的话

导入会先校验，失败时会直接告诉你原因：找不到 `*.model3.json`、`config.json` 不合法（比如 cleaner 不受支持）、
包里有多个 `*.model3.json` 不知道用哪个、角色名重复，等等。
## 关于翻译
设置后会默认将 GPT 输出的语言翻译成日文交给 VITS，否则内置的模型说别的语言可能基本不出声... \
接了 baidu 翻译，可以免费申请一个开发者账号 [这里](https://api.fanyi.baidu.com/) ，将申请到的 appid 和 密钥填到登录页的第二第三栏，或者在左上角菜单里选取 setting ，进行更改就行了
## 关于角色设定
内置的三个角色都自带设定，可以在 Setting 页面改。
导入的模型**每个角色有自己独立的设定**，在 ModelManager 里展开对应角色填写（以前所有外部模型共用一份设定）。
## 示例图

<img width="256" src="https://github-production-user-asset-6210df.s3.amazonaws.com/30189805/237924461-2015dd47-1904-4c37-b1c0-d56e5f5f55bb.png"><img width="256" src="https://github-production-user-asset-6210df.s3.amazonaws.com/30189805/237924775-470978ae-ae0d-4e6e-9bfe-f6bc48f5b884.png"><img width="256" src="https://github-production-user-asset-6210df.s3.amazonaws.com/30189805/237924898-86f72843-511f-40e2-a854-b19b0056c13f.png"><img width="256" src="https://github-production-user-asset-6210df.s3.amazonaws.com/30189805/237925004-c1d908f1-9c63-40c6-bd09-efa262f3ae5d.png"><img width="256" src="https://github-production-user-asset-6210df.s3.amazonaws.com/30189805/237925541-4af5a8e7-745f-4b85-a434-23fb9c16e0b0.png"><img width="256" src="https://github-production-user-asset-6210df.s3.amazonaws.com/30189805/237936171-e651f62d-2ad1-4225-a936-e2c2c2081416.png">

# 免责声明
  **1、模型禁止商用！**
  
  **2、不可将本软件用作任何非法用途，后果自负**

# 鸣谢
感谢 @weirdseed 大佬实现的 Android 版 VITS !

# 说明视频
https://www.bilibili.com/video/BV1w24y1K799/
