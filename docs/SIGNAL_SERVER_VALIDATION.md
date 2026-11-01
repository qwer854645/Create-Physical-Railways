# 服务端信号集成验证

日期：2026-10-21 · 产品版本：0.2.24

用户反馈本轮其他测试通过，信号混跑尚未人工测试，并授权自行运行服务端测试。本轮以独立 GameTest 服务端补充集成验证，不改变以后默认不自动启动客户端/服务器的约定。

## 信号接口验证

### 2026-11-02 同步与分批索引优化复验

身份协议 3 与分批轨道索引实现后，运行
`gradlew.bat build runSignalValidation -PsignalServerTests --offline`。
结果为 `BUILD SUCCESSFUL in 1m 42s`，125 个任务、`All 5 required tests passed`；
服务器正常退出。本机日志为 `build-sync-index-final-20261102.log`，不提交。

新增 `TrackIndexServerTests` 使用实际 Create 轨道图：1024 轨段初始后台构建用 13 tick 完成，
过程中没有发布部分图，已开始构建的边在替换后重新扫描；无关图路径对象保持一致。
绕过修改通知增加的连接由周期审计发现（该次距大图发布 198 tick），删除边在重建前即被
`live()` 拒绝，同 UUID 图对象替换及图移除后的查询也通过。显式完整刷新、关键导航查询和
后台检查的不同完成语义均保留；后台预算不是首次加载、交互刷新或关键导向查询的硬耗时上限。

原有 14 项信号接口、48 项岔口图规则、34 次真实驾驶台读数与两组实车停车/恢复测试共同通过。
离线 `TrainSnapshotSyncCheck` 通过 91 项，包含真实二进制 NBT、基线登录、递归字段和车厢列表
差分、删除/类型变化、版本不匹配、完整恢复、心跳和回退开关。较大静态数据样本的完整 NBT
为 14,156 字节，移动增量 279 字节；该样本不能代替实际多人带宽、CPU、GC 或 FPS 测量。
服务端场景没有连接真实客户端，因此不把这些结果表述成真实客户端重连与画面同步已验收。

两轮均成功运行一个必需 GameTest，包含相同的 14 个断言：

- 基础环境：NeoForge 21.1.264、Create 6.0.10、Sable 2.0.6 及其运行依赖，通过，进程正常退出
- 当前开发模组组合：再加入 libs 中的 Railway 0.3.0-beta.2、BnB 1.0.8、CEE 1.1.4、Aeronautics/Simulated 1.3.2、Automated Logistics 0.7.0、Hose Connectors 0.2.2、Xaero 开发依赖，通过，进程正常退出

日志成功标记为 `MIXED_SIGNAL_API_PASS checks=14` 和 `All 1 required tests passed`。基础轮日志位于 build-signal-server-run.log，完整组合位于 build-signal-server-full-mods.log；这些本机日志不提交。完整组合中有 Railway 为未安装的其他模组材质生成的战利品表错误，未阻止测试完成；不能称启动日志完全无错误。

测试在真实 NeoForge 注册表、Mixin 与服务端环境中创建原生 Train 和已有的 PhysicalTrainProxy 类型，调用真实 SignalEdgeGroup 查询。物理占用账本的输入和内部身份映射由测试夹具设置，结束后按原对象恢复；没有更改生产代码以绕过信号判断。

覆盖：原版列车识别物理占用、物理身份排除自身、真实原版占用仍阻挡物理列车、原版占用拒绝物理预约、跨两段的车尾占用、尾部释放、交叉冲突、物理未来预约、自身预约排除、相向物理请求互斥、原生清空集合后的 earlyTick 重发、清理自身且保留另一列车，以及清除查询字段。

## 真实车辆追踪验证

### 2026-11-02 驾驶台读数与岔口图验证

对 `14a9ea1` 新增的显示快照扩充既有实车测试：每 20 tick 从真实驾驶台取得 `getUpdateTag`，核对绑定、实际速度、当前时刻表状态、目的地、行程项与有效剩余距离，再由独立驾驶台实例执行客户端使用的 `loadWithComponents`，确认读数一致；同时核对磁盘保存不包含 `VisualReadout`，重新载入磁盘数据会清空显示快照。未注入物理速度或显示状态。

两组分别获得 `CONSOLE_READOUT_PASS nativeLeader=false samples=15 moving=true signalWait=false` 和 `nativeLeader=true samples=19 moving=true signalWait=true`。后者确实覆盖物理后车等待红灯，前者不要求原版后车的等待状态出现在物理前车上。此验证覆盖实际实体序列化与读取，不是有连接客户端接收数据包或字体排版的验证，也不单独证明同步频率。

新增 `JunctionServerTests` 在真实服务端注册独立 Create 轨道图，调用生产 `TrackNetwork.follow` 和 `RouteMemory`。8 轮交替选取两个出口，每轮重复拒绝 3 次，再允许通行，并以相反提议检查后轴是否跟随前轴；每轮整列通过后检查通行记录已释放。结果为 `JUNCTION_GRAPH_PASS laps=8 checks=48`。图、节点和通行许可由夹具提供，没有铺设物理岔轨、运行车辆、执行真实时刻表规划或模拟两列车竞争预约，不能称实车岔口往返通过。

复现：`gradlew.bat runSignalValidation -PsignalServerTests checkGoggleInformation checkLanguage --offline`。结果为 `All 4 required tests passed`、`BUILD SUCCESSFUL in 40s`；护目镜离线检查与语言检查（4044 项）也通过。本机日志为 `build-console-junction-validation-20261102.log`，不提交。测试世界仍使用独立构建目录，服务端已正常退出；日志中仍存在前述可选 Railway 战利品表错误。

新增 StraightMixedRunTests，两组场景使用真实的 181 格 Create 直轨、两座车站和 60 / 100 / 140 格处的普通信号。原版列车由车站组装，带真实座椅、村民乘务员和前向控制器；物理列车由转向架正常放置入口生成，使用 Sable 刚体、32 RPM 创造马达与驾驶台。两类车辆均由实际时刻表开往 20 格处的终点，测试不注入速度、物理信号占用或预约。

- 原版前车、物理后车：前车约在 81 格停车，后车从约 130.5 格起步，驶至约 102.6 格停稳；前车启动并清空 60–100 格区段后，后车恢复行驶并越过 100 格信号
- 物理前车、原版后车：前车约在 80.5 格停车，后车从约 121 格起步，驶至约 101 格停稳；前车清空区段后，原版后车恢复行驶并通过信号

两组并行运行在互不连接的铁路图上。测试要求后车先实际前进至少 3 格，再以低于 0.1 格/秒的速度持续停车至少 20 tick，核对红灯和前车的真实区段成员；前车清空区段前后车不得越过信号，恢复后两车持续保持大于 5 格的参考位置间距。该间距是测试安全断言，不是车钩间距或通用防撞参数。物理车不得脱轨，原版车不得进入 derailed 状态。

当前完整开发模组组合下，14 项接口断言与两组实车测试共同通过，日志标记为 `MIXED_SIGNAL_API_PASS checks=14`、两条 `MOVING_MIXED_PASS` 和 `All 3 required tests passed`。最终日志保存在本机 build-moving-signal-final.log，不提交构建日志。

反射仅用于设置通常由放置界面写入的轨道目标、调用包内正常放置/安装入口和读取诊断字段；没有绕过生产导航和信号算法。测试场景位于独立世界靠近原点处，避免随机 GameTest 远坐标导致 Rapier 浮点位置积分失去精度。每组结束移除测试车辆并释放自身强制加载的区块。

## 明确未覆盖

### 2026-11-02 最新代码复验

用户本轮明确授权自行启动测试服务端。在 `c9dd93c`（含岔口旧记录过期、短暂丢失轨道时保留轴轨道记录、被拒绝通行时保留停车限制）上，使用下述既有入口和完整开发模组组合重新运行。独立目录为 `build/signal-validation-run`，没有读取或修改客户端存档。

- `MIXED_SIGNAL_API_PASS checks=14`：通过。
- 原版前车、物理后车：`MOVING_MIXED_PASS nativeLeader=true tick=381`，参考位置最小间距约 21.58 格。
- 物理前车、原版后车：`MOVING_MIXED_PASS nativeLeader=false tick=315`，参考位置最小间距 20.5 格。
- `All 3 required tests passed`，服务端正常退出，`BUILD SUCCESSFUL in 38s`。

本机日志：`build-server-validation-20261102.log`，不提交。启动中仍有 Railway 可选材质对应的战利品表注册错误，未阻止场景完成。此次仅复验既有直轨测试，不能证明新岔口修复已通过服务端实车验证；多岔口往返、两车共用道岔及保存重载仍需独立场景。

上述实车结果只证明低速、单车厢、直线、普通信号下的两个追踪方向。没有实测相向会车、交叉口与链式信号出口预约、弯道、高速制动、长编组车尾几何、卸载重载以及客户端地图同步。不能将其写成“信号混跑全部通过”或“完全兼容所有铁路模组”。其余场景仍按 NATIVE_TRAIN_IDENTITY.md 的验收清单执行。

## 复现与隔离

    gradlew.bat runSignalValidation -PsignalServerTests --offline

构建配置仅在显式启用该参数时加入独立的 guidedrailways_signal_validation 开发模组，源码和结构位于 src/signalValidation。默认 build、check 和客户端启动不会运行服务端测试，测试类与测试元数据不进入发行 jar。

服务端工作目录是 build/signal-validation-run，不使用 run/saves，不打开或复制用户存档。每次显式运行前，prepareSignalValidationWorld 仅清理该构建目录下的 world 子目录，避免上一次保存的列车污染占用判断；任务会核对规范路径，不能指向用户世界。测试完成后自动保存并停止，日志保留。连接器、时刻表和原版列车的真实游戏存档保持原样。
