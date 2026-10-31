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
同名同对象已经注册，也会重新设置调试标签。`VeilFramebufferRegistrationMixin` 观察原生
`Map.put` 的返回值：为同一 Java 对象时只跳过后续重复调试命名，不再取消整个注册方法。
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

## 同一次状态快照中的重复查询

Veil 4.3.2 的 `FramebufferStack.push` 按 36006、36010、36006 查询绑定；
36006 同时表示 `GL_FRAMEBUFFER_BINDING` / `GL_DRAW_FRAMEBUFFER_BINDING`，中间没有切换绑定。
`VeilFramebufferSnapshotMixin` 通过 MixinExtras `@Share` 在本次 `push` 的局部变量中保存
第一次 36006 的结果，第二次复用；36010 的读绑定与视口依旧独立读取。
不跨帧、不跨调用保存结果，默认帧缓冲 0 也可复用。

运行时插件先用 `FramebufferSnapshotSupport` 识别这段查询序列；目标版本或其他转换引入
未知调用/写状态时跳过本项优化。该检查针对已知序列，不能保证所有第三方渲染组合兼容。
另可在启动时加 `-Dguidedrailways.disableFramebufferSnapshotReuse=true` 单独关闭本项。

`checkVeilFramebufferSnapshot` 与 Disabled 变体检查三次变两次查询、读/绘制绑定不同、
帧缓冲 0、调用隔离，并读取嵌入 Veil 字节码验证序列；模拟新增绑定操作后守卫必须拒绝。
客户端仍需验证实际注入、资源重载、窗口缩放、手部、后处理和物理车体显示。

## 兼容性加固

三项渲染优化在 Mixin 插件中统一检查目标结构；缺少字段、调用目标已替换、
读取失败时跳过对应优化。采样器路径遇到已识别的第三方调用/覆盖标记也保守跳过。
快照路径还拒绝两次查询之间新增的分支、未知调用和写状态。
这些检查只反映检查时可见的目标，无法保证随后应用的其他 Mixin 一定兼容。

启动参数 `-Dguidedrailways.disableRenderOptimizations=true` 可在注入前关闭三项优化，
各自独立的关闭参数仍有效。与只在运行时回退不同，这也避免加载对应优化的 Shadow 字段。
日志 `[RenderCompat]` 记录目标符合条件或跳过原因；eligible 仅说明允许尝试注入，
不代表实际注入、后续其他模组行为或画面已经验收。

帧缓冲注册路径不再从 HEAD 取消整个方法：注册表写入、原生清理和其他方法级钩子继续执行，
只包装 `Map.put` 和重复 `objectLabel` 调用；观察钩子未生效时，命名调用默认保留。
仍需检查其他模组是否依赖调试命名钩子本身的副作用，必要时独立关闭此项。

新增检查覆盖第三方着色器调用、缺失字段、全局/单项关闭、改动后的标签目标，
以及重复注册时原始注册表写入与包装器仍执行。未进行客户端组合测试的渲染模组不宣称兼容。
