# 着色器采样器位置复用试验

2026-10-30 的 JFR 与同次长帧日志对齐后，360、309、281 ms 等长帧期间均采到
`ShaderInstance.apply → Uniform.glGetUniformLocation → GL20C.nglGetUniformLocation`。
该阻塞出现在地形、转向架和连接器等不同绘制调用中。采样能定位调用栈，但不解释驱动内部
为何阻塞，也不证明所有长帧都来自这一条路径。

Minecraft 1.21.1 的 `ShaderInstance.updateLocations` 在链接阶段已建立同序的
`samplerNames`、`samplerLocations`，移除未找到的采样器名称；`apply` 却再次按名称查询。
客户端 `ShaderSamplerLocationMixin` 仅包装 `apply` 内这一处查询，读取该实例的原生位置列表。
不修改纹理绑定、采样器槽位、uniform 上传、链接过程和 `updateLocations`。

- 不增加全局或按 OpenGL program ID 索引的缓存；程序 ID 被回收复用时不会继承其他实例的数据。
- 原生列表位置变化时立即读取更新值；资源重载新建着色器时使用新实例的列表。
- program 不匹配、采样器未知、列表未完成或长度不匹配时调用原查询/其他包装器。
- 注入只在客户端生效。`require=0` 允许其他优化模组先移除这一调用时跳过本优化；
  这不代表任意第三方着色器组合已验收。
- 启动参数 `-Dguidedrailways.disableSamplerLocationReuse=true` 恢复原查询，便于同场景对照。
  此参数在进程启动时读取；不能在游戏运行中切换。

离线验证：`checkShaderSamplerReuse` 与 `checkShaderSamplerReuseDisabled` 直接调用包装方法，
用计数查询替身检查一万次重复绘制、缺失槽位、位置更新、同 program ID 的新实例及回退路径；
同时检查当前原版字节码中的调用目标和客户端注册。它们不创建 OpenGL 上下文，
不证明实际 Mixin 加载成功，也不证明长帧已改善。

游戏内待验收：相同世界与位置，分别观察连接器、地形、物理转向架；检查材质、光照与动画，
并执行资源重载。重新采集 JFR 和 FrameSpike，比较稳定运行阶段长帧次数与峰值，
检查 `apply` 的重复查询栈是否减少；必要时使用关闭参数做对照。只在用户明确请求时启动客户端。

## 2026-10-31 复测与 Veil 后续试验

新 JFR 在稳定阶段未再采到重复 `glGetUniformLocation`，但仍有 85–190 ms 长帧。
190/136 ms 帧采到 Veil `FramebufferStack.push → glGetIntegerv`，172/85 ms 帧采到
`FramebufferManager.setFramebuffer → VeilDebug.objectLabel → glObjectLabel`。
因此不能将“消除一个查询热点”等同于“整体卡顿解决”；各 OpenGL 调用也可能遇到共同的驱动等待。

已检查嵌入的 Veil 4.3.2：`VeilFirstPersonRenderer.bind` 每帧调用 `setFramebuffer`；后者即使
同名同对象已经注册，也会重新设置调试标签。`VeilFramebufferRegistrationMixin` 只在
原生 `manualFramebuffers` 已含该名称、且 `framebuffers` 的值为同一 Java 对象时跳过重复方法。
不另外保存对象/GL ID，不改变渲染绑定和帧缓冲恢复；定义缓冲转手动缓冲、新对象注册、
资源重载后重新注册均执行原方法。如果同一 Java 对象内部重新创建 GL ID，跳过重复标签
可能使新 GL 对象暂时缺少调试名称，但不影响绘制。

`glGetIntegerv` 是状态快照的一部分，尚未修改。下一轮要检查调试命名栈是否消失、
剩余状态查询长帧是否变化，以及窗口缩放、重载和手部画面是否正常。
`-Dguidedrailways.disableFramebufferRegistrationReuse=true` 在启动时关闭本轮试验，
与前一轮采样器优化的关闭参数相互独立。

`checkVeilFramebufferReuse` / `checkVeilFramebufferReuseDisabled` 检查实际注入方法的
重复注册、转换、替换和重载处理，并读取安装依赖字节码核对方法副作用清单；
离线通过仍不代表 Mixin 在客户端实际应用成功或卡顿已消除。
