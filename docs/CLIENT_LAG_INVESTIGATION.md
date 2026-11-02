# 客户端短时卡顿排查（2026-11-02）

保留客户端渲染和转向架拆除的测量结果。采样只能说明实际记录到的调用路径，时间邻近不能代替因果验证。

## 已观察到的开销

测试客户端为 RTX 2080 Ti、视距 12、Java 21 / ZGC / 最大堆 12 GiB。

- 11:37:39.554 的 1415 ms 帧内，9 个渲染线程样本均在 `SectionRenderDispatcher.uploadAllPendingUploads → VertexBuffer.bind → GL30C.glBindVertexArray`。
- 视区资源创建样本来自 `LevelRenderer.setLevel → allChanged → ViewArea.createSections → RenderSection → VertexBuffer`，未见这些创建样本由本模组转向架模型代码调用。
- 转向架数量在 11:37:51 从 3 降至 2。附近 105 ms 帧内采到普通区块绘制的 GL 顶点数组绑定，同期 BogieRender 最高 0.1221 ms；这不能证明删除回调本身花了 105 ms。
- Sable 单方块渲染数据的 `close()` 无操作，分区块渲染数据才释放 RenderSection 缓冲；不能未经测量就归因为大量 VBO 释放。
- 首轮 JFR 的 30 次 GCPhasePause 总计约 0.536 ms，最大约 0.132 ms；后续会话的最大暂停约 0.385 ms，ZAllocationStall 为零。GC MXBean 时间包含 ZGC 并发工作，不能当成暂停时长。
- 后续会话记录到 VBO_BIND 最长 281.62 ms、CHUNK_UPLOAD_BATCH 最长 338.77 ms。12:21:09 的 216 ms 帧对应 VBO_BIND 207.38 ms；12:21:10 的 572 ms 帧采到 Veil FramebufferStack 的 `glGetIntegerv`，另有帧采到 Xaero HUD 的 `glGetError`。
- PHYSICS_PROPERTY_SYNC 2070.85 ms 和首次 WORLD_REBUILD 674.37 ms 出现在进入世界阶段，不能套用到正常驾驶。

部分长帧已定位到客户端 OpenGL 调用阻塞，尚未区分批量上传、GPU/内存压力和驱动等下层机制，也不能据此认定某个外部模组单独有错。

## 诊断方式

用户明确要求打开客户端后，使用 `gradlew.bat runClient --offline -PlagProbe`。普通启动不应用 LagProbe 客户端 Mixin；诊断只计时，目标签名不支持则跳过对应探针。

保留的阶段为 WORLD_REBUILD、CHUNK_UPLOAD_BATCH、VBO_BIND、VBO_UPLOAD、VBO_CLOSE、SABLE_RENDER_RELEASE、PHYSICS_PROPERTY_SYNC 和 BOGIE_REMOVE。物理属性包计时包围实际任务执行。5 ms 以上操作可写入 JFR 事件 `physicalrailways.LagSpan`，`elapsedNanos` 是实测耗时，事件时间为结束时刻；40 ms 以上打印日志，每阶段最多每秒一条。嵌套阶段不能相加当作总耗时。

对实际客户端 PID 录制 JFR，分别测量首次加载后的静止、驾驶和创建/拆除转向架，动作之间留出间隔。检查服务端拆除计时与客户端释放/上传是否同窗，再决定修复位置。离线检查覆盖默认关闭、Minecraft/Sable 方法签名、包装调用与异常保持，以及真实 JFR 记录。
