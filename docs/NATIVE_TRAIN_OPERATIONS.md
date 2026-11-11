# 原版列车操作转发

日期：2026-11-11。适用 Create 6.0.10、Minecraft 1.21.1 / NeoForge。

`PhysicalTrainProxy` 现在同时提供查询和操作入口。服务器上的原版操作通过 `NativeTrainControl`
请求现有主驾驶台、`ScheduleController`、物理选路和转向架，不启动原版车厢实体或另一套执行器。
客户端的代理、反序列化和显示查询不发送物理命令。

## 支持的入口

- `TrainEditPacket.Serverbound`：名称、图标、地图颜色写入驾驶台并保存；车站界面允许编辑。
  检查玩家建造/交互权限及靠近驾驶台或真实停靠车站，回包使用规范化后的名称、颜色。
  服务器模组直接修改 `Train.name/icon/mapColorIndex` 也会接收；这些服务器内调用视为可信代码，不能代替网络包权限校验。
- `Navigation.findPathTo`（单站或站点列表）、`startNavigation`、`cancelNavigation`：使用物理规划器查路、行驶和制动。
  只接受当前铁路图上的同维度有效车站，调用方的逻辑路径不会强行移动物理车体。
  相同目标的重复启动复用有效计划；查询本身不启动列车。
- `Navigation.destination` 的服务器字段修改：目标站写入转为物理导航请求，清空转为取消。
- `ScheduleRuntime.setSchedule`：校验并复制任务数据，交给物理控制器执行。已有时刻表物品保留在驾驶台；
  无物品时保存为 `NativeProgram` 数据，不生成、消耗或返还任何时刻表物品。
- `ScheduleRuntime.paused/currentEntry/completed/currentTitle`：转发暂停/继续、行程项选择、完成及行程标题。
  直接替换 `schedule`、在其项目内修改数据也会在发布前接收，重新校验指令和条件；不直接接管外部对象。
  相同字段值不会被反复解释成新操作；程序原位修改保留当前项目、暂停意图、油门和标题。
- `ScheduleRuntime.startCurrentInstruction` 和 `transitInterrupted`：请求物理计划或取消；到站回调仅确认已经由真实停稳检测建立的关联。
- `Train.targetSpeed/speed` 和 `approachTargetSpeed`：转换为有动力预算、加速度和制动约束的目标速度，
  不写物理速度。原版单位为格/tick，转为物理格/秒时乘 20，再按代理车头与驾驶台方向换算正负号。
- `Train.throttle/manualSteer/manualTick`：油门及岔路预选写入现有控制器。逐刻手动输入需续期，
  20 tick 无续期后制动；明确设置的目标速度由取消、暂停、人工接管或拓扑变化结束。
- `Train.crash/detachFromTracks`：整列急停、释放真实轨道导向约束，保留车体、车钩和库存。
  `reattachToTracks` 先检查全列静止且两轴均仍靠近兼容轨道，再重新挂轨；不远程传送或凭空指定轨道。
- `arriveAt/setCurrentStation/leaveStation`：通过实际车站关联处理；回调或直接写入 `currentStation` 不能伪造抵达。
- `honk/honkTicks/determineHonk`：在实际物理车体位置播放原版列车汽笛并限流。
  `collectInitiallyOccupiedSignalBlocks` 重新发布自身的物理信号账本，不运行原版占用扫描。
- 主驾驶台对应双向驾驶能力查询；原版最高速度、加速度查询使用物理配置并转换单位。

## 时序与保护

公开可变字段无法拦截赋值指令，因此在每个世界刻的控制阶段接收变化，并在覆盖查询快照前再次接收。
程序内容的结构比较只在发布阶段进行，避免每物理子步序列化；无新请求时不重复解析编组。
内部投影后保存基线，防止自己的同步被当作外部命令。

每次操作重新核对当前代理、主驾驶台、车体/转向架集合、车钩完整性和服务器线程。
失效代理、未加载成员和发生变化的编组不缓存待执行命令；代理撤销时释放服务器引用。
人工正在握持拉杆、急停、故障或脱轨会拒绝新的正牵引/导航请求。暂停和取消可以安全切断外部驾驶。
外部设置直接执行中的 `speed` 也只是目标请求，不得绕过物理求解。

## 保留的边界

- 车站组装、拆解、物理列车跨维度行驶和时刻表物品交接继续不支持。
  `returnSchedule` 返回空物品；丢弃数据程序会清除数据，停止已有物品程序则保留真实物品供驾驶台取回。
- 按本轮用户选择，原版车厢库存视图继续只读，物品/流体输送仍经过实际对接设施。
- 原版 `tick`、原版燃料消耗、条件计时、冷却和序列化不是第二个命令执行器。
  这些内部调用不能重复推进物理任务；公开的距离、条件上下文、车厢轨道点等派生字段也不能冒充物理进度。
- 新增的第三方指令/等待条件仍需适配；依赖真实 `CarriageContraptionEntity`、原版组装结构或修改底层车厢点的模组不因本适配自动兼容。
  本接口不承诺任意第三方 Mixin 或客户端界面均兼容。

## 回归验证

`NativeControlServerTests.nativeWritesDrivePhysicalConsist` 使用正常放置、真实连挂的两节物理车，
通过 Create 注册表取得 `Train` 后调用上述入口，没有直接设置物理速度或车辆位置。
检查实际原版编辑包的 Mixin、权限、名称/颜色规范化、数据程序的真实 NBT 往返、
暂停与恢复、项目选择、程序原位修改、单位换算、双车运动、岔路预选、逐刻输入失联保护、
导航到站、重复导航不重置计划、整列急停/脱轨/挂轨，以及未开放的物品交接和库存保持。

复现：

```powershell
.\gradlew.bat build runSignalValidation -PsignalServerTests --offline
```

服务端测试类为独立开发 source set，不进入发行 JAR。客户端车站编辑控件的画面与多人并发操作仍待实机验收。

2026-11-11 验证结果：上述命令完整构建成功，9 项必需 GameTest 全部通过，
日志包含 `NATIVE_CONTROL_PASS`。测试服务器正常保存并退出；检查发行 JAR，未包含服务端测试类。
