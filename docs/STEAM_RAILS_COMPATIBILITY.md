# Steam 'n' Rails NeoForge 兼容

基线：Minecraft 1.21.1 / NeoForge 21.1.233 / Create 6.0.10-280 / Steam 'n' Rails 0.3.0-beta.2+neoforge-mc1.21.1

发布包来源：https://www.curseforge.com/minecraft/mc-mods/steam-n-rails-neoforge/files/8522154

发布包 SHA-256：`64b74ddad661deae1cfe7e8334cfa3d39bb8ef7b761f83595ba0476c4a9bb1d5`

可选模组 ID 为 `railways`。发布 jar 放在忽略的 `libs/`，只作编译接口和开发运行依赖，不复制进本模组发布包。发布包要求 NeoForge >=21.1.233，因此同步开发环境。保持 Create 6.0.10-280，实际与 Beta 的组合需客户端验收。

## 分步提交与验收

1. 固定 Beta 依赖与开发环境
2. 轨道类型识别与标准轨兼容
3. 普通/自动道岔、规划与行驶分离、编组跟随
4. 信号与轨旁止冲挡
5. 窄轨/宽轨转向架、放置/导向/模型/碰撞/存档
6. 单轨专用转向架与安装高度
7. 轨旁连挂设备适配本模组车钩
8. 集成检查、存档与缺省依赖回归、客户端待测清单

每一步单独提交，自动检查不代替游戏内验收。按用户要求不自行开服务器测试，等待用户回来测试。
