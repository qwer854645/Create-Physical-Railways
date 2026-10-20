# 依赖版本与兼容范围（0.2.8）

核对日期：2026-10-20。游戏环境保持 Minecraft 1.21.1 / NeoForge，不跨游戏版本升级。全程未启动客户端或服务器

## 当前开发组合

- NeoForge：21.1.233 → **21.1.264**，运行最低要求仍为 21.1.233
- Sable：2.0.3 → **2.0.6**，[发布页](https://www.curseforge.com/minecraft/mc-mods/sable/files/9043865)
- Ponder：1.0.82 → **1.0.87**，[官方 Maven](https://maven.createmod.net/net/createmod/ponder/ponder-neoforge/1.0.87%2Bmc1.21.1/)
- Create：**6.0.10**（开发坐标 6.0.10-280），仍是[最新正式发布版](https://www.curseforge.com/minecraft/mc-mods/create/files/all)。不采用 Maven 的 6.0.11 开发构建
- Flywheel：**1.0.6**；Registrate：**MC1.21-1.3.0+67**，沿用 Create 的正式开发依赖；不采用带流水线编号的开发构建
- Sable Companion：**1.6.0**，与 Sable 2.0.6 捆绑版本一致；Veil、Rapier 等内部依赖沿用上游捆绑版本，不拆包替换
- Steam 'n' Rails NeoForge：**0.3.0-beta.2**，按此前要求继续使用[最新 beta](https://www.curseforge.com/minecraft/mc-mods/steam-n-rails-neoforge/files/all)
- Blocks & Bogies：**1.0.8**、Electro Energetics：**1.1.4**，仍为各自最新发布版
- Create Aeronautics／Simulated：**1.3.2**、VS / Sable Hose Connectors：**0.2.2**、Automated Logistics：**0.7.0**，仍为各自最新发布版

完整发布包地址和 SHA-256／SHA-512 在 `verification/dependency-releases.json`，Ponder 的官方镜像校验值在 `verification/dependency-maven.json`。构建依赖固定到上述版本，用户安装范围与构建版本分别管理

## 已检查的版本

以下是实际下载并检查过的发布版本，不表示任意组合均已完成游戏内测试

- Sable：**2.0.0～2.0.6**，共 7 个发布版；检查公开调用和 Rapier 私有 Mixin 接口
- Blocks & Bogies：**1.0.6、1.0.7、1.0.8**；三版原生转向架模型资源一致
- Electro Energetics：**1.0.0、1.0.1、1.1.0～1.1.4**，共 7 个发布版；兼容旧电阻配置和新功耗配置
- Railway：**0.2.1、0.3.0-beta、0.3.0-beta.2**；接口和转向架几何一致，部分纹理修正由安装的版本自行提供
- Simulated：**1.3.0、1.3.1、1.3.2**，通过对应 Aeronautics 官方捆绑包检查
- Hose Connectors：**0.1.0～0.1.9、0.2.0～0.2.2**；旧版没有自己的 `onLoad`，改为构造结束后绑定本地库存。新版加载／调整缓冲区后再次绑定，所有版本继续使用库存实际变化判断运输活动
- Automated Logistics：**0.7.0** 提供完整的快照、对接运输活动及地址检查接口。0.4.0～0.6.2 只保留旧快照扩展，对接活动钩子会因缺少接口而停用；不把这些旧版计为完整兼容
- Create：**6.0.10**。本项目直接引用的接口检查可通过 6.0.7～6.0.9，但 Sable 的上游依赖限制要求 6.0.10，因此不降低实际安装要求

较早 BnB 存在菜单回调和模型变更，Railway 0.2.0 及更早存在转向架模型差异，Simulated 1.2.x 缺少当前对接 API；没有仅靠放宽版本号将这些版本标为兼容。新版本位于声明区间内也不等于已验收，上游各模组的依赖范围仍需取交集

## 新旧 CEE 配置

新版从 CEE 配置读取每副转向架的加速／巡航功耗。旧版读取对应电阻，以实际端电压计算 `P = V²/R`，再参与现有的编组负载计算；电压未知的初始电路构建沿用 3000 V 估值

配置字段只在类初始化时解析，运行期间没有反复查找字段。实际牵引预算始终使用电路求解返回的电压和电流，保留 85% 效率、最低电压要求和断电后的独立制动；估算需求不会凭空产生机械功率

## 离线检查

准备当前开发依赖：

```powershell
python verification/prepare_dependencies.py
.\gradlew.bat build copyDevMods --offline
```

`copyDevMods` 只复制依赖，并移除 run/mods 中已被替代的、项目管理的旧版 jar，不会启动游戏。未管理的其他模组文件保持原样

完整的候选发布包接口检查：

```powershell
python verification/check_dependency_matrix.py --artifact build/libs/guidedrailways-0.2.8.jar --download
```

候选包保存在忽略的 build/dependency-matrix，不复制到 run/mods。检查 81 个发布包，覆盖实际字节码引用、父类成员、Mixin 目标、反射接口和模型资源。结果写入 build/dependency-matrix-report.json；`dependency-policy.json` 中列入支持的版本必须全部通过，不兼容候选可失败并报告原因

另已使用 NeoForge 21.1.233、Sable 2.0.0、BnB 1.0.6、CEE 1.0.0、Railway 0.2.1 和 Simulated 1.3.0 完成最低组合源码编译，然后恢复最新开发组合完成完整构建。旧 API 可以通过 `*_api_jar` Gradle 参数单独指定，不必修改开发依赖或运行目录

静态检查不能证明运行时 Mixin 一定应用成功，也不证明游戏内行为和存档兼容已经验收。用户明确要求前不启动客户端

## 待玩家回归的场景

1. 现有存档加载、转向架及车钩保存／重载、时刻表继续运行
2. BnB 与 Railway 菜单切换、不同尺寸款式的放置／渲染／碰撞
3. CEE 接触网下升降受电弓、正反向牵引、断电、制动及多车连挂
4. 三类连接器的物品／流体运输、等待条件和包裹地址过滤
5. 原版列车与物理列车碰撞、单轨放置和行驶
