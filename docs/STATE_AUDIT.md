# 状态与缓存审计

本文件记录项目里每个**隐式状态机**的字段、合法组合，以及每个长生命周期缓存的过期策略。新增状态或缓存时必须更新这里，否则下一处同类缺陷就是从这里漏出去的。

背景：一周内修过三类同形缺陷——某状态只能经正常路径清除而没有超时（转向架交接、岔口通行记录）、多个布尔同时为真且优先级只存在于读代码的人脑子里（控制台）、手写缓存靠计矛盾次数逃逸（`repeat`）。因此凡是"多字段共同表达一个状态"或"静态表无过期"的地方，都要在此登记。

## 整列故障隔离与恢复（2026-11-11）

`TrainFaults` 按 `ServerLevel` 保存瞬态故障，使用车体 UUID 将当前同一编组关联到一个事件，
记录故障时的车体与转向架集合，不写入存档。可定位的控制、选路、牵引、物理、接触、
车钩和查询发布异常只拦截 `RuntimeException`，清空整列牵引指令并使用独立制动路径；
其他编组继续更新。无法确定归属的共享阶段异常保守地保持当前世界全部已知列车，
分别对 `world_before`、`world_after`、`physics`、`coupling` 四个固定阶段节流。

- `RecoveryWindow` 的首次异常冷却为 20 tick，连续异常依次为 40、80、160、200 tick；
  400 tick 没有新异常后重新从首次间隔开始。暂时缺少成员或物理句柄时每 20 tick 探测。
- 冷却结束后必须连续 10 个不同游戏刻就绪；物理子步不能累加稳定计时，间断或时钟回退重新等待。
  检查包括车钩运行时约束、原成员集合和物理句柄。存在共享阶段故障时不放行单列。
  正常运行时同样比较上一刻成员集合，尾轴消失不能因“其余车体和车钩仍完整”而漏过保护。
  只有主动编组操作接受新的快照基线；服务端实车回归见 `FAULT_SERVER_VALIDATION.md`。
- 异常暂停保留时刻表物品、当前项目和等待条件上下文，必须恢复后由玩家继续；普通加载等待不撤销
  自动运行意图。停车标志先于码头释放回调写入，回调异常不能留下仍运行的时刻表。
- 主动改变编组或明确重新接管时，可接受当前编组作为恢复目标；这不绕过车钩完整性检查。
  主动解挂后各列使用独立事件和稳定时钟，不能因一半恢复而放行另一半；重新连挂重新等待稳定。
  未再出现的事件别名按 `INCIDENT_TICKS=1200` 清理，世界卸载显式清空整表。
- `REPORT_TICKS=200` 限制每个事件的异常日志，记录阶段、成员与原始堆栈。车钩重建冷却按已保存链接 ID
  管理，删除链接时清除；接触约束清理冷却随运行时约束销毁。异常本身不删除配对、货物或身份数据。
- 故障保持期间，能读取的车体继续刷新实际信号占用；无法读取时保留最后占用。
  未完成的岔口通行使用 `holdForRecovery` 续期，不创建新通行，也不复活已经过期的通行。
  已有原版查询代理与车站关联在对应故障保持路径中保留最后快照。

检查：`RecoveryWindowCheck` 验证恢复时间策略；`FaultRecoveryCheck` 直接调用生产事件注册逻辑验证拆分与重连；
`ConsistRouteCheck` 验证超过租期的故障保持与恢复后尾轴选路；
`TrainRuntimeCheck` 验证异常边界、清除牵引先于回调、RPM 安全闸及持久化边界。
这些离线检查不等于真实物理引擎的故障注入验收。网络协议改为 9，客户端与服务端须使用同一构建。

## 状态机

### 1. 控制台意图 `ConsoleIntent`

| 意图 | 条件 | 是否驱动列车 |
|---|---|---|
| `IDLE` | 未绑定，或无人接管 | 否 |
| `MANUAL` | 手动握持拉杆 | 是 |
| `SCHEDULE` | 时刻表运行且被观察 | 否（由时刻表下令） |
| `OVERRIDE` | 时刻表存在但司机接管 | 是 |

- 派生字段：`bound`、`observing`、`active`、`schedule.running()`
- 断言：`ConsoleIntentCheck`（28 项，含全部 16 种标志组合都必须恰好得到一个意图）；自动对准已删除

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

## 约定

### 驾驶台只读显示快照（2026-11-02）

`ConsoleReadout` 是每个驾驶台单独持有的客户端显示快照，包含时刻表状态、目的地、行程项、剩余距离和实际速度。它不参与驾驶、选路或状态迁移，仅随 `VisualReadout` payload 下发，不写入磁盘存档。未知距离和速度以 -1 表示；读档时清空，避免把旧显示当作当前读数。`DriverControls.publish` 每 20 tick 比较一次，变化时通过现有方块实体数据包发送，不修改方块状态或触发区块重建；客户端只读取快照，不解析服务端物理车体。覆盖检查为 `GoggleInformationCheck`。

变化比较同时包含 `ConsoleStatus`。每次比较前重新解析绑定与等待原因；即使速度、距离和行程均未变化，道岔等待原因的出现或消失仍会同步。完全不变时继续跳过重复发送。

1. 任何 `static final Map/Set` 缓存都必须有命名常量表达其过期或上限，并登记到 `CacheHygieneCheck.CACHES`
2. 任何"多个布尔共同表达一个状态"的结构，要么收敛成枚举（派生而非存储），要么在此登记合法组合
3. 发现"只能经正常路径清除"的状态，必须给出超时或时钟替代方案，不得依赖调用方配合

### 驾驶台直接操作（2026-11-02）

驾驶模式入口和全局移动键/左键接管已移除。`active/operator` 现在仅表示按住拉杆期间的临时互斥操作，松开、打开界面或失焦即释放，保留 `enabled` 和当前档位；不用于持续占用玩家输入。客户端每 5 tick 续期，服务端 20 tick 未续期或时钟回退释放；失联、死亡或超距时沿用停车保护。领取时校验主控、权限、距离、视线及模型部件命中，后续档位仅接受当前操作者。`interactionAt` 不持久化。

红色按钮独立急停，不要求领取拉杆。复位记录仅包含最近按下者 UUID 和 `buttonAt`，要求仍对准按钮、按下后 20～60 tick 且已停稳；复位后清除，旧记录不能授权之后的请求，不持有实体引用。打开设置和查看显示不切换自动驾驶；明确按住拉杆会通过既有 `schedule.manual` 暂停自动任务。

### 三位岔路预选（2026-11-03）

每个驾驶台持有一个 `ManualRouteSelection`，仅保存当前意图及修订号；不可变快照在选择改变时才分配，物理步读取不额外分配。它不持有世界或实体引用，不写入磁盘，仅通过 `VisualRouteSelection` 和 `VisualRouteAutomatic` 同步显示。临时拉杆互斥释放不会清除此预选。

`ConsistRoutes.Context` 在每次准备时引用当前主控及预选快照；消费必须匹配修订号，不能把较新操作当作旧预选消费。`RouteMemory.passageVersion` 是实例内的新通行记录序号，只有实际创建记录才递增，不改变既有路径和保留策略。自动运行、失去主控/车体、卸载、停车保护及编组变化清除预选；不存在持久化待执行转向、全局玩家表或轮询扫描。


## 原版操作转发（2026-11-11）

`NativeTrainControl` 随每个代理创建，保存服务器/编组绑定和最后投影基线，不是全局注册表。
公开字段的变化在控制阶段和查询投影覆盖前接收；无变化不做编组查询，程序内容仅在发布阶段比较。
代理移除时 `unbind` 清除世界引用、成员和程序基线，失效请求不延迟执行。
手动输入租期为 20 tick，汽笛最小间隔为 20 tick；瞬态目标速度不写入驾驶台存档。

`DriverConsoleEntity` 保存 `TrainIcon`、`TrainMapColor`，名称仍使用 `TrainName`；`nativeSpeed` 为瞬态外部目标，
NaN 表示无外部目标；nativeUntil 是逐刻输入的物理控制租期，普通目标使用 Long.MAX_VALUE。
租期由物理自动控制阶段检查，不依赖查询代理是否撤销/重建。人工接管、暂停、取消、失联或主动拓扑变化结束该目标。
`ScheduleController.nativeProgram` 仅在没有物理时刻表物品时持有程序 NBT，保存到 PhysicalSchedule.NativeProgram；
`nativeDestination` 表示外部直接导航目标，保存到 NativeDestination，与原时刻表进度分别处理。
两者仍由同一个物理执行器推进，加载时走同一个成员恢复门，不通过原版 tick 推进。
外部导航完成不消耗原有程序项目；程序数据丢弃不生成物品，真实时刻表物品的所有权不交给原版返回入口。
转发范围、派生只读字段和测试场景见 `NATIVE_TRAIN_OPERATIONS.md`。
