# 状态与缓存审计

本文件记录项目里每个**隐式状态机**的字段、合法组合，以及每个长生命周期缓存的过期策略。新增状态或缓存时必须更新这里，否则下一处同类缺陷就是从这里漏出去的。

背景：一周内修过三类同形缺陷——某状态只能经正常路径清除而没有超时（转向架交接、岔口通行记录）、多个布尔同时为真且优先级只存在于读代码的人脑子里（控制台）、手写缓存靠计矛盾次数逃逸（`repeat`）。因此凡是"多字段共同表达一个状态"或"静态表无过期"的地方，都要在此登记。

## 状态机

### 1. 控制台意图 `ConsoleIntent`

| 意图 | 条件 | 是否驱动列车 |
|---|---|---|
| `IDLE` | 未绑定，或无人接管 | 否 |
| `MANUAL` | 司机在座 | 是 |
| `ALIGN` | 自动对准开启 | 是 |
| `SCHEDULE` | 时刻表运行且被观察 | 否（由时刻表下令） |
| `OVERRIDE` | 时刻表存在但司机接管 | 是 |

- 派生字段：`bound`、`aligning`、`observing`、`active`、`schedule.running()`
- 断言：`ConsoleIntentCheck`（46 项，含全部 32 种标志组合都必须恰好得到一个意图）

### 2. 时刻表状态 `ScheduleController`

**尚未收敛，登记为待办。** 当前由 16 个整数状态码与 8 个布尔标志共同表达：

```
状态码: EMPTY PAUSED PLANNING TRAVELLING WAITING FINISHED UNSUPPORTED NO_ROUTE NO_POWER
        TRACK_MISSING CONFLICT CHANGED OVERSHOOT UNLOADED EMERGENCY SIGNAL_WAIT
标志:   running manualOverride waiting releaseBrakes visualDirty routeRetry resumePending mailMonitored
```

**阶段投影（已加入）**：`ScheduleStage` 把 16 个状态码映射为 `IDLE/COMPLETE/PLANNING/RUNNING/HOLDING/BLOCKED/WAITING` 七个阶段，`ScheduleStageCheck`（41 项）逐码断言，并规定：只有 `TRAVELLING` 允许列车按时刻表移动、`BLOCKED` 与"自愈型暂停"互斥、**未知状态码一律视为 BLOCKED**（不得当成健康）。目前它只是投影，存储与迁移未变；下一步才是把迁移收敛到阶段上。

已知需要明确的组合（目前没有任何守卫）：

| 组合 | 含义 | 谁来保证 |
|---|---|---|
| `status=WAITING` 且 `running=false` | 等待中却没在跑 | 无 |
| `running=false` 且 `resumePending=true` | 已停但仍有待恢复 | 无 |
| `manualOverride=true` 且 `status=SCHEDULE 系列` | 司机接管期间的显示 | 部分（`manual()`） |
| `routeRetry=true` | 请求重算路线 | 由 `AutomaticTrains` 消费并清除 |

**布尔分类（已加入断言）**：`ScheduleFlagAudit` 要求控制器里每个布尔字段都在清单中登记并归类，否则构建失败：

| 类别 | 字段 | 含义 |
|---|---|---|
| `STATE` | `running`、`manualOverride`、`waiting` | 属于"在做什么"，是收敛进阶段的候选 |
| `REQUEST` | `releaseBrakes`、`visualDirty`、`routeRetry`、`resumePending` | 一次性指令，被消费后清除 |
| `MONITOR` | `mailMonitored` | 观测簿记，不是列车状态 |

新增布尔字段必须同时更新该清单——这就是"不再有未审视的状态"的机械保证。

**`waiting` 的收敛证据**：`waiting` 是三个 `STATE` 标志中最先可以折叠的一个，但前提是它与 `stage()==WAITING` 从不矛盾。与其假设，`ScheduleController.auditWaitFlag()` 每秒比较一次，矛盾时记 `[ScheduleWaitDisagrees]`。日志安静一段时间后即可把读取点改为派生查询；出现矛盾则说明"等待中但状态码不是 WAITING"是合法状态，该字段应降级为 `REQUEST` 而不是合并。

### 3. 岔口通行 `RouteMemory`

- 每个节点一个**有序通行队列**，每根轴一个消费指针
- 通行有 `PASSAGE_TICKS = 200` 的生命周期，每节点最多 `MAX_PASSAGES = 64` 条
- 断言：`ConsistRouteCheck`（121 项）

### 4. 驾驶使能 `DriverConsoleEntity.enabled`

- 时刻表启动时关闭（`ScheduleController:474`），手动给档位时重新打开
- 与 `ConsoleIntent` 的关系：`enabled=false` 时意图不可能是 `MANUAL`

## 缓存与过期

2026-11-02 同步与索引优化：`TrainSnapshotSync` 随代理生命周期释放，每列仅保留上次发送的
私有完整基线、递增版本和不含版本元数据的 NBT 大小，100 tick 周期的完整心跳按 UUID 错峰；
增量累计更新完整大小，较大增量退回完整包。客户端接收生成一份私有结果，内部代理接管所有权，
不暴露可写基线；对外查询仍返回副本。信号帧仍独立。`TrackNetwork.Index` 按世界持有
轨道图槽位，200～219 tick 后启动分批审计，每刻最多 512 个节点/边/空间采样步骤并采用约
1 ms 的软预算；发布、图表登记和关键同步查询不受该软预算保证。空间单元只登记图槽位，
读取活动分片；过期引用另按每刻最多 512 项清理，避免长期保留旧图快照。移除图后立即
使活动分片失效，世界卸载仍释放整个索引。`TrackTopology` 的逐图计数使用弱键，避免跨世界保留图。

`SchedulePriority`、`SwitchBlockedReasons`、`ApproachRooms`、`BranchWatch` 按 `ServerLevel` 隔离，`TrainRuntime.beforeTick`
逐刻调用 `TickCache.tick`，未再次查询的旧身份也会清除；世界卸载时显式移除整张表。
时钟回退使未来记录失效。`TickCacheCheck` 覆盖首次查询、20/60 tick 日志间隔、
未查询键的过期和时钟回退；`JunctionStateCheck` 与 `TrainRuntimeCheck` 检查实际生命周期接线。
接近距离与岔口诊断不再使用跨存档的维度字符串键；同维度的不同存档实例不会共享数据。

道岔诊断由 `SteamRailways.DIAGNOSTIC_TICKS`（20 tick）限流，按世界、轨道图和节点隔离，
经可选兼容桥逐刻清理并在卸载时释放。排队诊断由 `SwitchReservations.REPORT_TICKS`
（60 tick）限流，缓存属于预约实例，不再使用全局静态节点表。

| 缓存 | 位置 | 上界 | 断言 |
|---|---|---|---|
| 道岔预约 | `SwitchReservations.HOLD_TICKS` | 40 tick | `SwitchReservationsCheck` |
| 时刻表优先级 | `SchedulePriority.LIFETIME` | 40 tick | `CacheHygieneCheck` |
| 接近限距 | `ApproachRooms.LIFETIME` | 10 tick | `CacheHygieneCheck` |
| 岔口诊断 | `BranchWatch.WINDOW` | 40 tick | `TrainRuntimeCheck` / `CacheHygieneCheck` |
| 道岔拒绝原因 | `SwitchBlockedReasons.LIFETIME` | 60 tick | `CacheHygieneCheck` |
| 岔口通行 | `RouteMemory.PASSAGE_TICKS` / `MAX_PASSAGES` | 200 tick / 64 | `ConsistRouteCheck` |
| 转向架交接 | `MountEntity.HANDOFF_TIMEOUT` | 200 tick | `TrainRuntimeCheck` |
| 转向架预热 | `BogiePrewarm.MAX_ENTRIES` | 8192 条 | `CacheHygieneCheck` |
| 车钩待定 | `CouplerConnections` 内联常量 | 600 tick | 无（见下） |

### 已知不合规项

岔口通行的 200 tick 是失去有效观测后的租期，不是强制通行时限。`ConsistRoutes.prepare`
仅在成员仍就绪、未脱轨、每轴具有同一铁路图上的有效轨段且采样点距实际轴位置不超过 0.8 格时，
续约尚未完成的通行；整列通过立即停止保留。缺轴、无效图、偏离导向或失去观测不能续约，
已经过期的记录不能复活。物理子步前的检查先续约，再做过期与预约刷新。

- `CouplerConnections` 的 600 tick 是**内联字面量**，没有命名常量，因此未纳入 `CacheHygieneCheck`。原因是它同时决定所有权语义（`protectsSource`），改名需要一并复核所有权规则，留待专门一轮处理。
- `CargoStorage` 的快照缓存同样使用内联数值。

## 时刻表后续站与条件观察（2026-11-02）

`PhysicalScheduleRuntime` 的 `forecastSteps` 和 `futureStops` 是每列查询代理的派生数据，不参与任务推进，不写入存档或新增网络字段。程序、项目或标题变化时重建后续筛选，程序移除时清空；每次原版显示查询重新匹配当前车站，因此不会保留已改名或删除的站点。`ScheduleForecast.MAX_ENTRIES=128` 限制向后查看的项目数，`MAX_STOPS=32` 同时限制后续目的项目和最终展开的记录数，已登记到 `CacheHygieneCheck`。非循环到结尾停止，循环最多余下一圈，同名站去重。原版执行器仍为空操作。

`ScheduleController.nextConditionCheck` 只节流乘客与容器观察；所有或分支使用同一个采样决定。重置条件时清零，时钟回退时重新采样。每个条件上下文的 `PhysicalWaitState` 只缓存上次观察到的未加载或缺少连接器原因，供两个采样之间显示；不能使条件完成，重新采样即清除并重算。装卸空闲观察允许正常的 5 tick 采样间隔，缺失采样或时钟回退会重新开始连续观察；现有逐 tick 调用仍保持原来的连续性要求。

## 同步物理体迁移（2026-11-02）

`PhysicalTransfer.active` 是单次服务端同步调用的上下文，包含两端世界及至多 16 个成员 UUID。主线程且物理步外执行，不跨 tick 排队，`finally` 必须清空；它只阻止重入并让身份删除观察器识别迁移，不是持久化列车状态。迁移实例的成员、块数、体积、目的区块和粘合实体均有上限。失败不能因目标删除异常而再生成一份源库存。

`TransferBackup` 在修改前保存有限数量的不可变备份，最多 32 份，每份估计内存上限 16 MiB；满额拒绝，不自动删恢复资料。状态文件仅用于诊断，没有启动自动重放机制。源和目标的正常 SavedData 保存不构成跨文件事务，恢复能力及待验收场景见 PHYSICAL_TRANSFER.md。

## 约定

### 驾驶台只读显示快照（2026-11-02）

`ConsoleReadout` 是每个驾驶台单独持有的客户端显示快照，包含时刻表状态、目的地、行程项、剩余距离和实际速度。它不参与驾驶、选路或状态迁移，仅随 `VisualReadout` payload 下发，不写入磁盘存档。未知距离和速度以 -1 表示；读档时清空，避免把旧显示当作当前读数。`DriverControls.publish` 每 20 tick 比较一次，变化时通过现有方块实体数据包发送，不修改方块状态或触发区块重建；客户端只读取快照，不解析服务端物理车体。覆盖检查为 `GoggleInformationCheck`。

变化比较同时包含 `ConsoleStatus`。每次比较前重新解析绑定与等待原因；即使速度、距离和行程均未变化，道岔等待原因的出现或消失仍会同步。完全不变时继续跳过重复发送。

1. 任何 `static final Map/Set` 缓存都必须有命名常量表达其过期或上限，并登记到 `CacheHygieneCheck.CACHES`
2. 任何"多个布尔共同表达一个状态"的结构，要么收敛成枚举（派生而非存储），要么在此登记合法组合
3. 发现"只能经正常路径清除"的状态，必须给出超时或时钟替代方案，不得依赖调用方配合
