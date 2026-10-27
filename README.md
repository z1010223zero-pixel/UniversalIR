# 万能红外遥控（UniversalIR）

安卓万能红外遥控 app：内置离线码库控电视/机顶盒/灯/风扇/空调/投影/音响，支持「小爱同学」「小艺」等系统语音助手通过深链接调用发射。

## 功能

- **红外发射**：`ConsumerIrManager` 公开 API，小米/Redmi、华为/荣耀带红外机型均可用；逐帧载频/2s 上限校验、SIRC/Pioneer 自动连发。
- **协议覆盖**：NEC、NECext、RC5、RC5X、SIRC-12/15/20、Samsung32、Kaseikyo、RC6、RCA、Pioneer 共 12 种，内置码库中的信号全部可发射；RC5/RC5X/RC6 发送时按「设备 + 按键」翻转 toggle 位，模拟真实按键。
- **内置离线码库**：Flipper-IRDB（CC0-1.0）+ SmartIR climate（MIT），580 个码表 / 6544 条信号（534 Flipper + 46 SmartIR），gzip 后约 3.1MB；「试码匹配」流程绑定主开关。
- **空调全状态**：SmartIR 码表为「模式 × 风速 × 温度」完整矩阵（Broadlink base64 → 微秒换算用真值 tick 2^-15s），面板直接查表整帧重发。
- **语音/自动化入口**：
  - 深链接 `uniir://device/{id|名称}/on|off|toggle|key/{按键}`、`uniir://ac/{id|名称}?power=on&temp=26&mode=cool&fan=low`
  - 桌面动态快捷方式（每设备开机动作）
  - Tasker/MacroDroid 广播 `com.zero.universalir.SEND`（extras：device/key/power/mode/fan/temp）
  - App 内「语音接入向导」生成小爱训练计划话术+链接、小艺使用指引
- **配置备份**：设置页导出/导入 JSON。

语音接入速查：
- 小米：小爱同学 App → 技能中心 → 小爱训练 → 话术「客厅灯打开」→ 执行「打开网址」→ 粘贴 `uniir://...` 链接（每动作一条训练）。
- 华为/荣耀：「小艺，打开万能红外遥控」+ 面板点击；HarmonyOS 6 可用「小艺学技能」录制教学。

## 构建

要求 JDK 17+、Android SDK（compileSdk 35）。

```bash
# 生成/更新内置码库（需要网络，产物提交到 assets 后构建不再依赖网络）
python3 tools/fetch_codes.py

./gradlew assembleDebug        # 或用 Android Studio 直接打开
./gradlew testDebugUnitTest    # 单元测试（60 个：协议黄金向量、解析器、码库、路由）
```

`gradle-wrapper.properties` 的发行包地址已指向腾讯镜像；国内网络可直接 `./gradlew`。

## 项目结构

```
app/src/main/java/com/zero/universalir/
├── core/ir        IrSignal 统一码模型、IrLogic（2s 上限/载频选择）、IrTransmitter、RcToggleMemory
├── core/codec     NEC/NECext/RC5/RC5X/SIRC12/15/20/Samsung32/Kaseikyo/RC6/RCA/Pioneer
│                  编码器、Flipper .ir 解析、SmartIR climate 解析、Broadlink base64 → 微秒转换
├── core/model     DeviceType
├── data/codes     CodeLibrary（assets 索引 + 懒加载）
├── data/db        Room（devices / buttons）
├── feature/*      首页、添加向导（含自动轮播试码）、遥控面板（通用/空调）、语音向导、设置
├── voice          DeepLinkRouter、VoiceExecutor、深链接/广播入口、快捷方式
└── ui             主题与导航
tools/fetch_codes.py   码库拉取打包脚本（GitHub trees API + raw，缓存续传）
```

## 硬件事实与限制（调研结论，2026-10）

- **小米/Redmi 红外口只有发射 LED，物理上无法学习**（官方学习功能在米家万能遥控器等外设上）。
- **华为/荣耀**红外口有接收硬件，但学习封在系统应用内、无公开 API，第三方 app 不可复用。
- `transmit()` 失败静默返回：UI 以 Snackbar 提示"已发送"，实际以设备响应为准。
- HarmonyOS NEXT（纯血鸿蒙）不能安装 APK。

## 后续路线

- 学习功能：Broadlink 网关直连（纯局域网 UDP+AES，python-broadlink 0.19 协议可移植）或 USB OTG 学习器（Ocrustar 协议开源，约 ¥50）。`IrSignal`/`CodeLibrary` 已预留扩展位。
- 上架预留：华为 Intents Kit / 小米意图框架的执行端即 `DeepLinkRouter`，上架后仅需平台侧注册意图。
