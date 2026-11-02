# 客户端短时卡顿排查（2026-11-02）

本轮只排查进门户和拆转向架时的停顿，不处理物理体穿门失败。原始日志、JFR 和分析脚本保存在本地忽略目录 `build/perf-capture`，不提交存档或二进制采样。

## 当前证据

客户端 RTX 2080 Ti，视距 12，Java 21 / ZGC / 最大堆 12 GiB。约 77 秒 JFR 与本地日志按 UTC+8 对齐；这不是控制变量的动作重放。

- 11:37:39.554 的 1415 ms 帧：帧内 9 个渲染线程样本均在 `SectionRenderDispatcher.uploadAllPendingUploads → VertexBuffer.bind → GL30C.glBindVertexArray`。
- 11:37:57.336 日志记录玩家进入下界；11:37:59.198 的 672 ms 帧也采到上述批量区块上传路径。不能把它直接说成物理列车迁移保存耗时；本轮日志没有 PhysicalTransfer completed。
- 创建 VAO/VBO 的样本来自 `LevelRenderer.setLevel → allChanged → ViewArea.createSections → RenderSection → VertexBuffer`，属于切世界/视区资源创建，未见这些创建样本由本模组转向架模型代码调用。
- 转向架数量 11:37:51 从 3 降至 2。附近 11:37:50.623 的帧为 105 ms，同期 BogieRender 最高 0.1221 ms，帧内样本在普通区块绘制的 GL 顶点数组绑定。时间邻近不能证明删除回调本身花了 105 ms，需要动作计时。
- 当前 Sable 单方块渲染数据 `close()` 无操作，分区块渲染数据才释放 RenderSection 缓冲。不能未经测量就把“删除单方块转向架”归为大量 VBO 释放；承载车体更新也是待查路径。
- 11:37:27.997—11:37:32.780 渲染线程样本包含 Sable 物理属性包处理、对方块状态应用物理配置及整数解析，属于独立的同步开销。采样跨度不等于精确方法耗时。
- JFR 的 30 次 GCPhasePause 总计约 0.536 ms，最大约 0.132 ms，无法解释上述长帧。日志 GC MXBean 时间包含 ZGC 并发工作，不能当成暂停时长。
- 1.415 秒帧附近整机 CPU 约 34.6%，672 ms 帧附近约 42.0%；其中 JVM 系统态占比明显升高。未见全机 CPU 饱和，但仍不能凭 Java 栈区分驱动、系统内存管理等下层开销，也不能排除并发 GC 的资源争用。

结论：已定位部分长帧的阻塞调用路径为客户端区块上传/OpenGL 绑定，门户还涉及世界视区重建。尚未区分批量上传总量、单次驱动调用阻塞、GPU/内存压力等下层机制；拆除动作也未有精确边界标记。不据此调整 GC、修改传送逻辑或认定某个外部模组有错。

## 下一轮诊断

用户明确要求打开客户端后，采用 `gradlew.bat runClient --offline -PlagProbe`，不自行启动游戏。

`guidedrailways.lagProbe=true` 才启用诊断；普通启动跳过全部 LagProbe 客户端 mixin。只计时，不改变队列、资源生命周期、方块或传送行为，签名不支持则跳过该探针。

探针阶段分别为 WORLD_REBUILD、CHUNK_UPLOAD_BATCH、VBO_BIND/UPLOAD/CLOSE、SABLE_RENDER_RELEASE、PHYSICS_PROPERTY_SYNC、BOGIE_REMOVE、PORTAL_ATTEMPT 和 TRANSFER。物理属性包计时包围实际任务执行，不能只计入队时间；该任务可能在 RenderFrameEvent.Pre 之前执行，被现有帧计数漏掉。5 ms 以上操作可写入自定义 JFR 事件 `guidedrailways.LagSpan`，`elapsedNanos` 为实测耗时，事件时间为结束时刻；40 ms 以上打印 `[LagProbe]`，每阶段最多每秒一条。嵌套阶段不能相加当作总耗时。

JFR 用当前 JDK 的 jcmd 对实际客户端 PID 开始 profile 录制；分别记录静止、只进入门户、只创建/拆除转向架，动作之间留时间，不同时进行两种操作。检查服务端 BOGIE_REMOVE/PORTAL_ATTEMPT 与客户端释放/上传是否同窗，才决定修复哪个环节。

离线验证包含默认关闭、实际 Minecraft/Sable 方法签名、变更签名拒绝、包装方法调用/参数/异常保持，以及真实 JFR 记录。探针 Mixin 变换已在后述诊断会话验证；功能验收与因果对照仍需分别完成。

## 接触门户的实测与下一组对照

12:17 启动的诊断会话已成功应用全部探针，退出时的 JFR 为本地 `probe-session-20261102-1217.jfr`。记录到 PHYSICS_PROPERTY_SYNC 2070.85 ms、首次 WORLD_REBUILD 674.37 ms，属于进入世界阶段；后续碰门时需另行判断，不能套用首次加载结论。

门户尝试只有一条达到 5 ms 记录阈值，为 5.55 ms，其余低于阈值；没有达到阈值的 TRANSFER。聊天记录显示重试被门洞尺寸校验拒绝，不是已完成迁移。与此同时，VBO_BIND 129 条慢记录最长 281.62 ms，CHUNK_UPLOAD_BATCH 最长 338.77 ms。GC 暂停最长约 0.385 ms，ZAllocationStall 为零。

12:21:09 的 216 ms 帧对应 VBO_BIND 207.38 ms。12:21:10 的 572 ms 帧则采到 Veil FramebufferStack 的 glGetIntegerv；另有帧采到 Xaero HUD 的 glGetError。长帧出现在多个原生 OpenGL 入口，不能据此认定是其中某一模组单独有错。现有证据支持客户端 OpenGL 调用阻塞，尚不能确定是门户接触产生了资源更新，还是它与渲染停顿同时发生；较短的服务端尝试耗时也不能排除异步客户端副作用。

下一轮仅在用户要求打开客户端时运行 `gradlew.bat runClient --offline -PlagProbe -PskipPhysicalPortalChecks`：跳过本模组 PhysicalPortals.tick，保留原版门户、玩家原版跨维度行为、物理碰撞和全部渲染，不改存档配置。开关同时要求诊断模式开启；单独设置跳过属性不起作用。对照只用于判断是否依赖本模组自动门户检测，不用于宣称穿门功能已修好。若仍卡顿，再将排查范围转向渲染/碰撞和 GPU/系统指标；若明显消失，再检查门户失败处理引发的客户端更新。

对照需在首次加载结束后使用同一场景和动作。B 组改善时还要恢复正常检查做 A 组复测，不能把重复启动的缓存预热当作开关的因果证据。
