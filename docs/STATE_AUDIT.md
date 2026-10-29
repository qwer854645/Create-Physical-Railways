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

已知需要明确的组合（目前没有任何守卫）：

| 组合 | 含义 | 谁来保证 |
|---|---|---|
| `status=WAITING` 且 `running=false` | 等待中却没在跑 | 无 |
| `running=false` 且 `resumePending=true` | 已停但仍有待恢复 | 无 |
| `manualOverride=true` 且 `status=SCHEDULE 系列` | 司机接管期间的显示 | 部分（`manual()`） |
| `routeRetry=true` | 请求重算路线 | 由 `AutomaticTrains` 消费并清除 |

### 3. 岔口通行 `RouteMemory`

- 每个节点一个**有序通行队列**，每根轴一个消费指针
- 通行有 `PASSAGE_TICKS = 200` 的生命周期，每节点最多 `MAX_PASSAGES = 64` 条
- 断言：`ConsistRouteCheck`（121 项）

### 4. 驾驶使能 `DriverConsoleEntity.enabled`

- 时刻表启动时关闭（`ScheduleController:474`），手动给档位时重新打开
- 与 `ConsoleIntent` 的关系：`enabled=false` 时意图不可能是 `MANUAL`

## 缓存与过期

| 缓存 | 位置 | 上界 | 断言 |
|---|---|---|---|
| 道岔预约 | `SwitchReservations.HOLD_TICKS` | 40 tick | `SwitchReservationsCheck` |
| 时刻表优先级 | `SchedulePriority.LIFETIME` | 40 tick | `CacheHygieneCheck` |
| 接近限距 | `ApproachRooms.LIFETIME` | 10 tick | `CacheHygieneCheck` |
| 道岔拒绝原因 | `SwitchBlockedReasons.LIFETIME` | 60 tick | `CacheHygieneCheck` |
| 岔口通行 | `RouteMemory.PASSAGE_TICKS` / `MAX_PASSAGES` | 200 tick / 64 | `ConsistRouteCheck` |
| 转向架交接 | `MountEntity.HANDOFF_TIMEOUT` | 200 tick | `TrainRuntimeCheck` |
| 转向架预热 | `BogiePrewarm.MAX_ENTRIES` | 8192 条 | `CacheHygieneCheck` |
| 车钩待定 | `CouplerConnections` 内联常量 | 600 tick | 无（见下） |

### 已知不合规项

- `CouplerConnections` 的 600 tick 是**内联字面量**，没有命名常量，因此未纳入 `CacheHygieneCheck`。原因是它同时决定所有权语义（`protectsSource`），改名需要一并复核所有权规则，留待专门一轮处理。
- `CargoStorage` 的快照缓存同样使用内联数值。

## 约定

1. 任何 `static final Map/Set` 缓存都必须有命名常量表达其过期或上限，并登记到 `CacheHygieneCheck.CACHES`
2. 任何"多个布尔共同表达一个状态"的结构，要么收敛成枚举（派生而非存储），要么在此登记合法组合
3. 发现"只能经正常路径清除"的状态，必须给出超时或时钟替代方案，不得依赖调用方配合
