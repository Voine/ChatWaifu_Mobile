# ChatWaifu_Mobile
## 年轻人的第一个移动版老婆聊天器（雾
## 简介
Android 手机版的 ChatGPT 二次元聊天器，目前仍是一个相当简陋的 app。\
目前内置2个模型，会自动播放 ChatGPT 的回复，由于内置为日语模型，其他语言的播放效果可能相当一言难尽。\
\
**模型1: 语音来自 Blue Archive 优香，Live 2D 为官方模型 Hiyori。**\
**模型2: 语音来自命运石之门牧濑红莉栖，Live2D 为 Amadeus.**\
**模型3：语音和模型均来自 ANIPLEX 的作品 ATRI -My Dear Moments-**

- 集成 GhatGPT + VITS + Live2D
- 接了翻译功能，如需则自行设置 appid 和 key，详见下文
- 目前暂时只是把这几个功能连了起来，Live2D 也是播放的随机表情。日志记录，表情驱动，情感分析什么的，后面有空再搞把...
## 关于 ChatGPT
OpenAI Key [地址](https://platform.openai.com/account/api-keys)，将其输入登录页面的 Chat Key 部分就行，本地会缓存。
## 关于移动端 VITS
详见[这里](https://github.com/weirdseed/Vits-Android-ncnn)
## 关于 Live2D
详见[这里](https://docs.live2d.com/cubism-sdk-manual/top/)
## 关于模型替换
### VITS 模型
首先，模型需要参照[这里](https://github.com/weirdseed/vits-ncnn-convert-tool)，转成 ncnn 的版本，成功后会生成一个 config.json 以及很多的 bin 文件。将它们统一放在手机文件管理内 chatwaifu/vits/ 目录下，放完后整个目录是这样的：
```
文件管理：
├─chatwaifu
     ├─ vits
         ├─ yourModel
              ├─ config.json
              ├─ xxxx.bin
              ├─ xxxx.bin
              ├─ .....
```
### Live2D 模型
支持官方标准的 Live2D 格式，需要将它们放在手机文件管理内 chatwaifu/live2d/ 目录下，放完后整个目录是这样的：
```
文件管理：
├─chatwaifu
     ├─ live2d
         ├─ yourModel
              ├─ xxx.model3.json
              ├─ xxx.moc3
              ├─ xxx.exp3.json
              ├─ .....
```
### 注意
live2d 模型的名字与需要与 vits 模型的目录名一致
## 关于翻译
设置后会默认将 GPT 输出的语言翻译成日文交给 VITS，否则内置的优香模型说别的语言可能基本不出声... \
接了 baidu 翻译，可以免费申请一个开发者账号 [这里](https://api.fanyi.baidu.com/) ，将申请到的 appid 和 密钥填到登录页的第二第三栏，或者在左上角菜单里选取 setting ，进行更改就行了
## 关于角色设定
目前 Hiyori 和 Amadeus 都有内置的设定，外部导入的模型可将设定填写到 Setting 页面的 External Setting 部分
## 示例图
<img width="256" src="https://user-images.githubusercontent.com/30189805/221414807-11e1ca0e-4046-4702-a730-b80dbf8c4102.png"><img width="256" src="https://user-images.githubusercontent.com/30189805/222944944-54cc6685-4629-4970-ab43-e532b623e9b7.png">
<img src="https://user-images.githubusercontent.com/30189805/221416029-7247c2eb-3973-49f4-bdb4-7a2cf81ae74a.png" width="256">


# 免责声明
  **1、模型禁止商用！**
  
  **2、不可将本软件用作任何非法用途，后果自负**

# 鸣谢
感谢 @weirdseed 大佬实现的 Android 版 VITS !
