# 0.2.26 Alpha — Changes since 0.2.25

**This is an unstable Alpha release. Bugs, crashes and unexpected physics behavior are still possible.**

## Create train operation compatibility

- Train name, icon and map color edits through Create's station interface now persist to Physical Train Controls, with server-side permission and input validation.
- Supported Create train APIs can now forward navigation, schedule data, pause/resume, schedule entry selection, throttle, target speed, junction steering and horn requests to the physical train backend.
- Emergency stop, derail and rerail requests affect the whole physical consist. Rerailing requires every carriage to be stopped and its axles to be close to compatible tracks.
- External speed requests respect physical power, acceleration and braking limits. Continuous manual inputs expire after 20 ticks without renewal and apply braking.
- Schedule data supplied by another mod can run without creating or transferring a schedule item. Repeated navigation requests to the same active destination no longer reset the current plan.

## Stability and recovery

- Runtime faults are isolated where possible to the affected consist, clear traction commands and apply braking. Unaffected trains continue updating when the fault can be attributed to one consist.
- Recovery uses increasing retry delays and requires a stable readiness window, reducing repeated rebuild attempts and log spam while a problem persists.
- A missing or unloaded bogey now holds the whole consist instead of allowing the remaining vehicles to keep driving. Loading delays retain the automatic-running intent; fault-paused schedules require the player to resume them after recovery.
- Recovery preserves coupler pairings, cargo and schedule progress. Existing signal occupancy and unfinished junction passage reservations are retained during fault holding where available.
- Physical Train Controls now show fault/recovery status.

## Scope and testing

- Native carriage inventory views remain read-only. Item and fluid transfer still use physical docking facilities.
- Station assembly/disassembly, schedule item handoff and physical train travel across dimensions remain unsupported.
- Compatibility applies to the supported API operations. Addons that require real Create carriage entities or custom schedule instructions/conditions may need separate integration.
- Development-server regressions passed for native operation forwarding, fault isolation, missing-bogey recovery and save/restart preservation. The latest operation-forwarding run passed all 9 required GameTests.
- Client station editing visuals, multiplayer contention and long-running performance have not been fully accepted. These tests do not establish compatibility with every third-party addon.

Minecraft 1.21.1 · NeoForge · Create 6.0.10 · Sable 2.0.x. Client and server must both update to this build.

---

# 0.2.26 Alpha — 相对 0.2.25 的更新

**这是不稳定的 Alpha 测试版本，仍可能出现错误、崩溃和异常物理行为。**

## Create 列车操作兼容

- 通过 Create 车站界面编辑列车名称、图标和地图颜色，现在会保存到物理列车驾驶台，并进行服务端权限与输入校验。
- 支持的 Create 列车接口现在可以将导航、时刻表数据、暂停/继续、行程项选择、油门、目标速度、岔路预选和汽笛请求转发到物理列车。
- 急停、脱轨和重新上轨请求作用于整列物理车。重新上轨要求全列静止，且各轴均靠近兼容轨道。
- 外部速度请求遵守物理动力、加速和制动限制。连续手动输入在 20 tick 未续期后失效并制动。
- 其他模组提供的时刻表数据可以执行，无需生成或交接时刻表物品。重复请求前往同一有效目标站时，不再重置当前行驶计划。

## 稳定性与恢复

- 尽可能将运行异常隔离到受影响的编组，清除牵引指令并施加制动；能够确定异常归属时，其他列车继续更新。
- 恢复采用逐步增加的重试间隔，并要求连续一段时间就绪，减少问题持续期间的反复重建和日志刷屏。
- 转向架缺失或卸载时，现在会保持整列停车，避免其余车辆继续行驶。加载等待保留自动运行意图；因异常暂停的时刻表需要玩家在恢复后继续。
- 恢复过程保留车钩配对、货物和时刻表进度；故障保持期间，尽可能保留已有信号占用和未完成的岔口通行预约。
- 物理列车驾驶台增加故障与恢复状态提示。

## 范围与测试

- 原版车厢库存视图仍为只读，物品和流体输送继续通过实际对接设施进行。
- 继续不支持车站组装/拆解、时刻表物品交接和物理列车跨维度行驶。
- 兼容范围限于已支持的接口操作；依赖真实 Create 车厢实体或自定义时刻表指令、等待条件的附属模组，可能仍需专门适配。
- 开发服务端已通过操作转发、故障隔离、转向架缺失恢复和保存重启保持的回归测试；最近一次操作转发回归的 9 项必需 GameTest 全部通过。
- 客户端车站编辑画面、多人争用和长时间性能尚未完成验收；上述测试不代表全部第三方附属模组均兼容。

Minecraft 1.21.1 · NeoForge · Create 6.0.10 · Sable 2.0.x。客户端和服务端均需更新到本构建。
