# 功能计划（0.2.25 之后）

本文件只记录**已确认要做**的范围与验收方式。每一项都必须能用离线断言或探针日志验收，否则不做。

不含：`CouplerHandoff` 过期（需玩法决策）、渲染长帧（在其他模组阶段）、新的性能优化（热路径已收敛）。

## 阶段一：把“车为什么不动 / 停不准”告诉玩家

三条都依赖同一套管线，一次搭好后两条只是加字段，所以按下面的顺序做。

### 1.1 状态管线（基础）

- 新增 `ConsoleStatus`（纯枚举 + 纯函数），把互斥原因收敛成一组：`READY`、`NO_BODY`、`NO_ROUTE`、`SWITCH_DISABLED`、`SWITCH_HELD`、`NO_STATION`、`STATION_GONE`、`WAIT_PASSENGERS`、`WAIT_CARGO`、`WAIT_SIGNAL`、`HOLD_BRAKE`、`ARRIVED`
- 判定集中在 `ConsoleStatus.of(...)`，输入全部是已有数据（`SwitchBlocked` 原因、`ScheduleController` 的暂停原因、`conditionComplete` 的等待项、`DriverControls.body` 是否为空）
- 输出经 `DriverConsoleEntity.getUpdateTag` 下发（payload，不用方块状态，避免区块重建）

**验收**：`checkConsoleStatus`（纯函数断言，覆盖每个原因与优先级）；日志 `[ConsoleStatus] reason=…` 每秒一次。

**已完成**：`ConsoleStatus`（`1ffbb3c`）+ payload 下发与每秒日志（`b431fb2`），当前能报 `NO_BODY`、`NO_ROUTE`、`WAIT_TIMER`/`ARRIVED`、`HOLD_BRAKE`、`READY`。

**接线事实（下一轮直接照这个做，别重新找）**：

- 道岔拒绝发生在 `SteamRailways.diagnose(level, path, node, from, to, train, reason)`，`reason` 只有 `switch_disabled` 与 `held_by_another_consist`，已经每 20 tick 限流
- 那里的 `train` 是**预约身份**，由 `ConsistRoutes:34` 的 `memory.reservationIdentity(carrier)` 得到
- 控制台可以用 `DriverControls.body(this)` 拿到 carrier UUID，再算出同一身份
- 所以接线方式：`diagnose` 把 `reason` 写入一个按 `(level, 身份)` 的小表（约 60 tick 过期），`DriverConsoleEntity.resolveStatus()` 用算出的身份查表，映射到 `SWITCH_DISABLED` / `SWITCH_HELD`
- `STATION_GONE` 的精确判据：时刻表当前目标站 id 已不在铁路网络的站点集合里（`AutomaticNavigation` 里已有的遍历方式）

### 1.2 屏幕显示原因

- 文本用 `Font` 在 BER 里绘制一次（不自造像素字库），只在状态变化时更新缓存字符串
- 位置：屏幕下半部两行；第一行原因，第二行补充值（等待人数、剩余距离等）

**验收**：`ConsoleAssetCheck` 扩展——屏幕区域仍与贴图铜框不重叠；制造一次 `SWITCH_DISABLED`，屏幕出现对应文案。

### 1.3 站台消失的处理与提示

- `StationPresence` 已经能识别站点被拆（`live()`）；此处补玩家侧闭环：时刻表暂停在 `STATION_GONE`，目标站台清空
- 附近玩家收到一条聊天提示（“站台已被拆除，列车时刻表已暂停”）

**验收**：`TrainRuntimeCheck` 加契约——站点消失路径必须同时调用暂停与提示；游戏内拆站后状态为 `STATION_GONE` 且列车可重新派单。

## 阶段二：进站对准

### 2.1 停车点偏差显示

- 用 `ApproachLimit` 的 room 与计划停车点算带符号偏差，作为 `ConsoleStatus` 的补充值显示
- 偏差 > 1 格且在站内时标红（复用现有灯色逻辑）

**验收**：纯函数 `StopOffset.of(distance, stopAt)` 单测；游戏内手动进站能看到数值变化。

**进展**：`StopOffset` + `StopOffsetCheck`（15 项，`23a6259`）已完成符号约定、容差带与"故障阈值"；尚未接入 payload 与屏幕，等 2.2 一起接。

### 2.2 自动对准（本次唯一涉及物理的功能）

- 手动模式下按住对准键：以 0.3 m/s 上限蠕行，目标为停车点；偏差进入 ±0.3 格即停
- 复用 `RailwayStopModel` 与 `ApproachLimit`，**不新增停止判定**
- 硬性保护：超过 ±3 格或速度 > 1 m/s 立即退出对准；急停键任何时候优先

**验收**：`AutoAlignCheck`（纯函数：目标速度、退出条件、边界）；游戏内从 ±2 格内应能停进 ±0.3 格。

## 阶段三：岔道占用可视化与优先级（不含自动扳道）

### 3.1 占用可视化

- `SwitchReservations` 已能给出“谁占着这个岔”；把它映射为 `ConsoleStatus.SWITCH_HELD` + 占用者朝向提示
- 屏幕琥珀闪烁与现有灯语义一致（不新增颜色）

**验收**：两车争抢时，后车屏幕显示 `SWITCH_HELD` 且能区分“被占用”与“未启用”。

### 3.2 时刻表优先级

- 时刻表条目加 `priority`（0..5，默认 2），`SwitchQueue` 在“最早等待者优先”的基础上按优先级排序，同优先级仍按等待时间
- **不做自动扳道**：`SWITCH_DISABLED` 仍只提示，不改道岔状态

**验收**：`SwitchReservationsCheck` 扩展——高优先级可越过低优先级的等待者，但不得抢走活占用（已有规则）+ 同级保持 FIFO。

## 顺序与理由

1. **阶段一 1.1 → 1.3 → 1.2**：管线先行；站台闭环最小且能立刻验证今天的修复；屏幕文本最后（涉及渲染，成本最高）
2. **阶段二 2.1 → 2.2**：先有显示才有对准的可观测性
3. **阶段三 3.1 → 3.2**：可视化复用阶段一的管线，优先级改动只碰纯类

每个子项一个提交，提交信息写清验收方式；每次提交前全量 `./gradlew build`（含全部离线检查）。
