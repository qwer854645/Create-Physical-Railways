# 服务端信号集成验证

日期：2026-10-21 · 产品版本：0.2.24

用户反馈本轮其他测试通过，信号混跑尚未人工测试，并授权自行运行服务端测试。本轮以独立 GameTest 服务端补充集成验证，不改变以后默认不自动启动客户端/服务器的约定。

## 执行结果

两轮均成功运行一个必需 GameTest，包含相同的 14 个断言：

- 基础环境：NeoForge 21.1.264、Create 6.0.10、Sable 2.0.6 及其运行依赖，通过，进程正常退出
- 当前开发模组组合：再加入 libs 中的 Railway 0.3.0-beta.2、BnB 1.0.8、CEE 1.1.4、Aeronautics/Simulated 1.3.2、Automated Logistics 0.7.0、Hose Connectors 0.2.2、Xaero 开发依赖，通过，进程正常退出

日志成功标记为 `MIXED_SIGNAL_API_PASS checks=14` 和 `All 1 required tests passed`。基础轮日志位于 build-signal-server-run.log，完整组合位于 build-signal-server-full-mods.log；这些本机日志不提交。完整组合中有 Railway 为未安装的其他模组材质生成的战利品表错误，未阻止测试完成；不能称启动日志完全无错误。

测试在真实 NeoForge 注册表、Mixin 与服务端环境中创建原生 Train 和已有的 PhysicalTrainProxy 类型，调用真实 SignalEdgeGroup 查询。物理占用账本的输入和内部身份映射由测试夹具设置，结束后按原对象恢复；没有更改生产代码以绕过信号判断。

覆盖：原版列车识别物理占用、物理身份排除自身、真实原版占用仍阻挡物理列车、原版占用拒绝物理预约、跨两段的车尾占用、尾部释放、交叉冲突、物理未来预约、自身预约排除、相向物理请求互斥、原生清空集合后的 earlyTick 重发、清理自身且保留另一列车，以及清除查询字段。

## 明确未覆盖

这不是两列真实组装车辆沿轨运行的端到端测试。测试没有真实车体、轮轴、轨道图或时刻表导航，不验证物理覆盖计算、信号安装传播、链式出口寻路、实际制动距离、高速越界、长列车几何以及存档重载。不能将上述通过结果写成“实车信号混跑全部通过”。这些仍按 NATIVE_TRAIN_IDENTITY.md 中的混跑验收场景测试，或者另行建立自动建轨/组装/驾驶的实车测试夹具。

## 复现与隔离

    gradlew.bat runSignalValidation -PsignalServerTests --offline

构建配置仅在显式启用该参数时加入独立的 guidedrailways_signal_validation 开发模组，源码和结构位于 src/signalValidation。默认 build、check 和客户端启动不会运行服务端测试，测试类与测试元数据不进入发行 jar。

服务端工作目录是 build/signal-validation-run，不使用 run/saves，不打开或复制用户存档。测试完成后自动保存并停止；复现时此目录属于专用测试工作区。连接器、时刻表和原版列车的真实游戏存档保持原样。
