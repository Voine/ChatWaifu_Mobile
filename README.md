# ChatWaifu_Mobile
## 年轻人的第一个移动版老婆聊天器（雾

## 简介
Android 手机版的 ChatGPT 二次元聊天器，目前仍是一个相当简陋的 app。\
内置 Blue Archive 优香语音，Live 2D 为官方模型 Hiyori，会自动播放 ChatGPT 的回复，由于内置为日语模型，其他语言的播放效果可能相当一言难尽。
- 集成 GhatGPT + VITS + Live2D
- 接了翻译功能，如需则自行设置 appid 和 key，详见下文
- 目前暂时只是把这几个功能连了起来，Live2D 也是播放的随机表情。日志记录，表情驱动，情感分析什么的，后面有空再搞把...

## 关于 ChatGPT
内置了一个 private key，不过估计很快就会用完罢（悲\
需要更改 private key 请打开软件左上角菜单，填入自己的 key，点击确认即可，本地会缓存。\
OpenAI Key [地址](https://platform.openai.com/account/api-keys)

## 关于移动端 VITS
详见[这里](https://github.com/weirdseed/Vits-Android-ncnn) 

## 关于 Live2D
详见[这里](https://docs.live2d.com/cubism-sdk-manual/top/)

## 关于模型替换
### VITS 模型
目前暂时没加本地读取，有需要的话可自行换掉 VITS/src/main/assets/model/yuuka 下的内容，移动端无法使用 pytorch 原始模型，需要转换成 ncnn，具体如何转换请参考上述 VITS 详情连接

### Live2D 模型
如果需要的话需自行更改 Live2D/src/assets/Hiyori/ 下的内容，暂未添加本地读取功能

## 关于翻译
设置后会默认将 GPT 输出的语言翻译成日文交给 VITS，否则内置的优香模型说别的语言可能基本不出声... \
接了 baidu 翻译，可以免费申请一个开发者账号 [这里](https://api.fanyi.baidu.com/) ，将申请到的 appid 和 密钥填到菜单栏 translate setting 里应该就行了。

### 示例图
<img src="https://user-images.githubusercontent.com/30189805/219956344-bdaca0c6-8cc4-474a-9c6b-f0dae76dffbe.png" width="256"><img src="https://user-images.githubusercontent.com/30189805/219956378-1c87a8f6-fa38-4cc5-a6de-59da2d4519b8.png" width="256">

# 免责声明
  **1、模型禁止商用！**
  
  **2、不可将本软件用作任何非法用途，后果自负**

# 鸣谢
感谢 weirdseed 大佬实现的 Android 版 VITS !
