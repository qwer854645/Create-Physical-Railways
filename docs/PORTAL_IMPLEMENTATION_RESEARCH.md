# Sable 跨维度实现调查

日期：2026-11-02。用于物理列车跨维度功能设计，尚未集成或实机验证。拆解、车站组装与车站时刻表交接已由用户排除，不作为实现前提。

## 检查依据

静态检查了三个公开仓库和以下四个 CurseForge 发布 jar。jar 仅放在工作区研究目录，未装入模组目录、未执行，也未提交第三方文件。Java 字节码用 JDK 21 javap 检查；没有以开发分支功能代替已发布版本的能力。

- [AeroWarptics 1.3.0，文件 8787426](https://www.curseforge.com/minecraft/mc-mods/create-aerowarptics/files/8787426)。开发源码另取 ICEconchy/Aerowarptics 的 `370ec6cc852f9aac3b20f6606e238135cdc08505`，属性版本 1.4.0，提交日期 2026-10-31。
- [Dimensional Sable 1.1.0 NeoForge，文件 9284116](https://www.curseforge.com/minecraft/mc-mods/dimensional-sable/files/9284116)。源码 hollow-egg/Dimensional-Sable `1ebd3fdb43e61e33a26fab2243e7380ddb3da306`，提交日期 2026-10-24，构建依赖 Sable 2.0.6。
- [AeroPortals 1.3.6，文件 9281419](https://www.curseforge.com/minecraft/mc-mods/create-aeroportals/files/9281419)。公开源码 breakinblocks/AeroPortals `7a1455065e3101f2d499e7d849f92afa54cfb94b`，属性版本仍为 1.3.4；相关迁移、日志、API 和绳索修复路径另查 1.3.6 字节码。
- [Immersive Aeronautics 1.1.4，文件 8586854](https://www.curseforge.com/minecraft/mc-mods/immersive-aeronautics/files/8586854)。所给项目页未见作者源码链接，因此只对该发布包做静态检查，未把其他同名项目当作它的源码。文件 SHA-1：`fa6390f64e172d83116b04ce22f18d7359286084`。

## AeroWarptics：同维度跃迁与可选跨维度桥接

发布版 1.3.0 的 `CrossDimensionWarp` 持有可由外部注册的 `Handler`。`isSupported` 同时要求 handler 非空及配置启用；配置说明明确写着需要扩展注册 handler。检查整个 jar 的 `setHandler` 常量引用，仅有此类和配置说明，没有找到随包注册后端的调用。因此它不是一个安装后即可独立完成跨维度迁移的现成底座。

开发分支已经有另一套实现：`CrossDimensionWarp` 检查目标物理存储网格、空间和连接限制，通过 `DimensionalSableBridge` 的 MethodHandle 调用外部迁移。失败时尝试移除本次在目标维度新出现的副本；另恢复自身船员、驾驶设备、旋转轴承及自有数据。`DimensionChangeTracking` 在玩家换维度时提前移除旧世界跟踪，避免迟到的旧删除包影响新世界同一物理存储槽位的车体。

重要版本边界：此开发桥查找的是 `dev.egg.SubLevelWarper.WarpSubLevel(ServerSubLevel, Level, Vector3d)`，而本次检查的 Dimensional Sable 1.1.0 发布包暴露 `dev.egg.dimensionalsable.SubLevelWarper.warpConnectedSubLevels(... ServerLevel ...)`。类名、大小写及参数类型均不匹配。不能认为“两个模组都安装”就能使这版桥接生效。

可参考的是可选依赖隔离、传送前后恢复自有状态、及时清理客户端跟踪。开发分支的同维度 Rift Gate 限制及原版列车 RailGates 支持，不能直接解释为我们的物理编组跨维度已兼容。

来源：[桥接源码](https://github.com/ICEconchy/Aerowarptics/blob/370ec6cc852f9aac3b20f6606e238135cdc08505/src/main/java/uk/co/iceconchy/aerowarptics/compat/dimensionalsable/DimensionalSableBridge.java)、[跨维度协调](https://github.com/ICEconchy/Aerowarptics/blob/370ec6cc852f9aac3b20f6606e238135cdc08505/src/main/java/uk/co/iceconchy/aerowarptics/warp/CrossDimensionWarp.java)。

## Dimensional Sable：共享存储槽位与整组重建

`getSubLevelGroup` 遍历相交物理结构和 `BlockEntitySubLevelActor.sable$getConnectionDependencies` 报告的依赖。公开重载也允许调用方明确提供整组成员，而非完全依赖自动发现。

它为各维度的物理存储网格加入共享占用表，使新分配的槽位在不同维度之间尽量不重用。这样迁移时可保留原有内部方块坐标，减少因重新分配坐标导致的方块实体引用损坏。这是对 Sable 分配及存储规则的修改，不是普通实体 teleport。

迁移顺序：以主刚体为参考计算整组的相对位置；对位置、姿态、线速度和角速度应用同一旋转和平移；`SubLevelSerializer.toData` 保存并修改数据；在目标世界 `fullyLoad` 全部成员；再迁移附近实体、恢复乘坐关系并删除源成员。当前代码沿用序列化 UUID，不能用旧版“总会产生新 UUID”的描述概括此版本。

高度差通过保存/加载区段偏移处理。方块实体加载时还会递归处理 NBT 中三元素 double 列表：不在内部存储网格内的列表按世界坐标变换。这是一种按数据形状识别坐标的策略，不能证明任意第三方三维向量都有正确语义。

边界：自动相交不等于属于同一列车；外部世界连接仍需预检。仓库提示旧车体的槽位可能不满足新规则。加载异常清理主要针对指定异常路径，代码不是完整的持久化迁移事务，不能据此承诺任意失败或断电时原子恢复。

来源：[整组迁移](https://github.com/hollow-egg/Dimensional-Sable/blob/1ebd3fdb43e61e33a26fab2243e7380ddb3da306/common/src/main/java/dev/egg/dimensionalsable/SubLevelWarper.java)、[共享槽位接入](https://github.com/hollow-egg/Dimensional-Sable/blob/1ebd3fdb43e61e33a26fab2243e7380ddb3da306/common/src/main/java/dev/egg/dimensionalsable/mixin/SubLevelContainerMixin.java)、[NBT 变换](https://github.com/hollow-egg/Dimensional-Sable/blob/1ebd3fdb43e61e33a26fab2243e7380ddb3da306/common/src/main/java/dev/egg/dimensionalsable/utils/BlockEntityTransformer.java)。

## AeroPortals：检测、整组事务、恢复日志与扩展接口

`PortalTeleport` 检测门户及冷却状态，使用 Sable 的加载依赖链，并额外纳入 Simulated 绳索关联成员。落点按飞船方式检查，必要时整组向上避障；随后记录乘客相对位置并生成迁移计划。

`SableBridge.moveGroup` 在修改前检查整组可迁移性、捕获扩展状态并序列化成员，写恢复日志后删除源成员，再加载目标成员。它能够在目标槽位改变时生成内部坐标偏移；全部目标成员存在后才重放多物体连接状态，失败路径尝试删除目标副本并恢复源成员，未完成恢复时保留日志。此流程与 Dimensional Sable 的“先创建所有目标、后删源”顺序不同。

扩展接口包括可取消的 `SubLevelPreTransferEvent`、带逐成员 `PlotMove` 和坐标重映射的 `SubLevelTransferEvent`，以及 `TransferCarrier` 的 capture/replay/validateGroup/serialize 等方法。对我们而言，持久化自有连接及恢复需要使用完整迁移生命周期，不能仅依赖成功后的通知。

1.3.6 发布包已经含有 `SimulatedRopeCompat` 和 `SimulatedRopeFixup`。因此“所有绳索跨维度都会断”不适用于该实现；但这也不证明我们的自建转向架关节和车钩记录会自动恢复。其乘客补同步、区块短期加载、延迟速度恢复及重复传送抑制，可作为功能设计参考。

飞船的向上避障落点不适合导轨列车。铁路方案应验证完整出口轨道、方向及占用，不能在事件里只指定目标点，然后默认后续避障不会再改变落点。

来源：[迁移协调](https://github.com/breakinblocks/AeroPortals/blob/7a1455065e3101f2d499e7d849f92afa54cfb94b/src/main/java/com/breakinblocks/aeroportals/portal/PortalTeleport.java)、[整组底层](https://github.com/breakinblocks/AeroPortals/blob/7a1455065e3101f2d499e7d849f92afa54cfb94b/src/main/java/com/breakinblocks/aeroportals/portal/SableBridge.java)、[扩展状态接口](https://github.com/breakinblocks/AeroPortals/blob/7a1455065e3101f2d499e7d849f92afa54cfb94b/src/main/java/com/breakinblocks/aeroportals/api/TransferCarrier.java)。以上源码属于已注明的开发提交；发布包检查仅证实本节列出的相应路径存在，没有以此声称两个版本逐字节相同。

## Immersive Aeronautics：整刚体迁移加跨门世界同步和裁切

发布包的 `IPShipPortalTraversalImpl.considerShip` 检查单个物理结构与 Portal，使用 `Portal.transformPoint` 计算落点，必要时应用门户旋转，随后调用 `IPSableBridge.moveThroughPortal`。后者保存结构和恢复日志、删除源结构、在目标世界重新分配/加载内部存储区域，并处理坐标偏移、动力数据重建及失败恢复。它依然是整刚体换维度，不是把一个物理求解器中的刚体切成两半。

`IPShipRiderCarry` 捕获并重放乘客及座椅关系，支持延迟重坐；`IPShipThroughPortalSync`、`IPSablePortalSyncClient` 及 Sable 渲染相关 Mixin 处理门户另一侧的世界、物理结构同步、跟踪、可见性和裁切。另有 Veil shader clipping 兼容。画面连续性依赖这些额外的客户端路径。

检查的主迁移路径以单个 ServerSubLevel 为单位，未见按本模组列车编组收集全部转向架与车厢的步骤，不能直接当作整列迁移契约；也未验证通用连接组的事务恢复。项目说明对 Sodium/Iris 版本及其他渲染模组有明确限制。跨门可见或显示裁切不能证明同一刚体在两维度同时参与碰撞、支撑和牵引。

来源：[作者项目说明](https://www.curseforge.com/minecraft/mc-mods/immersive-aeronautics)与上述 1.1.4 发布包字节码。本节对内部流程的判断属于本次静态分析，未进行游戏内穿门、碰撞或乘客验收。

## 对 Guided Railways 的含义

本项目的 MountEntity 和 CouplerEntity 并未实现上述 BlockEntitySubLevelActor 依赖接口，相关底层关节和编组关系由自己的运行系统管理。因此不能指望自动依赖遍历稳定找齐整列；相交包围盒既可能漏掉下部转向架，也可能带上旁边的独立车体。应显式枚举物理编组成员，并拒绝带有未纳入组的外部连接。

现有 MountBlock/CouplerBlock 有方块移动回调，但序列化整个物理结构再加载不等同于逐块移动，不能默认这些回调会收到通知。需要单独恢复安装座到下部刚体的关系、车钩端点、存储中的维度与坐标、导向轨段及旧 capability 失效。

现有 TrackNetwork 多处明确跳过跨维度轨道边。仅添加传送能力不会让自动驾驶选择另一维度车站。需要将 Create 门户轨道边解释为受控迁移边界：出口整列空间及信号预约确认后再迁移，到达后重新绑定轨道和身份，驾驶台保留时刻表物品与进度。

建议先建立自己的列车迁移协调入口，再验证一个可选迁移后端。Dimensional Sable 可提供旋转与显式成员列表；AeroPortals 的整组恢复及扩展状态接口更丰富。兼容性验证前不定论二者谁更适合，也不让多个模组同时触发同一列车的传送。Immersive Aeronautics 的跨门渲染可作为独立的可选兼容，首版不承诺半节车体跨维度受力。

源码借鉴和直接复制需区分：AeroPortals 的 CurseForge 标签写 MIT，但本次检查的 1.3.6 jar 元数据及公开源码许可证实际为 PolyForm Shield 与 PolyForm Perimeter 组合；不能据页面标签把源码当作 MIT 复制。本文只记录实现思路和公开接口，不复制实现进项目。

本次没有修改玩法代码、安装依赖、运行 Minecraft 客户端或测试服务端，也没有证明上述模组可与当前整合环境同时运行。
