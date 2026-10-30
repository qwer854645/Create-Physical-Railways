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
