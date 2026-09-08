# Tennis Analyzer Android

面向网球爱好者的 Android 分析应用。目前包含视频动作分析、比赛数据记录、历史训练，以及固定机位下的球场标定和球速估算原型。

## 使用说明

完整的中文操作指南见：[使用说明](docs/使用说明.md)。

## 当前能力

- 导入或拍摄训练视频
- 视频播放、慢放、逐帧查看和关键帧收藏
- 比赛比分、回合与失误记录
- 本地训练记录
- 通用球场关键点模型
- 底线后方手机/三脚架低机位模型
- 自动识别四个单打场地角点，并允许人工修正
- 根据两个网球位置和时间差进行球速估算

自动网球跟踪尚未完成，目前球速测算仍需要人工点击网球起点和终点。

## 环境

- Android Studio
- JDK 11 或更高版本
- Android SDK 36
- 最低 Android 版本：Android 7.0（API 24）

首次打开后，在 Android Studio 中完成 Gradle 同步即可运行。`local.properties`、构建缓存和 APK 不进入版本库。

## 模型

应用在 `app/src/main/assets` 中保留两套 FP32 LiteRT/TFLite 模型：

- `court_keypoints_fp32.tflite`：通用/较高机位
- `court_keypoints_low_camera_v1_fp32.tflite`：底线后方低机位

低机位模型目前属于候选版本，已经通过 Android 虚拟机和 Redmi K40 的设备测试，仍需使用更多真实球场视频验证。

## 测试

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

连接设备后可运行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

设备测试会验证两份模型的文件完整性、张量规格、低机位标注帧精度，以及未参与训练视频的多帧识别。
